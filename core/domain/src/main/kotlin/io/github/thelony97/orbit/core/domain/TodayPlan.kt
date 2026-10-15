package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.Meeting
import io.github.thelony97.orbit.core.model.OccasionKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Heute (PLAN 9 C1): what stands today, by fixed rules – occasions of the day, meetings still to
 * come, topics whose day has come (a week is caught up), and at most one „Heute vor N Jahren“.
 * Every item has a stable [Item.key]; Jakob's „Erledigt“ is stored under it.
 */
object TodayPlan {

    sealed interface Item {
        val key: String
        val contactId: String
    }

    data class Occasion(
        override val key: String,
        override val contactId: String,
        val withId: String?,
        val kind: OccasionKind,
        val label: String,
        val turning: Int?,
    ) : Item

    data class Meet(override val key: String, override val contactId: String, val meetingId: String, val start: Long, val place: String) : Item

    data class Topic(override val key: String, override val contactId: String, val topicId: String, val text: String, val day: LocalDate) : Item

    data class YearsAgo(
        override val key: String,
        override val contactId: String,
        val entryId: String,
        val years: Int,
        val typeId: String,
        val note: String,
    ) : Item

    private const val TOPIC_DAYS = 7L

    /** Keys of the day carry the date; older ones can go from the „erledigt“ list. */
    fun stale(key: String, today: LocalDate): Boolean {
        if (!key.startsWith("occ:") && !key.startsWith("ago:")) return false
        val day = runCatching { LocalDate.parse(key.substringAfterLast(':')) }.getOrNull() ?: return true
        return day.isBefore(today.minusDays(1))
    }

    fun build(
        contacts: List<Contact>,
        meetings: List<Meeting>,
        now: Long,
        zone: ZoneId,
        done: Set<String>,
        visible: (Contact) -> Boolean,
    ): List<Item> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val shown = contacts.filter(visible)
        val ids = shown.associateBy { it.id }
        val me = contacts.firstOrNull { it.isMe }?.id
        val out = ArrayList<Item>()
        out += meetingsToday(meetings, ids.keys, now, today, zone)
        out += occasionsToday(shown, today, me)
        out += topicsDue(shown, today)
        yearsAgo(shown, today, zone)?.let { out += it }
        return out.filter { it.key !in done }
    }

    /** Meetings still to come today, by time. */
    private fun meetingsToday(meetings: List<Meeting>, ids: Set<String>, now: Long, today: LocalDate, zone: ZoneId): List<Item> =
        meetings.filter { m -> !m.resolved && m.end > now && m.contactId in ids && dayOf(m.start, zone) == today }
            .sortedBy { it.start }
            .map { m -> Meet("meet:${m.id}", m.contactId, m.id, m.start, m.place) }

    /** Occasions of the day (one's own birthday is no card for oneself). */
    private fun occasionsToday(shown: List<Contact>, today: LocalDate, me: String?): List<Item> =
        UpcomingIndex.build(shown, today) { true }
            .filter { it.daysUntil == 0L && !(it.ownerId == me && it.occasion.kind == OccasionKind.BIRTHDAY) }
            .map { u -> Occasion("occ:${u.ownerId}:${u.occasion.id}:$today", u.ownerId, u.withId, u.occasion.kind, u.occasion.label, u.turning) }

    /** Topics whose day has come (a week back). */
    private fun topicsDue(shown: List<Contact>, today: LocalDate): List<Item> = shown.filter { !it.isMe }.flatMap { c ->
        c.topics.mapNotNull { t ->
            val day = TopicPlan.dayOf(t)
            val due = day != null && !t.done && !day.isAfter(today) && !day.isBefore(today.minusDays(TOPIC_DAYS))
            if (due && day != null) Topic("topic:${t.id}", c.id, t.id, t.text, day) else null
        }
    }

    /** The one memory of this day in earlier years: with a note first, then the most recent year. */
    private fun yearsAgo(contacts: List<Contact>, today: LocalDate, zone: ZoneId): YearsAgo? {
        val hits = contacts.filter { !it.isMe }.flatMap { c ->
            c.entries.filter { it.memberId == null }.mapNotNull { e ->
                val d = dayOf(e.at, zone)
                val years = today.year - d.year
                if (years >= 1 && d.monthValue == today.monthValue && d.dayOfMonth == today.dayOfMonth) {
                    YearsAgo("ago:${e.id}:$today", c.id, e.id, years, e.typeId, e.note.trim())
                } else {
                    null
                }
            }
        }
        return hits.sortedWith(compareBy({ it.note.isBlank() }, { it.years })).firstOrNull()
    }

    private fun dayOf(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
