package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.CalendarDay
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.ContactKind
import io.github.thelony97.orbit.core.model.HistoryEntry
import io.github.thelony97.orbit.core.model.LabeledValue
import io.github.thelony97.orbit.core.model.Occasion
import io.github.thelony97.orbit.core.model.OccasionKind
import io.github.thelony97.orbit.core.model.OrbitDoc
import io.github.thelony97.orbit.core.model.OrgLink
import io.github.thelony97.orbit.core.model.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class IndexTest {
    private val today = LocalDate.of(2026, 10, 13)

    @Test fun foldMakesUmlautsAndEszettMeet() {
        assertEquals("muller strasse janner", Fold.fold("Müller Straße Jänner"))
    }

    @Test fun lastContactPerTypeIgnoresMemberTalks() {
        val h = sequenceOf(
            HistoryEntry("1", "a", "call", at = 10),
            HistoryEntry("2", "a", "call", at = 30),
            HistoryEntry("3", "a", "meet", at = 20),
            HistoryEntry("4", "org", "call", at = 99, memberId = "m1"),
        )
        val idx = LastContactIndex.build(h)
        assertEquals(30L, idx["a"]!!.byType["call"])
        assertEquals(30L, idx["a"]!!.any)
        assertNull(idx["org"])
    }

    @Test fun searchTextContainsAssignmentsAndDigits() {
        val c = Contact("p", firstName = "Peter", phones = listOf(LabeledValue("", "+43 660 12")))
        val doc = OrbitDoc(
            contacts = listOf(c, Contact("o", kind = ContactKind.ORG, firstName = "Arzt Müller")),
            orgLinks = listOf(OrgLink("l", "p", "o", "gp")),
        )
        val t = SearchText.of(c, SearchText.orgNamesByPerson(doc)["p"].orEmpty())
        assertTrue("arzt muller" in t)
        assertTrue("4366012" in t)
        assertTrue("peter" in t)
    }

    @Test fun visibility() {
        val org = Contact("o", kind = ContactKind.ORG)
        assertFalse(Visibility.inLists(org, showOrgs = false))
        assertTrue(Visibility.inLists(org, showOrgs = true))
        val arch = Contact("a", archived = true)
        assertFalse(Visibility.inLists(arch, true))
        assertTrue(Visibility.inArchive(arch))
        assertFalse(Visibility.inArchive(arch.copy(deletedAt = 1)))
        assertTrue(Visibility.inTrash(arch.copy(deletedAt = 1)))
    }

    @Test fun upcomingSortsSkipsPastOnceAndCountsYears() {
        val anna = Contact("anna", dates = listOf(
            Occasion("b", OccasionKind.BIRTHDAY, date = CalendarDay(20, 10, 1995)),
            Occasion("w", OccasionKind.WEDDING, date = CalendarDay(13, 10, 2021), sharedWith = "tom"),
            Occasion("x", OccasionKind.CUSTOM, label = "Vertrag", date = CalendarDay(1, 1, 2026), repeat = Repeat.ONCE),
            Occasion("y", OccasionKind.CUSTOM, label = "Umzug", date = CalendarDay(1, 12, 2026), repeat = Repeat.ONCE),
        ))
        val tom = Contact("tom")
        val alt = Contact("alt", archived = true, dates = listOf(Occasion("z", OccasionKind.BIRTHDAY, date = CalendarDay(14, 10))))
        val list = UpcomingIndex.build(listOf(anna, tom, alt), today) { Visibility.inLists(it, false) }
        assertEquals(listOf("w", "b", "y"), list.map { it.occasion.id })
        assertEquals(0L, list[0].daysUntil)
        assertEquals(5, list[0].turning)
        assertEquals("tom", list[0].withId)
        assertEquals(31, list[1].turning)
        assertNull(list[2].turning)
        assertEquals(49L, list[2].daysUntil)
    }
}
