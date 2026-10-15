package io.github.thelony97.orbit.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.app.RemoteInput
import androidx.lifecycle.lifecycleScope
import io.github.thelony97.orbit.actions.ContactActions
import io.github.thelony97.orbit.backup.Backups
import io.github.thelony97.orbit.data.Mutations
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.startMillis
import io.github.thelony97.orbit.domain.today
import io.github.thelony97.orbit.domain.zone
import io.github.thelony97.orbit.sync.Sync
import io.github.thelony97.orbit.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Runs [block] off the main thread while keeping the receiver alive. */
internal fun BroadcastReceiver.runAsync(context: Context, block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            OrbitRepository.init(context)
            OrbitRepository.ensureLoaded()
            block()
            OrbitRepository.flush()
        } finally {
            pending.finish()
        }
    }
}

object DigestScheduler {


    private fun pendingIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 4711, Intent(ctx, DigestReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Schedules the next daily summary. Catches up immediately if today's was missed. */
    suspend fun schedule(ctx: Context) {
        val settings = OrbitRepository.snapshot().settings
        val nowDt = LocalDateTime.now(zone())
        val todayAt = nowDt.toLocalDate().atTime(settings.digestHour, settings.digestMinute)
        val missedToday = nowDt.isAfter(todayAt) && settings.lastDigestDay < nowDt.toLocalDate().toEpochDay()
        val next = when {
            missedToday -> nowDt.plusSeconds(30)
            nowDt.isBefore(todayAt) -> todayAt
            else -> todayAt.plusDays(1)
        }
        val millis = next.atZone(zone()).toInstant().toEpochMilli()
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(ctx)
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    suspend fun run(ctx: Context) {
        val todayDate = today()
        val before = OrbitRepository.snapshot()
        if (before.settings.lastDigestDay >= todayDate.toEpochDay()) return
        runCatching { SyncEngine.syncAll(ctx) }
        val data = OrbitRepository.snapshot()
        // Only occasions (PLAN 3.5/3.13a), each with its own reminders (am Tag / 1 / 3 / 7 / 14 Tage
        // vorher); never archived or deleted contacts. Your own („Ich & Anna“) remind like any other.
        val me = data.contacts.firstOrNull { it.isMe }?.id
        val due = io.github.thelony97.orbit.core.domain.ReminderPlan.dueOn(data.contacts, todayDate) { it.deletedAt == null && !it.archived }
            // Your own birthday is no reminder for you; shared ones with „Ich“ stay.
            .filterNot { it.upcoming.ownerId == me && it.upcoming.occasion.kind == io.github.thelony97.orbit.core.model.OccasionKind.BIRTHDAY }
        Notifications.occasions(ctx, due, data)
        // Themen mit Datum (PLAN 3.3): since the last morning run, missed days caught up.
        val lastRun = before.settings.lastDigestDay.takeIf { it > 0 }?.let { java.time.LocalDate.ofEpochDay(it) }
        Notifications.topicsDue(
            ctx,
            io.github.thelony97.orbit.core.domain.TopicPlan.due(data.contacts, todayDate, lastRun) { it.deletedAt == null && !it.archived },
            data,
        )
        // Treffen heute (PLAN 3.13a): what to have in mind before meeting someone.
        if (data.settings.meetingBrief) io.github.thelony97.orbit.core.domain.MeetingBrief.today(
            data.meetings, data.contacts, data.relations, todayDate, System.currentTimeMillis(), zone(),
        ) { it.deletedAt == null && !it.archived }.forEach { Notifications.meetingBrief(ctx, it, data) }

        val startOfToday = todayDate.startMillis()
        val meetingsToAsk = data.meetings.filter { !it.asked && !it.resolved && it.end < startOfToday }
        meetingsToAsk.forEach { m ->
            data.contacts.firstOrNull { it.id == m.contactId && it.deletedAt == null && !it.archived }?.let { c ->
                Notifications.meetingFollowUp(ctx, m, c)
            }
        }

        val askedIds = meetingsToAsk.map { it.id }.toSet()
        OrbitRepository.update { d ->
            d.copy(
                meetings = d.meetings.map { m -> if (m.id in askedIds) m.copy(asked = true) else m },
                settings = d.settings.copy(lastDigestDay = todayDate.toEpochDay()),
            )
        }
    }
}

class DigestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync(context) {
        DigestScheduler.run(context)
        DigestScheduler.schedule(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    private val handled = setOf(
        Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED,
    )

    override fun onReceive(context: Context, intent: Intent) {
        // Only the system's own broadcasts (an explicit intent from elsewhere does nothing).
        if (intent.action !in handled) return
        runAsync(context) {
            DigestScheduler.schedule(context)
            Sync.schedulePeriodic(context)
            Backups.schedule(context)
        }
    }
}

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_NOTE = "io.github.thelony97.orbit.NOTE"
        const val ACTION_MEETING = "io.github.thelony97.orbit.MEETING"
        const val ACTION_CALLBACK = "io.github.thelony97.orbit.CALLBACK"
        const val ACTION_UNARCHIVE = "io.github.thelony97.orbit.UNARCHIVE"
        const val ACTION_MEMBER = "io.github.thelony97.orbit.MEMBER"
        const val EXTRA_NAME = "name"
        const val EXTRA_NUMBER = "number"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_CONTACT = "contact"
        const val EXTRA_ENTRY = "entry"
        const val EXTRA_MEETING = "meeting"
        const val EXTRA_HAPPENED = "happened"
        const val EXTRA_NOTIF = "notif"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(EXTRA_NOTIF, -1)
        when (intent.action) {
            ACTION_NOTE -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifications.KEY_NOTE)?.toString()?.trim()
                val contactId = intent.getStringExtra(EXTRA_CONTACT) ?: return
                val entryId = intent.getStringExtra(EXTRA_ENTRY) ?: return
                runAsync(context) {
                    if (!text.isNullOrEmpty()) {
                        OrbitRepository.update { d ->
                            d.copy(contacts = d.contacts.map { c ->
                                if (c.id != contactId) c
                                else c.copy(entries = c.entries.map { e ->
                                    if (e.id != entryId) e
                                    else e.copy(note = listOf(e.note, text).filter { it.isNotBlank() }.joinToString("\n"))
                                })
                            })
                        }
                    }
                    if (notifId >= 0) Notifications.cancel(context, notifId)
                }
            }
            ACTION_MEMBER -> {
                // „Mitarbeiter zuordnen?“ (PLAN 3.6): „Name, Rolle“ typed into the notification.
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifications.KEY_MEMBER)?.toString()?.trim()
                val orgId = intent.getStringExtra(EXTRA_CONTACT) ?: return
                val entryId = intent.getStringExtra(EXTRA_ENTRY) ?: return
                val number = intent.getStringExtra(EXTRA_NUMBER) ?: return
                runAsync(context) {
                    if (!text.isNullOrEmpty()) OrbitRepository.update { d -> Mutations.memberFromCall(d, orgId, entryId, text, number) }
                    if (notifId >= 0) Notifications.cancel(context, notifId)
                }
            }
            ACTION_CALLBACK -> {
                val number = intent.getStringExtra(EXTRA_NUMBER) ?: return
                val name = intent.getStringExtra(EXTRA_NAME).orEmpty().ifBlank { number }
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 15)
                CallbackWorker.schedule(context, name, number, intent.getStringExtra(EXTRA_CONTACT), minutes)
                Notifications.cancel(context, Notifications.ID_CALLER)
                android.widget.Toast.makeText(context, if (minutes >= 60) "Erinnerung in 1 Std" else "Erinnerung in $minutes Min", android.widget.Toast.LENGTH_SHORT).show()
            }
            ACTION_UNARCHIVE -> {
                val contactId = intent.getStringExtra(EXTRA_CONTACT) ?: return
                val number = intent.getStringExtra(EXTRA_NUMBER)
                val tappedAt = android.os.SystemClock.elapsedRealtime()
                runAsync(context) {
                    OrbitRepository.update { d -> Mutations.unarchive(d, contactId) }
                    OrbitRepository.flush()
                    // Still ringing: the card stays, now without the archive hint.
                    val card = number?.takeIf { Notifications.callerShown(context) }?.let { n ->
                        val iso = io.github.thelony97.orbit.domain.Phones.countryIso(context)
                        io.github.thelony97.orbit.store.CallerIndexFile.read(context)
                            ?.lookup(n) { io.github.thelony97.orbit.domain.Phones.normalize(it, iso) }
                    }
                    val ongoing = { !io.github.thelony97.orbit.sync.PhoneStateReceiver.endedSince(tappedAt) && Notifications.callerShown(context) }
                    if (card != null && !card.archived) Notifications.callerInfo(context, card, number, ongoing)
                    else Notifications.cancel(context, Notifications.ID_CALLER)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(context, "Zurückgeholt", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            ACTION_MEETING -> {
                val meetingId = intent.getStringExtra(EXTRA_MEETING) ?: return
                val happened = intent.getBooleanExtra(EXTRA_HAPPENED, false)
                runAsync(context) {
                    OrbitRepository.update { d -> Mutations.confirmMeeting(d, meetingId, happened) }
                    if (notifId >= 0) Notifications.cancel(context, notifId)
                }
            }
        }
    }
}

