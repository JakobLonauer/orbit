package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.CheckItem
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.HistoryEntry
import io.github.thelony97.orbit.core.model.Meeting
import io.github.thelony97.orbit.core.model.Relation
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MeetingBriefTest {
    private val zone = ZoneId.of("Europe/Vienna")
    private val today = LocalDate.of(2026, 10, 14)
    private fun at(h: Int, day: LocalDate = today) = day.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()

    private val anna = Contact(
        id = "anna", firstName = "Anna",
        topics = listOf(CheckItem("t1", "Wohnung?"), CheckItem("t2", "erledigt", done = true)),
        entries = listOf(
            HistoryEntry(id = "e1", contactId = "anna", typeId = "call", at = 10, note = "Neuer Job ab Jänner"),
            HistoryEntry(id = "e2", contactId = "anna", typeId = "call", at = 20, note = " "),
            HistoryEntry(id = "e3", contactId = "anna", typeId = "meet", at = 5, note = "alt"),
            HistoryEntry(id = "e4", contactId = "anna", typeId = io.github.thelony97.orbit.core.model.BuiltinTypes.GIFT, at = 30, note = "Buch"),
        ),
    )
    private val contacts = listOf(
        anna,
        Contact(id = "tom", firstName = "Tom"),
        Contact(id = "lea", firstName = "Lea"),
        Contact(id = "ex", firstName = "Ex", archived = true),
        Contact(id = "mama", firstName = "Mama"),
        Contact(id = "alt", firstName = "Alt", archived = true),
    )
    private val relations = listOf(
        Relation("r1", "tom", "anna", "partner"),
        Relation("r2", "anna", "lea", "parent"),
        Relation("r3", "anna", "ex", "spouse"),
        // Anna is Mama's child, not the other way round.
        Relation("r4", "mama", "anna", "parent"),
    )
    private val visible = { c: Contact -> !c.archived && c.deletedAt == null }

    @Test fun laterTodayWithTopicsNoteAndFamily() {
        val meetings = listOf(
            Meeting("m1", "anna", at(18), at(20), place = "Café"),
            Meeting("early", "anna", at(7), at(8)),
            Meeting("tomorrow", "anna", at(18, today.plusDays(1)), at(19, today.plusDays(1))),
            Meeting("done", "anna", at(19), at(20), resolved = true),
            Meeting("arch", "alt", at(18), at(19)),
        )
        val briefs = MeetingBrief.today(meetings, contacts, relations, today, now = at(9), zone = zone, visible = visible)
        assertEquals(listOf("m1"), briefs.map { it.meeting.id })
        val b = briefs.single()
        assertEquals(listOf("Wohnung?"), b.topics)
        assertEquals("Neuer Job ab Jänner", b.lastNote)
        assertEquals(listOf("tom"), b.partners)
        assertEquals(listOf("lea"), b.children)
    }
}
