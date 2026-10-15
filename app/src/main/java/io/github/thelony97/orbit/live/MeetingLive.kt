package io.github.thelony97.orbit.live

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.thelony97.orbit.R
import io.github.thelony97.orbit.core.domain.LiveMeeting
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.notify.Notifications
import io.github.thelony97.orbit.notify.runAsync
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Treffen als Live-Update (PLAN 9 C2/C3): from 30 minutes before a meeting a quiet live chip
 * counts down to it („Anna · 14:00“) with „+5 / +10 / +15 Min“ (Spät dran); from the start it
 * counts the meeting and goes at its end. Only when nothing ranks higher (call, Dranbleiben).
 */
object MeetingLive {

    private const val ID_BASE = 70_000
    private const val HORIZON = 48 * 3_600_000L
    private const val ACTION_SOON = "io.github.thelony97.orbit.MEETING_SOON"
    const val EXTRA_ID = "meeting"

    private fun id(meetingId: String) = ID_BASE + (meetingId.hashCode() and 0x0FFF)

    /** Alarms for the meetings of the next two days (app start, every save, boot). */
    fun schedule(ctx: Context, data: OrbitData) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        data.meetings.filter { !it.resolved && it.end > now && it.start - now < HORIZON }.forEach { m ->
            // A few seconds inside the 30 minutes, so the alarm never fires just before them.
            val at = maxOf(now + 1_000, m.start - LiveMeeting.SOON_MS + 5_000)
            val pi = PendingIntent.getBroadcast(
                ctx, id(m.id), Intent(ctx, MeetingLiveReceiver::class.java).setAction(ACTION_SOON).putExtra(EXTRA_ID, m.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            runCatching {
                if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
            // The start: from „soon“ to „on“.
            if (m.start > now) {
                val start = PendingIntent.getBroadcast(
                    ctx, id(m.id) + 0x1000, Intent(ctx, MeetingLiveReceiver::class.java).setAction(ACTION_SOON).putExtra(EXTRA_ID, m.id),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                runCatching {
                    if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, m.start, start)
                    else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, m.start, start)
                }
            }
        }
    }

    /** Posts (or updates) the live chip of [meetingId], if it is still soon or on. */
    @android.annotation.SuppressLint("MissingPermission")
    fun post(ctx: Context, data: OrbitData, meetingId: String) {
        val now = System.currentTimeMillis()
        val m = data.meetings.firstOrNull { it.id == meetingId } ?: return
        val c = data.contacts.firstOrNull { it.id == m.contactId && it.deletedAt == null && !it.archived } ?: return
        val phase = LiveMeeting.now(listOf(m), now) { true } ?: return cancel(ctx, meetingId)
        // One live thing at a time: a call or a Dranbleiben run ranks higher.
        if (io.github.thelony97.orbit.call.CallSession.calls.value.isNotEmpty() || io.github.thelony97.orbit.call.Persist.run.value != null) return
        if (!Notifications.canPost(ctx)) return
        val name = io.github.thelony97.orbit.domain.Logic.shortName(c)
        val soon = phase is LiveMeeting.Phase.Soon
        val builder = NotificationCompat.Builder(ctx, Notifications.CH_MEETING)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFA78BFF.toInt())
            .setContentTitle("$name · " + io.github.thelony97.orbit.util.Fmt.time(m.start))
            .setContentText(m.place.ifBlank { if (soon) "Treffen" else "Treffen läuft" })
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(Notifications.openContact(ctx, c.id, id(m.id)))
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(name)
            .setUsesChronometer(true)
            .setChronometerCountDown(soon)
            .setWhen(m.start)
            .setShowWhen(true)
            .setTimeoutAfter((m.end - now).coerceAtLeast(60_000L))
        if (soon) {
            LiveMeeting.steps.forEachIndexed { i, minutes ->
                builder.addAction(0, "+$minutes Min", LateActivity.pending(ctx, c.id, minutes, id(m.id) * 4 + i))
            }
        }
        runCatching { NotificationManagerCompat.from(ctx).notify(id(m.id), builder.build()) }
    }

    fun cancel(ctx: Context, meetingId: String) = NotificationManagerCompat.from(ctx).cancel(id(meetingId))
}

class MeetingLiveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(MeetingLive.EXTRA_ID) ?: return
        runAsync(context) { MeetingLive.post(context, OrbitRepository.snapshot(), id) }
    }
}

/**
 * „+10 Min“ from the live chip (PLAN 9 C3): sends „Bin ca. 10 Min später da“ – an activity, so the
 * prepared WhatsApp/SMS may open when Orbit is not allowed to send by itself.
 */
class LateActivity : androidx.activity.ComponentActivity() {
    companion object {
        private const val EXTRA_CONTACT = "contact"
        private const val EXTRA_MINUTES = "minutes"

        fun pending(ctx: Context, contactId: String, minutes: Int, requestCode: Int): PendingIntent = PendingIntent.getActivity(
            ctx,
            requestCode,
            Intent(ctx, LateActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_CONTACT, contactId)
                .putExtra(EXTRA_MINUTES, minutes),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getStringExtra(EXTRA_CONTACT)
        val minutes = intent?.getIntExtra(EXTRA_MINUTES, 0) ?: 0
        OrbitRepository.init(applicationContext)
        lifecycleScope.launch {
            val c = runCatching { OrbitRepository.ensureLoaded() }.getOrNull()?.contacts?.firstOrNull { it.id == id }
            if (c != null && minutes > 0) io.github.thelony97.orbit.actions.ContactActions.late(this@LateActivity, c, LiveMeeting.message(minutes))
            finish()
        }
    }
}
