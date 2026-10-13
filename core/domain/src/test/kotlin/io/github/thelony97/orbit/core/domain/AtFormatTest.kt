package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.CalendarDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AtFormatTest {
    private val d = LocalDate.of(2026, 1, 13)

    @Test fun austrianMonths() {
        assertEquals("13. Jänner 2026", AtFormat.date(d))
        assertEquals("13. Jän.", AtFormat.dateShort(d))
        assertEquals("Di., 13. Jän.", AtFormat.weekdayDate(d))
        assertEquals("Dienstag", AtFormat.weekday(d))
        assertEquals("29. Februar 1964", AtFormat.day(CalendarDay(29, 2, 1964)))
        assertEquals("3. Jänner", AtFormat.day(CalendarDay(3, 1)))
    }

    @Test fun relative() {
        assertEquals("heute", AtFormat.until(0))
        assertEquals("morgen", AtFormat.until(1))
        assertEquals("übermorgen", AtFormat.until(2))
        assertEquals("in 5 Tagen", AtFormat.until(5))
        assertEquals("in 3 Wochen", AtFormat.until(21))
        assertEquals("gestern", AtFormat.until(-1))
        assertEquals("vor 2 Jahren", AtFormat.ago(800))
        assertEquals("vor 6 Wochen", AtFormat.ago(45))
        assertEquals("vor 2 Monaten", AtFormat.ago(60))
        assertEquals("vor 2 Jahren", AtFormat.ago(700))
        assertEquals("vor 1 Jahr", AtFormat.ago(400))
        assertEquals("vorgestern", AtFormat.ago(2))
    }

    @Test fun phrases() {
        assertEquals("wird 31", AtFormat.turning(31))
        assertEquals("5. Hochzeitstag", AtFormat.nth(5, "Hochzeitstag"))
        assertTrue(AtFormat.isRound(30))
        assertFalse(AtFormat.isRound(31))
        assertFalse(AtFormat.isRound(0))
    }
}
