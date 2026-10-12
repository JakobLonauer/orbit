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
import io.github.thelony97.orbit.domain.DueState
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
        val idx = Index(data)
        val active = Logic.active(data).filterNot { it.archived }

        val due = active.mapNotNull { c ->
            val info = Logic.due(c, idx, todayDate)
            if (info.state == DueState.DUE && info.dueDate != null && c.remindedDueDay != info.dueDate.toEpochDay()) {
                Triple(c, Logic.daysSinceLastContact(c, todayDate), info.dueDate.toEpochDay())
            } else {
                null
            }
        }
        val birthdays = active.filter { c -> c.birthday?.let { Logic.isToday(it, todayDate) } == true }
        val anniversaries = active.flatMap { c ->
            c.anniversaries.filter { Logic.isToday(it.date, todayDate) }.map { a ->
                val years = a.date.year?.let { todayDate.year - it }
                c to (if (years != null && years > 0) "${a.label} ($years Jahre)" else a.label)
            }
        }
        Notifications.digest(
            ctx,
            Notifications.DigestContent(
                due = due.map { it.first to it.second },
                birthdays = birthdays,
                anniversaries = anniversaries,
                lastNameFirst = data.settings.sortByLastName,
            ),
        )

        val startOfToday = todayDate.startMillis()
        val meetingsToAsk = data.meetings.filter { !it.asked && !it.resolved && it.end < startOfToday }
        meetingsToAsk.forEach { m ->
            data.contacts.firstOrNull { it.id == m.contactId && it.deletedAt == null }?.let { c ->
                Notifications.meetingFollowUp(ctx, m, c)
            }
        }

        val remindedIds = due.associate { it.first.id to it.third }
        val askedIds = meetingsToAsk.map { it.id }.toSet()
        OrbitRepository.update { d ->
            d.copy(
                contacts = d.contacts.map { c -> remindedIds[c.id]?.let { c.copy(remindedDueDay = it) } ?: c },
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
    override fun onReceive(context: Context, intent: Intent) = runAsync(context) {
        DigestScheduler.schedule(context)
        Sync.schedulePeriodic(context)
        Backups.schedule(context)
    }
}

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_NOTE = "io.github.thelony97.orbit.NOTE"
        const val ACTION_MEETING = "io.github.thelony97.orbit.MEETING"
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

/** Invisible activity behind the "Anrufen" button of a notification. */
class CallActionActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_CONTACT = "contact"

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
            data.contacts.firstOrNull { it.id == contactId }?.let { c ->
                ContactActions.call(this@CallActionActivity, c, null, data.settings)
            }
            finish()
        }
    }
}