/**
 * Invisible activity behind „Gratulieren“ in the birthday notification (PLAN 3.5): opens WhatsApp
 * or SMS with the greeting and ticks „Gratuliert“ for this year. An activity, not a receiver, so
 * starting the messenger is allowed.
 */
class CongratulateActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_CONTACT = "contact"
        private const val EXTRA_OCCASION = "occasion"
        private const val EXTRA_YEAR = "year"

        fun pendingIntent(ctx: Context, contactId: String, occasionId: String, year: Int): PendingIntent {
            val intent = Intent(ctx, CongratulateActivity::class.java)
                .putExtra(EXTRA_CONTACT, contactId)
                .putExtra(EXTRA_OCCASION, occasionId)
                .putExtra(EXTRA_YEAR, year)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(
                ctx, ("congrats-$contactId").hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.cancel(this, Notifications.ID_DIGEST)
        val contactId = intent.getStringExtra(EXTRA_CONTACT)
        val occasionId = intent.getStringExtra(EXTRA_OCCASION)
        val year = intent.getIntExtra(EXTRA_YEAR, 0)
        lifecycleScope.launch {
            OrbitRepository.init(applicationContext)
            val data = OrbitRepository.ensureLoaded()
            data.contacts.firstOrNull { it.id == contactId }?.let { c ->
                val opened = ContactActions.congratulate(this@CongratulateActivity, c, ContactActions.greeting(c))
                if (opened && occasionId != null && data.settings.giftTracking) {
                    OrbitRepository.update { d -> Mutations.markCongrats(d, c.id, occasionId, year) }
                    OrbitRepository.flush()
                }
            }
            finish()
        }
    }
}

