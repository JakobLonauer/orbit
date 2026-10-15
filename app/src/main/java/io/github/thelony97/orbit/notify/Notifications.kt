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
    /** Caller info while it rings: lock screen forced to the public version (name and role). */
    const val CH_CALLER = "caller"
    /** Calls while Orbit is the phone app: silent (the system rings), private on the lock screen; see CallNotification.ensureChannel. */
    const val CH_INCALL = "incall"
    const val CH_MEETING = "meeting"
    const val CH_SYSTEM = "system"

    const val ID_DIGEST = 1000
    const val ID_TOPICS = 1001
    const val ID_BACKUP = 1002
    const val ID_CALLER = 1003
    /** 1004 is the call itself (call/CallNotification). */
    const val ID_PHONE_FALLBACK = 1005
    const val ID_TOPICS_DAY = 1006
    /** PLAN 9 B3: „3 Rückrufe“ after a busy calendar event. */
    const val ID_CALLBACKS = 1007
    private const val ID_AFTER_CALL_BASE = 20_000
    private const val ID_MEETING_BASE = 30_000
    private const val ID_CALLBACK_BASE = 40_000
    private const val ID_UNKNOWN_BASE = 50_000
    private const val ID_BRIEF_BASE = 60_000
    /** Each kind keeps to its own range of 4096 ids (ids double as PendingIntent request codes). */
    private const val ID_MASK = 0x0FFF

    const val KEY_NOTE = "note"
    const val KEY_MEMBER = "member"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_DIGEST, "Tägliche Übersicht", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Anlässe und Themen des Tages"
                },
                NotificationChannel(CH_CALL, "Anruf-Helfer", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Themen während des Anrufs und Notiz danach"
                },
                NotificationChannel(CH_CALLER, "Anrufer-Info", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Wer anruft, beim Klingeln"
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                },
                NotificationChannel(CH_MEETING, "Treffen", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Treffen am Morgen und Nachfrage danach"
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
        .setColor(0xFFA78BFF.toInt())
        .setAutoCancel(true)

    /** A contact photo for a notification's large icon (a quarter of the stored size). */
    private fun photoOf(ctx: Context, name: String?): android.graphics.Bitmap? = name?.takeIf { it.isNotBlank() }?.let {
        runCatching {
            val file = java.io.File(java.io.File(ctx.filesDir, "photos"), it)
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })
        }.getOrNull()
    }

    /**
     * Tägliche Anlässe (PLAN 3.5/3.13a), gathered in one notification: „Heute: Annas 30. Geburtstag ·
     * In 7 Tagen: 5. Hochzeitstag Anna & Tom“. Lead-time birthday reminders list the gift ideas.
     * One person only: photo, „Anrufen“, and on the birthday itself „Gratulieren“.
     */
    fun occasions(ctx: Context, due: List<io.github.thelony97.orbit.core.domain.DueReminder>, data: io.github.thelony97.orbit.data.OrbitData) {
        if (due.isEmpty()) return
        val byId = data.contacts.associateBy { it.id }
        val nameOf = { id: String -> byId[id]?.let { Logic.shortName(it) }.orEmpty() }
        val lines = due.map { r ->
            val head = io.github.thelony97.orbit.core.domain.ReminderPlan.dayWord(r.daysBefore) + ": " +
                io.github.thelony97.orbit.core.domain.ReminderPlan.phrase(r.upcoming, nameOf)
            val ideas = if (r.withGiftIdeas) byId[r.upcoming.ownerId]?.gifts?.filterNot { it.done }?.joinToString(", ") { it.text }.orEmpty() else ""
            if (ideas.isNotEmpty()) "$head · 🎁 $ideas" else head
        }
        val people = due.flatMap { listOfNotNull(it.upcoming.ownerId, it.upcoming.withId) }.distinct()
        val single = people.singleOrNull()?.let { byId[it] }
        val style = NotificationCompat.InboxStyle()
        lines.take(7).forEach { style.addLine(it) }
        if (lines.size > 7) style.setSummaryText("+${lines.size - 7} weitere")
        val builder = base(ctx, CH_DIGEST)
            .setContentTitle(lines.first())
            .setContentText(if (lines.size > 1) lines.drop(1).joinToString(" · ") else null)
            .setStyle(if (lines.size > 1) style else NotificationCompat.BigTextStyle().bigText(lines.first()))
            .setContentIntent(openContact(ctx, single?.id, ID_DIGEST))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        if (single != null) {
            photoOf(ctx, single.photo)?.let { builder.setLargeIcon(it) }
            val birthdayToday = due.firstOrNull { it.daysBefore == 0 && it.upcoming.occasion.kind == io.github.thelony97.orbit.core.model.OccasionKind.BIRTHDAY }
            if (single.phones.any { it.value.isNotBlank() }) {
                if (birthdayToday != null) {
                    builder.addAction(0, "Gratulieren", CongratulateActivity.pendingIntent(ctx, single.id, birthdayToday.upcoming.occasion.id, birthdayToday.upcoming.date.year))
                }
                builder.addAction(0, "Anrufen", CallActionActivity.pendingIntent(ctx, single.id))
            }
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

    /**
     * Themen mit Datum (PLAN 3.3/3.13a): „Heute ansprechen“ in the morning. One person → photo,
     * „Anrufen“ and a tap into the contact; on the lock screen only that topics are due.
     */
    fun topicsDue(ctx: Context, due: List<io.github.thelony97.orbit.core.domain.TopicPlan.Due>, data: io.github.thelony97.orbit.data.OrbitData) {
        if (due.isEmpty()) return
        val byId = data.contacts.associateBy { it.id }
        val single = due.map { it.contactId }.distinct().singleOrNull()?.let { byId[it] }
        // Caught-up days (phone was off) are no longer „heute“.
        val heading = if (due.all { it.day == io.github.thelony97.orbit.domain.today() }) "Heute ansprechen" else "Ansprechen"
        val builder = base(ctx, CH_DIGEST)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openContact(ctx, single?.id, ID_TOPICS_DAY))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(base(ctx, CH_DIGEST).setContentTitle("Heute ansprechen").build())
        if (single != null) {
            val texts = due.map { it.topic.text }
            builder.setContentTitle(heading + ": " + Logic.shortName(single))
                .setContentText(texts.joinToString(" · "))
                .setStyle(NotificationCompat.InboxStyle().also { st -> texts.take(7).forEach { st.addLine("• $it") } })
            photoOf(ctx, single.photo)?.let { builder.setLargeIcon(it) }
            if (single.phones.any { it.value.isNotBlank() }) builder.addAction(0, "Anrufen", CallActionActivity.pendingIntent(ctx, single.id))
        } else {
            val lines = due.map { d -> (byId[d.contactId]?.let { Logic.shortName(it) } ?: "") + ": " + d.topic.text }
            builder.setContentTitle(heading)
                .setContentText(lines.joinToString(" · "))
                .setStyle(NotificationCompat.InboxStyle().also { st ->
                    lines.take(7).forEach { st.addLine(it) }
                    if (lines.size > 7) st.setSummaryText("+${lines.size - 7} weitere")
                })
        }
        post(ctx, ID_TOPICS_DAY, builder)
    }

    fun cancelTopics(ctx: Context) {
        cancel(ctx, ID_TOPICS)
        cancel(ctx, ID_CALLER)
    }

    /**
     * Anrufer-Info beim Klingeln (PLAN 3.12, without the dialer role): name, photo, role, last
     * contact, open topics, customer number. The lock screen only gets name and role.
     */
    fun callerInfo(
        ctx: Context,
        card: io.github.thelony97.orbit.core.domain.CallerCard,
        number: String,
        /** Checked right before posting: the call may have ended while the card was built. */
        stillValid: () -> Boolean = { true },
    ) {
        val n = io.github.thelony97.orbit.core.domain.CallerNotice.of(card, System.currentTimeMillis(), java.time.ZoneId.systemDefault())
        val photo = card.photo?.let { name ->
            runCatching {
                val file = java.io.File(java.io.File(ctx.filesDir, "photos"), name)
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
            }.getOrNull()
        }
        val public = base(ctx, CH_CALLER)
            .setContentTitle(n.publicTitle)
            .setContentText(n.publicLine)
        val builder = base(ctx, CH_CALLER)
            .setContentTitle(n.title)
            .setContentText(n.line.ifBlank { n.details.firstOrNull().orEmpty() })
            .setStyle(NotificationCompat.BigTextStyle().bigText((listOf(n.line) + n.details).filter { it.isNotBlank() }.joinToString("\n")))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public.build())
            .setContentIntent(openContact(ctx, card.contactId, ID_CALLER))
            .setTimeoutAfter(3 * 60 * 60 * 1000L)
        if (photo != null) builder.setLargeIcon(photo)
        // Archived (PLAN 3.11): the hint is in the title, „Zurückholen“ is one tap away.
        if (card.archived) builder.addAction(0, "Zurückholen", unarchiveIntent(ctx, card.contactId, number))
        // „Später zurückrufen“ (PLAN 3.12/3.13a): one tap while it rings, a reminder later.
        builder.addAction(0, "In 15 Min erinnern", callbackIntent(ctx, card.name, number, card.contactId, 15))
        builder.addAction(0, "In 1 Std erinnern", callbackIntent(ctx, card.name, number, card.contactId, 60))
        if (stillValid()) post(ctx, ID_CALLER, builder)
    }

    /**
     * Unknown number ringing (PLAN 3.12 without the phone role): what the number itself says
     * (PLAN 9 B1 „Linz · Festnetz“, „0900 · teuer“), Herold's name once it is known, how often it
     * called before, plus the reminder actions.
     */
    fun unknownCaller(
        ctx: Context,
        unknown: io.github.thelony97.orbit.data.UnknownNumber?,
        number: String,
        info: io.github.thelony97.orbit.core.domain.NumberInfo.Info? = null,
        hit: io.github.thelony97.orbit.core.domain.Herold.Hit? = null,
    ) {
        val days = unknown?.let {
            java.time.temporal.ChronoUnit.DAYS.between(
                java.time.Instant.ofEpochMilli(it.lastAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                java.time.LocalDate.now(),
            )
        }
        val title = hit?.name ?: unknown?.cachedName?.takeIf { it.isNotBlank() } ?: unknown?.display ?: number
        val times = when (unknown?.calls ?: 0) {
            0 -> null
            1 -> "1 Anruf"
            else -> "${unknown?.calls} Anrufe"
        }
        val last = unknown?.lastAt?.takeIf { it > 0 }?.let { "zuletzt " + io.github.thelony97.orbit.core.domain.AtFormat.ago(days ?: 0) }
        val line = listOfNotNull(
            hit?.category?.takeIf { it.isNotBlank() },
            info?.line?.takeIf { it.isNotBlank() } ?: "Unbekannt",
            times,
            last,
        ).joinToString(" · ")
        val builder = base(ctx, CH_CALLER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(base(ctx, CH_CALLER).setContentTitle("Anruf").setContentText("Unbekannt").build())
            .setContentTitle(title)
            .setContentText(line)
            .setOngoing(true)
            // The Herold answer updates the same card without a second sound.
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openContact(ctx, null, ID_CALLER))
            .setTimeoutAfter(3 * 60 * 60 * 1000L)
            .addAction(0, "In 15 Min erinnern", callbackIntent(ctx, title, number, null, 15))
            .addAction(0, "In 1 Std erinnern", callbackIntent(ctx, title, number, null, 60))
        post(ctx, ID_CALLER, builder)
    }

    private fun callbackIntent(ctx: Context, name: String, number: String, contactId: String?, minutes: Int): PendingIntent {
        val intent = Intent(ctx, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_CALLBACK
            putExtra(NotificationActionReceiver.EXTRA_NAME, name)
            putExtra(NotificationActionReceiver.EXTRA_NUMBER, number)
            putExtra(NotificationActionReceiver.EXTRA_CONTACT, contactId)
            putExtra(NotificationActionReceiver.EXTRA_MINUTES, minutes)
        }
        return PendingIntent.getBroadcast(ctx, ID_CALLER * 10 + minutes, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun unarchiveIntent(ctx: Context, contactId: String, number: String): PendingIntent {
        val intent = Intent(ctx, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_UNARCHIVE
            putExtra(NotificationActionReceiver.EXTRA_CONTACT, contactId)
            putExtra(NotificationActionReceiver.EXTRA_NUMBER, number)
        }
        return PendingIntent.getBroadcast(ctx, ID_CALLER * 10 + 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Whether the caller card is still on screen (it is ongoing until the call ends). */
    fun callerShown(ctx: Context): Boolean = runCatching {
        ctx.getSystemService(android.app.NotificationManager::class.java)?.activeNotifications?.any { it.id == ID_CALLER } == true
    }.getOrDefault(false)

    fun callbackId(number: String) =ID_CALLBACK_BASE + (number.filter { it.isDigit() }.hashCode() and ID_MASK)

    /** After a call with an unknown number: create it as a person or an organisation in one tap. */
    fun unknownAfterCall(ctx: Context, number: String, durationSec: Long, cachedName: String?) {
        val id = ID_UNKNOWN_BASE + (number.filter { it.isDigit() }.hashCode() and ID_MASK)
        fun create(org: Boolean): PendingIntent {
            val intent = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_NEW_NUMBER, number)
                putExtra(MainActivity.EXTRA_NEW_ORG, org)
                // Buttons do not auto-dismiss: the app cancels it when it opens the draft.
                putExtra(MainActivity.EXTRA_NOTIF, id)
            }
            // Separate request codes for the two buttons (org flag differs, extras alone don't count).
            return PendingIntent.getActivity(ctx, id * 2 + if (org) 1 else 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val builder = base(ctx, CH_CALL)
            .setContentTitle(cachedName?.takeIf { it.isNotBlank() } ?: number)
            .setContentText("Unbekannt · " + Fmt.duration(durationSec))
            .setContentIntent(create(false))
            .addAction(0, "Als Kontakt anlegen", create(false))
            .addAction(0, "Als Organisation anlegen", create(true))
            .setTimeoutAfter(12 * 60 * 60 * 1000L)
        post(ctx, id, builder)
    }

    /** PLAN 9 B3: „3 Rückrufe“ – a calendar appointment just ended and people are waiting. */
    fun callbacks(ctx: Context, count: Int, names: List<String>) {
        val open = PendingIntent.getActivity(
            ctx, ID_CALLBACKS,
            Intent(ctx, MainActivity::class.java)
                .setAction(io.github.thelony97.orbit.actions.AppShortcuts.ACTION_CALLBACKS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = base(ctx, CH_CALL)
            .setContentTitle(if (count == 1) "1 Rückruf" else "$count Rückrufe")
            .setContentText(names.joinToString(" · ") + if (count > names.size) " …" else "")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(2 * 60 * 60 * 1000L)
        post(ctx, ID_CALLBACKS, builder)
    }

    /** The reminder itself: "Zurückrufen: Anna" with a call button. */
    fun callback(ctx: Context, name: String, number: String, contactId: String?) {
        val id = callbackId(number)
        val callIntent = if (ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            Intent(Intent.ACTION_CALL, android.net.Uri.fromParts("tel", number, null))
        } else {
            Intent(Intent.ACTION_DIAL, android.net.Uri.fromParts("tel", number, null))
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val call = PendingIntent.getActivity(ctx, id, callIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = base(ctx, CH_CALL)
            .setContentTitle("Zurückrufen: $name")
            .setContentText(number)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contactId?.let { openContact(ctx, it, id) } ?: call)
            .addAction(0, "Anrufen", call)
            .setTimeoutAfter(2 * 60 * 60 * 1000L)
        post(ctx, id, builder)
    }

    fun afterCallId(entry: Entry) = ID_AFTER_CALL_BASE + (entry.id.hashCode() and ID_MASK)

    /**
     * After a call: „Notiz“ as a reply line; from an unknown Durchwahl in an organisation's number
     * range also „Mitarbeiter“ (PLAN 3.6 „Mitarbeiter zuordnen?“) – „Name, Rolle“ creates them.
     */
    fun afterCall(ctx: Context, contact: Contact, entry: Entry, note: Boolean = true, extension: String? = null) {
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
            .setContentText(if (extension != null) "$extension · Mitarbeiter zuordnen?" else "Willst du eine Notiz festhalten?")
            // Name and number only once the phone is unlocked.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(base(ctx, CH_CALL).setContentTitle("Gespräch beendet").build())
        if (note) builder.addAction(action)
        if (extension != null && contact.isOrg) {
            val memberIntent = Intent(ctx, NotificationActionReceiver::class.java).apply {
                this.action = NotificationActionReceiver.ACTION_MEMBER
                putExtra(NotificationActionReceiver.EXTRA_CONTACT, contact.id)
                putExtra(NotificationActionReceiver.EXTRA_ENTRY, entry.id)
                putExtra(NotificationActionReceiver.EXTRA_NUMBER, extension)
                putExtra(NotificationActionReceiver.EXTRA_NOTIF, notifId)
            }
            val memberPending = PendingIntent.getBroadcast(
                ctx, notifId + 1, memberIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            builder.addAction(
                NotificationCompat.Action.Builder(0, "Mitarbeiter", memberPending)
                    .addRemoteInput(RemoteInput.Builder(KEY_MEMBER).setLabel("Name, Rolle").build())
                    .setAllowGeneratedReplies(false)
                    .build(),
            )
        }
        builder
            .setContentIntent(openContact(ctx, contact.id, notifId))
            .setTimeoutAfter(12 * 60 * 60 * 1000L)
        post(ctx, notifId, builder)
    }

    fun meetingId(m: Meeting) = ID_MEETING_BASE + (m.id.hashCode() and ID_MASK)

    /**
     * Am Morgen eines geplanten Treffens (PLAN 3.13a): „Heute 18:00 · Anna · Café“ with the open
     * topics, the last note and partner/children. Private on the lock screen.
     */
    fun meetingBrief(ctx: Context, b: io.github.thelony97.orbit.core.domain.MeetingBrief.Brief, data: io.github.thelony97.orbit.data.OrbitData) {
        val byId = data.contacts.associateBy { it.id }
        val c = byId[b.contactId] ?: return
        val names = { ids: List<String> -> ids.mapNotNull { byId[it]?.let { p -> Logic.shortName(p) } }.joinToString(", ") }
        val lines = buildList {
            b.topics.take(5).forEach { add("• $it") }
            b.lastNote?.let { add("Zuletzt: $it") }
            val family = listOfNotNull(
                names(b.partners).takeIf { it.isNotEmpty() },
                names(b.children).takeIf { it.isNotEmpty() }?.let { if (b.children.size == 1) "Kind: $it" else "Kinder: $it" },
            )
            if (family.isNotEmpty()) add(family.joinToString(" · "))
        }
        // Nothing to have in mind: the calendar reminds of the meeting itself (W5).
        if (lines.isEmpty()) return
        val title = "Heute " + Fmt.time(b.meeting.start) + " · " + Logic.shortName(c) + if (b.meeting.place.isNotBlank()) " · ${b.meeting.place}" else ""
        val id = ID_BRIEF_BASE + (b.meeting.id.hashCode() and ID_MASK)
        val builder = base(ctx, CH_MEETING)
            .setContentTitle(title)
            .setContentText(lines.joinToString(" · ").ifEmpty { null })
            .setStyle(NotificationCompat.InboxStyle().also { st -> lines.forEach { st.addLine(it) } })
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openContact(ctx, c.id, id))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(base(ctx, CH_MEETING).setContentTitle("Treffen heute " + Fmt.time(b.meeting.start)).build())
        photoOf(ctx, c.photo)?.let { builder.setLargeIcon(it) }
        post(ctx, id, builder)
    }

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

    /** PLAN 3.12: Android used the preinstalled phone app although Orbit is the phone app. */
    fun phoneFallback(ctx: Context) {
        val builder = base(ctx, CH_SYSTEM)
            .setContentTitle("Anruf lief über die System-Telefon-App")
            .setContentText("Orbit konnte den Anruf nicht übernehmen")
            .setContentIntent(openContact(ctx, null, ID_PHONE_FALLBACK))
        post(ctx, ID_PHONE_FALLBACK, builder)
    }

    fun backupFailed(ctx: Context, reason: String) {
        val builder = base(ctx, CH_SYSTEM)
            .setContentTitle("Backup fehlgeschlagen")
            .setContentText(reason)
            .setContentIntent(openContact(ctx, null, ID_BACKUP))
        post(ctx, ID_BACKUP, builder)
    }
}
