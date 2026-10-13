package io.github.thelony97.orbit.ui.detail

import io.github.thelony97.orbit.data.DayOfYear
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekdayHintTest {
    private val today = LocalDate.of(2026, 10, 13) // a Tuesday

    @Test fun namesTheWeekdayWithinTwoMonths() {
        assertEquals(" – ein Sonntag", weekdayHint(12, DayOfYear(25, 10, 1996), today))
        assertEquals(" – ein Donnerstag", weekdayHint(2, DayOfYear(15, 10), today))
    }

    @Test fun silentWhenItDoesNotHelp() {
        assertEquals("", weekdayHint(1, DayOfYear(14, 10), today))
        assertEquals("", weekdayHint(61, DayOfYear(13, 12), today))
    }
}
