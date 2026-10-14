package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.CalendarDay
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.Occasion
import io.github.thelony97.orbit.core.model.OccasionKind
import io.github.thelony97.orbit.core.model.Repeat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class OccasionListTest {
    private val today = LocalDate.of(2026, 10, 14) // Wednesday
    private fun o(id: String, kind: OccasionKind, d: Int, m: Int, y: Int? = null, pinned: Boolean = false, label: String = "", repeat: Repeat = Repeat.YEARLY, with: String? = null) =
        Occasion(id, kind, label = label, date = CalendarDay(d, m, y), pinned = pinned, repeat = repeat, sharedWith = with)

    private val anna = Contact("anna", firstName = "Anna", groupIds = listOf("fr"), dates = listOf(
        o("a-bd", OccasionKind.BIRTHDAY, 14, 10, 1996),
        o("a-wed", OccasionKind.WEDDING, 16, 10, 2021, pinned = true, with = "tom"),
    ))
    private val tom = Contact("tom", firstName = "Tom", dates = listOf(o("t-bd", OccasionKind.BIRTHDAY, 2, 1, 1980)))
    private val arzt = Contact("arzt", kind = io.github.thelony97.orbit.core.model.ContactKind.ORG, company = "Arzt",
        dates = listOf(o("v", OccasionKind.CUSTOM, 20, 11, 2026, label = "Vertrag endet", repeat = Repeat.ONCE)))
    private val all = listOf(anna, tom, arzt)
    private val names = mapOf("anna" to "Anna", "tom" to "Tom", "arzt" to "Arzt")
    private fun ids(groups: List<OccasionList.Group>) = groups.map { g -> g.title to g.rows.map { it.occasion.id } }

    @Test fun nextInSectionsSharedOnce() {
        val g = OccasionList.build(all, today, { true }, name = { names.getValue(it) })
        assertEquals(
            listOf("Heute" to listOf("a-bd"), "Diese Woche" to listOf("a-wed"), "Später" to listOf("v", "t-bd")),
            ids(g),
        )
    }

    @Test fun filters() {
        val pinned = OccasionList.build(all, today, { true }, OccasionList.Filter(pinnedOnly = true))
        assertEquals(listOf("a-wed"), pinned.flatMap { it.rows }.map { it.occasion.id })
        val week = OccasionList.build(all, today, { true }, OccasionList.Filter(withinDays = 7))
        assertEquals(listOf("a-bd", "a-wed"), week.flatMap { it.rows }.map { it.occasion.id })
        // Anna turns 30, the wedding is the 5th, Tom turns 47: round ones are 30 and 5.
        val round = OccasionList.build(all, today, { true }, OccasionList.Filter(onlyRound = true))
        assertEquals(listOf("a-bd", "a-wed"), round.flatMap { it.rows }.map { it.occasion.id })
        val group = OccasionList.build(all, today, { true }, OccasionList.Filter(groupId = "fr"))
        assertEquals(listOf("a-bd", "a-wed"), group.flatMap { it.rows }.map { it.occasion.id })
        val kinds = OccasionList.build(all, today, { true }, OccasionList.Filter(kinds = setOf(OccasionKind.CUSTOM)))
        assertEquals(listOf("v"), kinds.flatMap { it.rows }.map { it.occasion.id })
    }

    @Test fun calendarNameAndYearsAndReversed() {
        val cal = OccasionList.build(all, today, { true }, sort = OccasionList.Sort.CALENDAR, name = { names.getValue(it) })
        assertEquals(listOf("Jänner" to listOf("t-bd"), "Oktober" to listOf("a-bd", "a-wed"), "November" to listOf("v")), ids(cal))
        val byName = OccasionList.build(all, today, { true }, sort = OccasionList.Sort.NAME, name = { names.getValue(it) })
        assertEquals(listOf("a-bd", "a-wed", "v", "t-bd"), byName.single().rows.map { it.occasion.id })
        val years = OccasionList.build(all, today, { true }, sort = OccasionList.Sort.YEARS)
        assertEquals(listOf("t-bd", "a-bd", "a-wed", "v"), years.single().rows.map { it.occasion.id })
        val rev = OccasionList.build(all, today, { true }, sort = OccasionList.Sort.CALENDAR, reversed = true, name = { names.getValue(it) })
        assertEquals(listOf("November" to listOf("v"), "Oktober" to listOf("a-wed", "a-bd"), "Jänner" to listOf("t-bd")), ids(rev))
    }

    @Test fun lines() {
        val g = OccasionList.build(all, today, { true }).flatMap { it.rows }.associateBy { it.occasion.id }
        assertEquals("Geburtstag · wird 30", OccasionList.line(g.getValue("a-bd")))
        assertEquals("5. Hochzeitstag", OccasionList.line(g.getValue("a-wed")))
        assertEquals("Vertrag endet", OccasionList.line(g.getValue("v")))
    }
}
