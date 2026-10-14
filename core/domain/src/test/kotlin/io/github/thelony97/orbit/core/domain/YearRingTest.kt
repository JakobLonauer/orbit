package io.github.thelony97.orbit.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class YearRingTest {
    @Test fun januaryAtTheTopClockwise() {
        assertEquals(0f, YearRing.angleOf(LocalDate.of(2026, 1, 1)), 0.001f)
        // 1 July 2026 is day 182 of 365.
        assertEquals(181 * 360f / 365, YearRing.angleOf(LocalDate.of(2026, 7, 1)), 0.001f)
        assertEquals(359.01f, YearRing.angleOf(LocalDate.of(2026, 12, 31)), 0.01f)
    }

    @Test fun tapFindsTheMonth() {
        val r = 100f
        // Straight up: January; right: April (90° ≈ 1 April); down: July; left: October.
        assertEquals(1, YearRing.monthAt(0f, -r, 80f, 120f, 2026))
        assertEquals(3, YearRing.monthAt(r * 0.999f, -r * 0.04f, 80f, 120f, 2026))
        assertEquals(4, YearRing.monthAt(r, 0f, 80f, 120f, 2026))
        assertEquals(7, YearRing.monthAt(0f, r, 80f, 120f, 2026))
        assertEquals(10, YearRing.monthAt(-r, 0f, 80f, 120f, 2026))
        assertEquals(12, YearRing.monthAt(-r * 0.2f, -r, 80f, 120f, 2026))
    }

    @Test fun hubAndOutsideAreNoMonth() {
        assertNull(YearRing.monthAt(0f, -10f, 80f, 120f, 2026))
        assertNull(YearRing.monthAt(0f, -200f, 80f, 120f, 2026))
    }
}
