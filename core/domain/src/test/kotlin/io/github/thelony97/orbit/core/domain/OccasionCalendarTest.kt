package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.CalendarDay
import io.github.thelony97.orbit.core.model.Occasion
import io.github.thelony97.orbit.core.model.OccasionKind
import io.github.thelony97.orbit.core.model.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class OccasionCalendarTest {
    private val today = LocalDate.of(2026, 10, 14)
    private val names = mapOf("anna" to "Anna", "tom" to "Tom", "hans" to "Hans")
    private val nameOf = { id: String -> names[id].orEmpty() }

    @Test fun birthdayRepeatsYearlyFromTheNextOne() {
        val e = OccasionCalendar.event(Occasion("b", OccasionKind.BIRTHDAY, date = CalendarDay(3, 2, 1996)), "anna", today, nameOf)!!
        assertEquals("Annas Geburtstag", e.title)
        assertEquals(LocalDate.of(2027, 2, 3), e.day)
        assertEquals("FREQ=YEARLY", e.rrule)
    }

    @Test fun sharedWeddingNamesBothAndKeepsTheNote() {
        val o = Occasion("w", OccasionKind.WEDDING, date = CalendarDay(16, 10, 2021), sharedWith = "tom", note = " Tisch reservieren ")
        val e = OccasionCalendar.event(o, "anna", today, nameOf)!!
        assertEquals("Hochzeitstag Anna & Tom", e.title)
        assertEquals(LocalDate.of(2026, 10, 16), e.day)
        assertEquals("Tisch reservieren", e.description)
    }

    @Test fun leapDayAndOneOffs() {
        val leap = OccasionCalendar.event(Occasion("l", OccasionKind.BIRTHDAY, date = CalendarDay(29, 2, 1980)), "hans", today, nameOf)!!
        assertEquals("FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=-1", leap.rrule)
        assertEquals(LocalDate.of(2027, 2, 28), leap.day)
        val once = OccasionCalendar.event(Occasion("o", OccasionKind.CUSTOM, label = "Vertrag endet", date = CalendarDay(1, 3, 2027), repeat = Repeat.ONCE), "anna", today, nameOf)!!
        assertNull(once.rrule)
        assertEquals(LocalDate.of(2027, 3, 1), once.day)
        assertEquals("Vertrag endet Anna", once.title)
        assertNull(OccasionCalendar.event(Occasion("n", OccasionKind.CUSTOM, label = "X", date = CalendarDay(1, 3), repeat = Repeat.ONCE), "anna", today, nameOf))
    }
}
