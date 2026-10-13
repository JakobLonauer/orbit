package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.CalendarDay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DateMathTest {

    @Test
    fun leapDayFallsBackInNonLeapYear() {
        assertEquals(LocalDate.of(2027, 2, 28), DateMath.nextOccurrence(CalendarDay(29, 2), LocalDate.of(2027, 1, 10)))
        assertEquals(LocalDate.of(2028, 2, 29), DateMath.nextOccurrence(CalendarDay(29, 2), LocalDate.of(2028, 1, 10)))
    }

    @Test
    fun todayCountsAsNextOccurrence() {
        val today = LocalDate.of(2026, 10, 13)
        assertEquals(0L, DateMath.daysUntil(CalendarDay(13, 10), today))
    }

    @Test
    fun yearWrap() {
        val today = LocalDate.of(2026, 12, 31)
        assertEquals(LocalDate.of(2027, 1, 1), DateMath.nextOccurrence(CalendarDay(1, 1), today))
        assertEquals(1L, DateMath.daysUntil(CalendarDay(1, 1), today))
    }

    @Test
    fun turningUsesNextOccurrence() {
        val today = LocalDate.of(2026, 10, 13)
        assertEquals(30, DateMath.turning(CalendarDay(12, 10, 1997), today))
        assertEquals(29, DateMath.turning(CalendarDay(14, 10, 1997), today))
        assertEquals(null, DateMath.turning(CalendarDay(14, 10), today))
    }
}
