package io.github.thelony97.orbit.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.now
import io.github.thelony97.orbit.notify.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

object Sync {
    private const val PERIODIC = "orbit-sync-periodic"
    private const val NOW = "orbit-sync-now"
    private const val AFTER_CALL = "orbit-sync-after-call"

    fun schedulePeriodic(ctx: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(3, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun requestNow(ctx: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    fun afterCall(ctx: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(4, TimeUnit.SECONDS)
            .setInputData(workDataOf(SyncWorker.KEY_MODE to SyncWorker.MODE_AFTER_CALL, SyncWorker.KEY_SINCE to now() - TimeUnit.MINUTES.toMillis(5)))
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(AFTER_CALL, ExistingWorkPolicy.REPLACE, request)
    }

    /** Several new contacts at once (Mehrfach-Import): one worker, one pass. */
    fun backfill(ctx: Context, contactIds: Collection<String>) {
        if (contactIds.isEmpty()) return
        if (contactIds.size == 1) return backfill(ctx, contactIds.first())
        // WorkManager input is capped at 10 KB: a few hundred ids would throw. One worker per block.
        contactIds.chunked(100).forEach { block ->
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(workDataOf(SyncWorker.KEY_MODE to SyncWorker.MODE_BACKFILL, SyncWorker.KEY_CONTACTS to block.joinToString(",")))
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("orbit-backfill-many-" + block.hashCode(), ExistingWorkPolicy.REPLACE, request)
        }
    }

    fun backfill(ctx: Context, contactId: String) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf(SyncWorker.KEY_MODE to SyncWorker.MODE_BACKFILL, SyncWorker.KEY_CONTACT to contactId))
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("orbit-backfill-$contactId", ExistingWorkPolicy.REPLACE, request)
    }
}

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        const val KEY_MODE = "mode"
        const val KEY_CONTACT = "contact"
        const val KEY_CONTACTS = "contacts"
        const val KEY_SINCE = "since"
        const val MODE_AFTER_CALL = "after_call"
        const val MODE_BACKFILL = "backfill"
    }

    override suspend fun doWork(): Result {
        OrbitRepository.init(applicationContext)
        OrbitRepository.ensureLoaded()
        when (inputData.getString(KEY_MODE)) {
            MODE_BACKFILL -> {
                val ids = (inputData.getString(KEY_CONTACTS)?.split(",").orEmpty() + listOfNotNull(inputData.getString(KEY_CONTACT)))
                    .filter { it.isNotBlank() }.toSet()
                SyncEngine.backfill(applicationContext, ids)
            }
            MODE_AFTER_CALL -> {
                val since = inputData.getLong(KEY_SINCE, 0L)
                val fresh = SyncEngine.syncAll(applicationContext)
                val data = OrbitRepository.snapshot()
                var prompted = false
                // An unknown Durchwahl asks „Mitarbeiter zuordnen?“ even without the note prompt.
                val newest = fresh.filter { (it.entry.at + (it.entry.durationSec ?: 0) * 1000) >= since }.maxByOrNull { it.entry.at }
                if (data.settings.noteAfterCall || newest?.extension != null) {
                    // The call that just ended is the newest one; older catch-up entries get no prompt.
                    newest
                        ?.let { call ->
                            data.contacts.firstOrNull { it.id == call.contactId }?.let { contact ->
                                // Archived (PLAN 3.11): the call lands silently in the Verlauf, no prompt.
                                // Orbit as phone app: the call screen has the note line for this contact.
                                val onScreen = io.github.thelony97.orbit.call.CallSession.noteOnScreen(applicationContext, contact.id)
                                if (!contact.archived && (!onScreen || call.extension != null)) {
                                    Notifications.afterCall(applicationContext, contact, call.entry, note = data.settings.noteAfterCall && !onScreen, extension = call.extension)
                                }
                                prompted = true
                            }
                        }
                }
                // PLAN 3.12: talked to an unknown number → offer to create a contact or an organisation.
                if (!prompted && data.settings.suggestUnknown) {
                    SyncEngine.lastCall(applicationContext, since)?.takeIf { SyncEngine.isIncoming(it) && it.duration > 0 }?.let { call ->
                        val number = call.number
                        val iso = io.github.thelony97.orbit.domain.Phones.countryIso(applicationContext)
                        val normalize: (String) -> String? = { io.github.thelony97.orbit.domain.Phones.normalize(it, iso) }
                        // Known = a contact, a hidden member or an org's number range (same lookup as the ring).
                        val known = number != null && (
                            io.github.thelony97.orbit.store.CallerIndexFile.read(applicationContext)?.lookup(number, normalize) != null ||
                                io.github.thelony97.orbit.domain.PhoneMatcher(data.contacts.filter { it.deletedAt == null }, iso).match(number) != null
                            )
                        val key = number?.let(normalize)
                        val dismissed = data.unknownNumbers.any { it.key == key && it.dismissed }
                        if (!number.isNullOrBlank() && !known && !dismissed) {
                            Notifications.unknownAfterCall(applicationContext, number, call.duration, call.cachedName)
                        }
                    }
                }
            }
            else -> SyncEngine.syncAll(applicationContext)
        }
        OrbitRepository.flush()
        return Result.success()
    }
}

