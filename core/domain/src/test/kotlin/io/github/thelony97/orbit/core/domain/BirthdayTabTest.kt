package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.CalendarDay
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.ContactKind
import io.github.thelony97.orbit.core.model.Occasion
import io.github.thelony97.orbit.core.model.OccasionKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BirthdayTabTest {
    // Wednesday; the week runs to Sunday 18 October.
    private val today = LocalDate.of(2026, 10, 14)

    private fun p(id: String, day: Int, month: Int, year: Int? = null, archived: Boolean = false) = Contact(
        id = id, firstName = id, archived = archived,
        dates = listOf(Occasion("b-$id", OccasionKind.BIRTHDAY, date = CalendarDay(day, month, year))),
    )

    private val contacts = listOf(
        p("anna", 14, 10, 1996),
        p("bea", 12, 10),
        p("cara", 7, 10),
        p("dora", 6, 10),
        p("emil", 18, 10),
        p("finn", 19, 10),
        p("gina", 3, 11),
        p("hans", 29, 2, 1980),
        p("alt", 15, 10, archived = true),
        Contact(id = "arzt", kind = ContactKind.ORG, company = "Arzt", dates = listOf(Occasion("o", OccasionKind.BIRTHDAY, date = CalendarDay(15, 10)))),
        Contact(id = "ohne", firstName = "Ohne"),
        Contact(id = "me", isMe = true, firstName = "Jakob"),
    )

    private val result = BirthdayTab.build(contacts, today, visible = { !it.archived })

    private fun titles() = result.groups.map { g -> g.title to g.rows.map { it.contactId } }

    @Test fun sectionsInOrder() {
        assertEquals(
            listOf(
                "Gerade vorbei" to listOf("cara", "bea"),
                "Heute" to listOf("anna"),
                "Diese Woche" to listOf("emil"),
                "Diesen Monat" to listOf("finn"),
                "November" to listOf("gina"),
                "Februar 2027" to listOf("hans"),
                "Oktober 2027" to listOf("dora"),
            ),
            titles(),
        )
    }

    @Test fun ageDaysAndLeapDay() {
        val all = result.groups.flatMap { it.rows }.associateBy { it.contactId }
        assertEquals(30, all.getValue("anna").turning)
        assertEquals(0L, all.getValue("anna").daysUntil)
        assertEquals(-2L, all.getValue("bea").daysUntil)
        assertEquals(null, all.getValue("bea").turning)
        // 29 February shows on the 28th in a non-leap year (PLAN 3.5).
        assertEquals(LocalDate.of(2027, 2, 28), all.getValue("hans").date)
        assertEquals(47, all.getValue("hans").turning)
    }

    @Test fun neverArchiveOrOrganisationsAndCountsPeopleWithoutBirthday() {
        val ids = result.groups.flatMap { g -> g.rows.map { it.contactId } }
        assertEquals(false, "alt" in ids || "arzt" in ids)
        // „Ohne“ counts; you yourself and organisations do not.
        assertEquals(1, result.withoutBirthday)
    }

    @Test fun typedBirthdays() {
        val cd = { d: Int, m: Int, y: Int? -> CalendarDay(d, m, y) }
        assertEquals(cd(14, 10, null), BirthdayTab.parseDay("14.10.", today))
        assertEquals(cd(14, 10, null), BirthdayTab.parseDay("14.10", today))
        assertEquals(cd(14, 10, 1996), BirthdayTab.parseDay("14.10.1996", today))
        assertEquals(cd(14, 10, 1996), BirthdayTab.parseDay("14.10.96", today))
        assertEquals(cd(1, 2, 2005), BirthdayTab.parseDay("1.2.05", today))
        assertEquals(cd(14, 10, null), BirthdayTab.parseDay("1410", today))
        assertEquals(cd(29, 2, null), BirthdayTab.parseDay("29.02.", today))
        assertEquals(cd(29, 2, 2000), BirthdayTab.parseDay("29.2.2000", today))
        assertEquals(null, BirthdayTab.parseDay("29.2.2001", today))
        assertEquals(null, BirthdayTab.parseDay("31.4.", today))
        assertEquals(null, BirthdayTab.parseDay("12.13.", today))
        assertEquals(null, BirthdayTab.parseDay("1.1.2030", today))
        assertEquals(null, BirthdayTab.parseDay("abc", today))
        assertEquals(null, BirthdayTab.parseDay("", today))
    }

    @Test fun peopleWithoutBirthdayByName() {
        assertEquals(listOf("ohne"), result.without)
    }

    @Test fun onSundayThereIsNoRestOfTheWeek() {
        val sunday = LocalDate.of(2026, 10, 18)
        val r = BirthdayTab.build(listOf(p("a", 19, 10), p("b", 18, 10)), sunday, { true })
        assertEquals(listOf("Heute", "Diesen Monat"), r.groups.map { it.title })
    }

    @Test fun weekReachingIntoNextMonth() {
        // Thursday 29 October: the week ends on Sunday 1 November.
        val r = BirthdayTab.build(listOf(p("a", 1, 11), p("b", 2, 11)), LocalDate.of(2026, 10, 29), { true })
        assertEquals(listOf("Diese Woche" to listOf("a"), "November" to listOf("b")), r.groups.map { g -> g.title to g.rows.map { it.contactId } })
    }

    @Test fun sameDaySortsByName() {
        val r = BirthdayTab.build(listOf(p("zoe", 20, 10), p("adam", 20, 10)), today, { true }, name = { it.firstName })
        assertEquals(listOf("adam", "zoe"), r.groups.single().rows.map { it.contactId })
    }
}
