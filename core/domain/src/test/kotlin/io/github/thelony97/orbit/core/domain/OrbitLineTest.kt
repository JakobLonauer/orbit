package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.BuiltinTypes
import io.github.thelony97.orbit.core.model.CalendarDay
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.ContactKind
import io.github.thelony97.orbit.core.model.EntryType
import io.github.thelony97.orbit.core.model.Group
import io.github.thelony97.orbit.core.model.LabeledValue
import io.github.thelony97.orbit.core.model.Occasion
import io.github.thelony97.orbit.core.model.OccasionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class OrbitLineTest {
    // Thursday.
    private val now = LocalDateTime.of(2026, 10, 15, 10, 0)
    private val contacts = listOf(
        Contact("anna", firstName = "Anna", lastName = "Huber", groupIds = listOf("uni"), addresses = listOf(LabeledValue("", "Hauptstraße 1, Linz"))),
        Contact("tom", firstName = "Tom", lastName = "Berger", dates = listOf(Occasion("b", OccasionKind.BIRTHDAY, date = CalendarDay(1, 2, 1990)))),
        Contact("tom2", firstName = "Tom", lastName = "Maier"),
        Contact("bank", kind = ContactKind.ORG, company = "Raiffeisen"),
    )
    private val types = BuiltinTypes.all + EntryType("bier", "Bier", "drink")
    private fun p(s: String) = OrbitLine.parse(s, contacts, types, now)

    private data class Case(val text: String, val person: String?, val type: String?, val at: LocalDateTime?, val capture: Boolean)

    @Test fun table() {
        listOf(
            Case("Anna Kaffee morgen 15 Uhr", "anna", BuiltinTypes.MEET, LocalDateTime.of(2026, 10, 16, 15, 0), true),
            Case("Anna gestern angerufen", "anna", BuiltinTypes.CALL, LocalDateTime.of(2026, 10, 14, 12, 0), true),
            Case("Tom Berger Fr 9:30", "tom", null, LocalDateTime.of(2026, 10, 16, 9, 30), true),
            Case("Anna in 2 Wochen", "anna", null, LocalDateTime.of(2026, 10, 29, 12, 0), true),
            Case("Anna Bier", "anna", "bier", null, true),
            Case("Raiffeisen Anruf um 9", "bank", BuiltinTypes.CALL, LocalDateTime.of(2026, 10, 15, 9, 0), true),
            Case("Anna Donnerstag 8:00", "anna", null, LocalDateTime.of(2026, 10, 22, 8, 0), true),
            Case("Anna 24.12.", "anna", null, LocalDateTime.of(2026, 12, 24, 12, 0), true),
            Case("Anna", "anna", null, null, false),
            Case("Tom Kaffee", null, null, null, false),
            Case("Kaffee morgen", null, null, null, false),
            Case("Anna so wichtig", "anna", null, null, false),
        ).forEach { c ->
            val l = p(c.text)
            assertEquals(c.text, c.person, l.personId)
            if (c.person != null) {
                assertEquals(c.text, c.type, l.typeId)
                assertEquals(c.text, c.at, l.at)
            }
            assertEquals(c.text, c.capture, l.capture)
        }
    }

    @Test fun noteKeepsKindWordAndPlus() {
        val l = p("Anna Kaffee morgen +Wohnung in Wien")
        assertEquals("Kaffee · Wohnung in Wien", l.note)
        assertTrue(OrbitLine.Kind.NOTE in l.chips.map { it.kind })
    }

    @Test fun withoutPersonItStaysSearch() {
        val l = p("Kaffee morgen")
        assertEquals("Kaffee morgen", l.rest)
        assertTrue(l.chips.isEmpty())
    }

    @Test fun operators() {
        val groups = listOf(Group("uni", "Uni", 0))
        val l = p("#uni ist:person ort:linz")
        assertEquals("", l.rest)
        assertTrue(OrbitLine.accepts(contacts[0], l.filters, groups))
        assertFalse(OrbitLine.accepts(contacts[1], l.filters, groups))
        assertTrue(OrbitLine.accepts(contacts[1], p("hat:geburtstag").filters, groups))
        assertFalse(OrbitLine.accepts(contacts[3], p("ist:person").filters, groups))
        assertNull(p("hat:geburtstag Tom").personId)
    }
}