/**
 * Watches the phone state. Ringing with a known number shows "Nächstes Mal ansprechen",
 * idle (call ended) triggers a sync that offers to add a note.
 */
class PhoneStateReceiver : BroadcastReceiver() {

    companion object {
        /** When the last call ended (process-wide; receivers are new objects each time). */
        @Volatile private var idleAt = 0L

        /** Whether a call ended at or after [elapsed] (SystemClock.elapsedRealtime). */
        fun endedSince(elapsed: Long): Boolean = idleAt >= elapsed

        /** One fallback notice per call at most. */
        @Volatile private var fallbackCheckedAt = 0L
    }

    /**
     * Orbit holds the phone role, yet Telecom did not hand the call to Orbit (service not bound a
     * few seconds in): the preinstalled phone app took it – say so afterwards (PLAN 3.12).
     */
    private fun checkFallback(context: Context) {
        val t = android.os.SystemClock.elapsedRealtime()
        if (t - fallbackCheckedAt < 60_000L) return
        fallbackCheckedAt = t
        val pending = goAsync()
        scope.launch {
            try {
                kotlinx.coroutines.delay(4000)
                // Handed over = Orbit got a call around this moment (the service may already be
                // gone again after a very short call, so not „is bound now“).
                val bound = io.github.thelony97.orbit.call.CallSession.lastCallAddedAt >= t - 30_000L
                if (!bound && io.github.thelony97.orbit.call.PhoneRole.held(context)) Notifications.phoneFallback(context)
            } finally {
                pending.finish()
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        when (state) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Outgoing or answered: same check while Orbit is the phone app.
                if (io.github.thelony97.orbit.call.PhoneRole.held(context)) checkFallback(context)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                idleAt = android.os.SystemClock.elapsedRealtime()
                Notifications.cancelTopics(context)
                Sync.afterCall(context)
            }
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (number.isNullOrBlank()) return
                // Orbit is the phone app: its call screen shows all this already.
                if (io.github.thelony97.orbit.call.PhoneRole.held(context)) {
                    checkFallback(context)
                    return
                }
                val ringAt = android.os.SystemClock.elapsedRealtime()
                // A very short call can end before the card is ready: never post after "idle".
                fun stillRinging() = idleAt < ringAt
                val pending = goAsync()
                scope.launch {
                    try {
                        // PLAN 3.12: who is calling, straight from the small caller index – no
                        // full load, so it shows while it still rings. Never touches the call.
                        val shown = runCatching {
                            val index = io.github.thelony97.orbit.store.CallerIndexFile.read(context)
                            val iso = io.github.thelony97.orbit.domain.Phones.countryIso(context)
                            index?.takeIf { it.callerInfo }
                                ?.lookup(number) { io.github.thelony97.orbit.domain.Phones.normalize(it, iso) }
                                ?.also { if (stillRinging()) Notifications.callerInfo(context, it, number, ::stillRinging) }
                        }.getOrNull()
                        // The caller card already lists the open topics; otherwise the old reminder.
                        if (shown == null) {
                            OrbitRepository.init(context)
                            val data = OrbitRepository.ensureLoaded()
                            val contact = SyncEngine.contactFor(data, context, number)
                            // Archived contacts never show up in notifications (PLAN 3.11).
                            if (contact != null && !contact.archived && contact.deletedAt == null && data.settings.topicsDuringCall && stillRinging()) {
                                Notifications.topics(context, contact)
                            }
                            // Unknown number that called before (PLAN 3.12: „3 Anrufe …“).
                            if (contact == null && data.settings.callerInfo) {
                                val key = io.github.thelony97.orbit.domain.Phones.normalize(number, io.github.thelony97.orbit.domain.Phones.countryIso(context))
                                data.unknownNumbers.firstOrNull { it.key == key && !it.dismissed && it.calls > 0 }
                                    ?.let { if (stillRinging()) Notifications.unknownCaller(context, it, number) }
                            }
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