/** Invisible activity behind the "Anrufen" button of a notification. */
class CallActionActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_CONTACT = "contact"
        private const val EXTRA_WHATSAPP = "whatsapp"

        /** Favoriten-Widget (PLAN 3.14): anrufen or WhatsApp with one tap; unique data per row. */
        fun intent(ctx: Context, contactId: String, whatsapp: Boolean): Intent =
            Intent(ctx, CallActionActivity::class.java)
                .setData(android.net.Uri.parse("orbit://" + (if (whatsapp) "whatsapp/" else "call/") + contactId))
                .putExtra(EXTRA_CONTACT, contactId)
                .putExtra(EXTRA_WHATSAPP, whatsapp)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun pendingIntent(ctx: Context, contactId: String): PendingIntent {
            val intent = Intent(ctx, CallActionActivity::class.java)
                .putExtra(EXTRA_CONTACT, contactId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(
                ctx, contactId.hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.cancel(this, Notifications.ID_DIGEST)
        val contactId = intent.getStringExtra(EXTRA_CONTACT)
        lifecycleScope.launch {
            OrbitRepository.init(applicationContext)
            val data = OrbitRepository.ensureLoaded()
            data.contacts.firstOrNull { it.id == contactId && it.deletedAt == null }?.let { c ->
                if (intent.getBooleanExtra(EXTRA_WHATSAPP, false)) ContactActions.whatsapp(this@CallActionActivity, c, null)
                else ContactActions.call(this@CallActionActivity, c, null, data.settings)
            }
            finish()
        }
    }
}

/** „Später zurückrufen“: posts the reminder after the chosen delay (survives reboots via WorkManager). */
class CallbackWorker(ctx: Context, params: androidx.work.WorkerParameters) : androidx.work.CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val number = inputData.getString(KEY_NUMBER) ?: return Result.success()
        Notifications.callback(applicationContext, inputData.getString(KEY_NAME).orEmpty().ifBlank { number }, number, inputData.getString(KEY_CONTACT))
        return Result.success()
    }

    companion object {
        private const val KEY_NAME = "name"
        private const val KEY_NUMBER = "number"
        private const val KEY_CONTACT = "contact"

        fun schedule(ctx: Context, name: String, number: String, contactId: String?, minutes: Int) {
            // PLAN 9 B3: it waits in the Rückruf-Stapel too, until called or swiped away.
            io.github.thelony97.orbit.call.LaterCalls.add(ctx, number)
            val data = androidx.work.Data.Builder()
                .putString(KEY_NAME, name)
                .putString(KEY_NUMBER, number)
                .putString(KEY_CONTACT, contactId)
                .build()
            val request = androidx.work.OneTimeWorkRequestBuilder<CallbackWorker>()
                .setInitialDelay(minutes.toLong(), java.util.concurrent.TimeUnit.MINUTES)
                .setInputData(data)
                .build()
            // One reminder per number: a new choice replaces the old one.
            androidx.work.WorkManager.getInstance(ctx)
                .enqueueUniqueWork("callback-" + number.filter { it.isDigit() }, androidx.work.ExistingWorkPolicy.REPLACE, request)
        }
    }
}
