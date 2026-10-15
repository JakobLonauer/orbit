package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Attachment
import io.github.thelony97.orbit.core.model.CalendarDay
import io.github.thelony97.orbit.core.model.CheckItem
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.ContactKind
import io.github.thelony97.orbit.core.model.HistoryEntry
import io.github.thelony97.orbit.core.model.LabeledValue
import io.github.thelony97.orbit.core.model.Meeting
import io.github.thelony97.orbit.core.model.Occasion
import io.github.thelony97.orbit.core.model.OccasionKind
import io.github.thelony97.orbit.core.model.OrbitDoc
import io.github.thelony97.orbit.core.model.Relation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TwinsTest {
    private val norm: (String) -> String? = { raw -> raw.filter { it.isDigit() }.let { if (it.startsWith("0")) "+43" + it.drop(1) else "+$it" } }

    @Test fun findsByNumberMailAndNameBirthday() {
        val list = listOf(
            Contact("a", firstName = "Anna", phones = listOf(LabeledValue("", "0664 1234567"))),
            Contact("b", firstName = "Anni", phones = listOf(LabeledValue("", "+43 664 1234567"))),
            Contact("c", firstName = "Tom", emails = listOf(LabeledValue("", "Tom@x.at"))),
            Contact("d", firstName = "T.", emails = listOf(LabeledValue("", "tom@x.at "))),
            Contact("e", firstName = "Lena", lastName = "Maier", dates = listOf(Occasion("1", OccasionKind.BIRTHDAY, date = CalendarDay(3, 4, 1990)))),
            Contact("f", firstName = "Lena", lastName = "Maier", dates = listOf(Occasion("2", OccasionKind.BIRTHDAY, date = CalendarDay(3, 4, 1990)))),
            Contact("g", firstName = "Lena", lastName = "Maier"),
            Contact("h", kind = ContactKind.ORG, company = "Praxis", phones = listOf(LabeledValue("", "0664 1234567"))),
        )
        val p = Twins.pairs(list, norm)
        assertEquals(setOf("a:b", "c:d", "e:f"), p.map { "${it.a}:${it.b}" }.toSet())
        assertTrue(Twins.pairs(list, norm, setOf(Twins.key("b", "a"))).none { it.a == "a" && it.b == "b" })
    }

    @Test fun mergeKeepsEverythingAndMovesReferences() {
        val a = Contact(
            "a", firstName = "Anna", lastName = "Huber", phones = listOf(LabeledValue("mobil", "0664 1")), topics = listOf(CheckItem("t1", "Wohnung")),
            entries = listOf(HistoryEntry("e1", "a", "call", 10)),
        )
        val b = Contact(
            "b", firstName = "Anni", phones = listOf(LabeledValue("", "0664 1"), LabeledValue("", "0732 2")), emails = listOf(LabeledValue("", "a@h.at")),
            topics = listOf(CheckItem("t2", "wohnung"), CheckItem("t3", "Urlaub")),
            entries = listOf(HistoryEntry("e2", "b", "meet", 20), HistoryEntry("e3", "b", "call", 30)),
            gallery = listOf(Attachment("p1", "b", "f.jpg", addedAt = 1)),
        )
        val other = Contact("x", firstName = "Tom")
        val doc = OrbitDoc(
            contacts = listOf(a, b, other),
            relations = listOf(Relation("r1", "b", "x", "friend"), Relation("r2", "a", "x", "friend")),
            meetings = listOf(Meeting("m", "b", 1, 2)),
        )
        assertEquals(listOf("firstName"), Twins.conflicts(a, b).map { it.field })
        assertEquals("1 + 2 → 3 Einträge", Twins.countLine(a, b))
        val out = Twins.apply(doc, "a", "b", setOf("firstName"), now = 99)
        val m = out.contacts.first { it.id == "a" }
        assertEquals("Anni", m.firstName)
        assertEquals("Huber", m.lastName)
        assertEquals(2, m.phones.size)
        assertEquals(1, m.emails.size)
        assertEquals(listOf("Wohnung", "Urlaub"), m.topics.map { it.text })
        assertEquals(listOf("e3", "e2", "e1"), m.entries.map { it.id })
        assertTrue(m.entries.all { it.contactId == "a" })
        assertEquals("a", m.gallery.single().contactId)
        val gone = out.contacts.first { it.id == "b" }
        assertNotNull(gone.deletedAt)
        assertTrue(gone.entries.isEmpty())
        assertEquals(1, out.relations.size)
        assertEquals("a", out.meetings.single().contactId)
    }
}
