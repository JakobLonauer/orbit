package io.github.thelony97.orbit.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import io.github.thelony97.orbit.MainActivity
import io.github.thelony97.orbit.R
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.Entry
import io.github.thelony97.orbit.data.Meeting
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.util.Fmt

object Notifications {

    const val CH_DIGEST = "digest"
    const val CH_CALL = "call"
    const val CH_MEETING = "meeting"
    const val CH_SYSTEM = "system"

    const val ID_DIGEST = 1000
    const val ID_TOPICS = 1001
    const val ID_BACKUP = 1002
    private const val ID_AFTER_CALL_BASE = 20_000
    private const val ID_MEETING_BASE = 30_000

    const val KEY_NOTE = "note"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_DIGEST, "Tägliche Übersicht", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Wer fällig ist, Geburtstage und Jahrestage"
                },
                NotificationChannel(CH_CALL, "Anruf-Helfer", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Themen während des Anrufs und Notiz danach"
                },
                NotificationChannel(CH_MEETING, "Treffen", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Nachfrage, ob ein geplantes Treffen stattgefunden hat"
                },
                NotificationChannel(CH_SYSTEM, "Backup & Updates", NotificationManager.IMPORTANCE_LOW),
            ),
        )
    }

    fun canPost(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    private fun post(ctx: Context, id: Int, builder: NotificationCompat.Builder) {
        if (!canPost(ctx)) return
        try {
            NotificationManagerCompat.from(ctx).notify(id, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancel(ctx: Context, id: Int) = NotificationManagerCompat.from(ctx).cancel(id)

    fun openContact(ctx: Context, contactId: String?, requestCode: Int): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            contactId?.let { putExtra(MainActivity.EXTRA_CONTACT, it) }
        }
        return PendingIntent.getActivity(ctx, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun base(ctx: Context, channel: String) = NotificationCompat.Builder(ctx, channel)
        .setSmallIcon(R.drawable.ic_notification)
        .setAutoCancel(true)

    data class DigestContent(
        val due: List<Pair<Contact, Long?>>,
        val birthdays: List<Contact>,
        val anniversaries: List<Pair<Contact, String>>,
        val lastNameFirst: Boolean,
    ) {
        val isEmpty get() = due.isEmpty() && birthdays.isEmpty() && anniversaries.isEmpty()
    }

    fun digest(ctx: Context, content: DigestContent) {
        if (content.isEmpty) return
        val name = { c: Contact -> Logic.displayName(c, content.lastNameFirst) }
        val parts = buildList {
            if (content.birthdays.isNotEmpty()) add(if (content.birthdays.size == 1) "1 Geburtstag" else "${content.birthdays.size} Geburtstage")
            if (content.anniversaries.isNotEmpty()) add(if (content.anniversaries.size == 1) "1 Jahrestag" else "${content.anniversaries.size} Jahrestage")
            if (content.due.isNotEmpty()) add("${content.due.size} fällig")
        }
        val lines = buildList {
            content.birthdays.forEach { c ->
                val age = c.birthday?.let { Logic.turningAge(it) }
                add(if (age != null) "🎂 ${name(c)} wird $age" else "🎂 ${name(c)} hat Geburtstag")
            }
            content.anniversaries.forEach { (c, label) -> add("✨ ${name(c)}: $label") }
            content.due.forEach { (c, days) ->
                add(if (days != null) "👋 ${name(c)} · zuletzt ${Fmt.ago(days)}" else "👋 ${name(c)}")
            }
        }
        val style = NotificationCompat.InboxStyle()
        lines.take(7).forEach { style.addLine(it) }
        if (lines.size > 7) style.setSummaryText("+${lines.size - 7} weitere")

        val single = content.due.singleOrNull()?.first
            ?: (if (content.due.isEmpty() && content.birthdays.size == 1) content.birthdays.first() else null)
        val builder = base(ctx, CH_DIGEST)
            .setContentTitle("Heute: ${parts.joinToString(" · ")}")
            .setContentText(lines.firstOrNull())
            .setStyle(style)
            .setContentIntent(openContact(ctx, single?.id, ID_DIGEST))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        val callable = content.due.singleOrNull()?.first?.takeIf { it.phones.isNotEmpty() }
        if (callable != null) {
            builder.addAction(0, "Anrufen", CallActionActivity.pendingIntent(ctx, callable.id))
        }
        post(ctx, ID_DIGEST, builder)
    }

    fun topics(ctx: Context, contact: Contact) {
        val open = contact.topics.filterNot { it.done }
        if (open.isEmpty()) return
        val style = NotificationCompat.InboxStyle()
        open.take(7).forEach { style.addLine("• ${it.text}") }
        val builder = base(ctx, CH_CALL)
            .setContentTitle("Ansprechen mit ${contact.firstName.ifBlank { Logic.displayName(contact, false) }}")
            .setContentText(open.joinToString(" · ") { it.text })
            .setStyle(style)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openContact(ctx, contact.id, ID_TOPICS))
            .setTimeoutAfter(3 * 60 * 60 * 1000L)
        post(ctx, ID_TOPICS, builder)
    }

    fun cancelTopics(ctx: Context) = cancel(ctx, ID_TOPICS)

    fun afterCallId(entry: Entry) = ID_AFTER_CALL_BASE + (entry.id.hashCode() and 0x7FFF)

    fun afterCall(ctx: Context, contact: Contact, entry: Entry) {
        val notifId = afterCallId(entry)
        val replyIntent = Intent(ctx, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_NOTE
            putExtra(NotificationActionReceiver.EXTRA_CONTACT, contact.id)
            putExtra(NotificationActionReceiver.EXTRA_ENTRY, entry.id)
            putExtra(NotificationActionReceiver.EXTRA_NOTIF, notifId)
        }
        val replyPending = PendingIntent.getBroadcast(
            ctx, notifId, replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val remoteInput = RemoteInput.Builder(KEY_NOTE).setLabel("Worum ging's?").build()
        val action = NotificationCompat.Action.Builder(0, "Notiz", replyPending)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()
        val minutes = Fmt.duration(entry.durationSec ?: 0)
        val builder = base(ctx, CH_CALL)
            .setContentTitle("Gespräch mit ${Logic.displayName(contact, false)} ($minutes)")
            .setContentText("Willst du eine Notiz festhalten?")
            .addAction(action)
            .setContentIntent(openContact(ctx, contact.id, notifId))
            .setTimeoutAfter(12 * 60 * 60 * 1000L)
        post(ctx, notifId, builder)
    }

    fun meetingId(m: Meeting) = ID_MEETING_BASE + (m.id.hashCode() and 0x7FFF)

    fun meetingFollowUp(ctx: Context, meeting: Meeting, contact: Contact) {
        val notifId = meetingId(meeting)
        fun answer(happened: Boolean): PendingIntent {
            val intent = Intent(ctx, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_MEETING
                putExtra(NotificationActionReceiver.EXTRA_MEETING, meeting.id)
                putExtra(NotificationActionReceiver.EXTRA_HAPPENED, happened)
                putExtra(NotificationActionReceiver.EXTRA_NOTIF, notifId)
            }
            return PendingIntent.getBroadcast(
                ctx, notifId * 2 + if (happened) 1 else 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val builder = base(ctx, CH_MEETING)
            .setContentTitle("Hat das Treffen mit ${Logic.displayName(contact, false)} stattgefunden?")
            .setContentText(Fmt.dateTime(meeting.start) + if (meeting.place.isNotBlank()) " · ${meeting.place}" else "")
            .addAction(0, "Ja", answer(true))
            .addAction(0, "Nein", answer(false))
            .setContentIntent(openContact(ctx, contact.id, notifId))
        post(ctx, notifId, builder)
    }

    fun backupFailed(ctx: Context, reason: String) {
        val builder = base(ctx, CH_SYSTEM)
            .setContentTitle("Backup fehlgeschlagen")
            .setContentText(reason)
            .setContentIntent(openContact(ctx, null, ID_BACKUP))
        post(ctx, ID_BACKUP, builder)
    }
}
