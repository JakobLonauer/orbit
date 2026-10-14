package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.Meeting
import io.github.thelony97.orbit.core.model.Relation
import java.time.LocalDate
import java.time.ZoneId

/**
 * Am Morgen eines geplanten Treffens (PLAN 3.13a): open topics, the last note and partner/children
 * from the relations – what to have in mind before meeting someone. Pure, for the morning run.
 */
object MeetingBrief {

    data class Brief(
        val meeting: Meeting,
        val contactId: String,
        val topics: List<String>,
        val lastNote: String?,
        val partners: List<String>,
        val children: List<String>,
    )

    private val PARTNER_KINDS = setOf("partner", "spouse")
    private val NOT_NOTES = setOf(io.github.thelony97.orbit.core.model.BuiltinTypes.CONGRATS, io.github.thelony97.orbit.core.model.BuiltinTypes.GIFT)

    /**
     * Meetings later today ([now] onwards) with a visible contact. Partners are either side of a
     * partner/spouse relation; children are the „to“ side of „<from> ist Elternteil von <to>“.
     */
    fun today(
        meetings: List<Meeting>,
        contacts: List<Contact>,
        relations: List<Relation>,
        today: LocalDate,
        now: Long,
        zone: ZoneId,
        visible: (Contact) -> Boolean,
    ): List<Brief> {
        val byId = contacts.associateBy { it.id }
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        fun ok(id: String) = byId[id]?.let { visible(it) && !it.isMe } == true
        return meetings.filter { !it.resolved && it.start in start until end && it.start >= now }
            .sortedBy { it.start }
            .mapNotNull { m ->
                val c = byId[m.contactId]?.takeIf { visible(it) } ?: return@mapNotNull null
                val partners = relations.filter { it.kind in PARTNER_KINDS && (it.fromId == c.id || it.toId == c.id) }
                    .map { if (it.fromId == c.id) it.toId else it.fromId }.filter(::ok).distinct()
                val children = relations.filter { it.kind == "parent" && it.fromId == c.id }.map { it.toId }.filter(::ok).distinct()
                Brief(
                    meeting = m,
                    contactId = c.id,
                    topics = c.topics.filterNot { it.done }.map { it.text },
                    // A gift's „Buch“ or „WhatsApp“ from a tapped button is no note about the person.
                    lastNote = c.entries.asSequence().filter { it.typeId !in NOT_NOTES && it.source != io.github.thelony97.orbit.core.model.EntrySource.APP_ACTION }.sortedByDescending { it.at }
                        .map { it.note.trim() }.firstOrNull { it.isNotEmpty() },
                    partners = partners,
                    children = children,
                )
            }
    }
}
