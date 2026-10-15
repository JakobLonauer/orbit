package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.CalendarDay
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.Occasion
import io.github.thelony97.orbit.core.model.OccasionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class SendLaterTest {
    private val now = LocalDateTime.of(2026, 10, 15, 10, 0)

    @Test fun angles() {
        assertEquals(0, SendLater.minuteOf(0f))
        assertEquals(18 * 60, SendLater.minuteOf(270f))
        assertEquals(6 * 60 + 5, SendLater.minuteOf(91.2f))
        assertEquals(0, SendLater.minuteOf(359.9f))
        assertEquals(90f, SendLater.angleOf(6 * 60), 0.01f)
    }

    @Test fun pastTimeMeansTomorrow() {
        assertEquals(LocalDateTime.of(2026, 10, 16, 9, 0), SendLater.at(9 * 60, now, null).at)
        assertEquals(LocalDateTime.of(2026, 10, 15, 18, 30), SendLater.at(18 * 60 + 30, now, null).at)
    }

    @Test fun midnightMagnet() {
        val anna = Contact("anna", dates = listOf(Occasion("b", OccasionKind.BIRTHDAY, date = CalendarDay(18, 10, 1996))))
        val occ = SendLater.nextOccasion(anna, listOf(anna), now.toLocalDate())!!
        assertEquals(LocalDate.of(2026, 10, 18), occ.date)
        val p = SendLater.at(10, now, occ.date)
        assertTrue(p.magnet)
        assertEquals(LocalDateTime.of(2026, 10, 18, 0, 0), p.at)
        assertFalse(SendLater.at(12 * 60, now, occ.date).magnet)
    }

    @Test fun labels() {
        assertEquals("heute 18:30", SendLater.label(LocalDateTime.of(2026, 10, 15, 18, 30), now))
        assertEquals("morgen 07:00", SendLater.label(LocalDateTime.of(2026, 10, 16, 7, 0), now))
    }
}
