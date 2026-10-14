package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.domain.OpeningHours.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

class OpeningHoursTest {
    // Wednesday 14 October 2026.
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, 12 + day, h, m)
    private val arzt = OpeningHours.parse("Mo-Fr 08:00-12:00,13:00-17:00; Sa 09:00-12:00; Su off; PH off")!!
    private val short = { d: DayOfWeek -> listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")[d.value - 1] }

    @Test fun openWithClosingTimeAndSoon() {
        assertEquals(Status.Open(LocalTime.of(12, 0), false), OpeningHours.status(arzt, at(2, 9)))
        assertEquals(Status.Open(LocalTime.of(17, 0), true), OpeningHours.status(arzt, at(2, 16, 40)))
        assertEquals("Schließt bald · 17:00", OpeningHours.line(OpeningHours.status(arzt, at(2, 16, 40)), short))
    }

    @Test fun closedSaysWhenItOpens() {
        assertEquals("Geschlossen · öffnet 13:00", OpeningHours.line(OpeningHours.status(arzt, at(2, 12, 30)), short))
        assertEquals("Geschlossen · öffnet Do 08:00", OpeningHours.line(OpeningHours.status(arzt, at(2, 18)), short))
        // Saturday afternoon: Sunday off, Monday again.
        assertEquals(Status.Closed(LocalTime.of(8, 0), DayOfWeek.MONDAY), OpeningHours.status(arzt, at(5, 13)))
    }

    @Test fun overnightAndRoundTheClock() {
        val bar = OpeningHours.parse("Fr,Sa 20:00-02:00")!!
        assertEquals(Status.Open(LocalTime.of(2, 0), false), OpeningHours.status(bar, at(5, 0, 30)))
        assertEquals(Status.Open(LocalTime.of(2, 0), false), OpeningHours.status(bar, at(4, 23)))
        assertEquals(Status.Closed(LocalTime.of(20, 0), DayOfWeek.FRIDAY), OpeningHours.status(bar, at(2, 10)))
        assertEquals("Rund um die Uhr geöffnet", OpeningHours.line(OpeningHours.status(OpeningHours.parse("24/7")!!, at(2, 3)), short))
        assertEquals(Status.Open(null, false), OpeningHours.status(OpeningHours.parse("Mo-Su 00:00-24:00")!!, at(0, 12)))
    }

    @Test fun germanDaysWrapAndLaterRulesWin() {
        val w = OpeningHours.parse("Mo-So 10:00-18:00; Mi off; Fr-Mo 09:00-12:00")!!
        assertEquals(emptyList<OpeningHours.Span>(), w.days[2])
        assertEquals(listOf(OpeningHours.Span(540, 720)), w.days[0])
        assertEquals(listOf(OpeningHours.Span(600, 1080)), w.days[1])
    }

    @Test fun unknownTextIsNoSchedule() {
        listOf("", "nach Vereinbarung", "Mo-Fr", "Mo 8-12", "Xy 08:00-12:00", "Mo 25:00-26:00", "Mo 08:00-12:00-13:00").forEach {
            assertNull(it, OpeningHours.parse(it))
        }
        assertEquals("Geschlossen", OpeningHours.line(OpeningHours.status(OpeningHours.parse("Mo off")!!, at(0, 9)), short))
    }
}
