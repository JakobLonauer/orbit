package io.github.thelony97.orbit.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TimeZoomTest {
    private val today = LocalDate.of(2026, 10, 15)

    @Test fun labels() {
        assertEquals("KW 42", TimeZoom.label(today, TimeZoom.Level.WEEK, today))
        assertEquals("Oktober", TimeZoom.label(today, TimeZoom.Level.MONTH, today))
        assertEquals("Mai 2025", TimeZoom.label(LocalDate.of(2025, 5, 3), TimeZoom.Level.MONTH, today))
        assertEquals("2026-W42", TimeZoom.key(today, TimeZoom.Level.WEEK))
    }

    @Test fun summary() = assertEquals("5 Anrufe · 2 Treffen · 1 Nachricht", TimeZoom.summary(List(5) { "Anruf" } + List(2) { "Treffen" } + "Nachricht"))

    @Test fun levels() {
        assertEquals(TimeZoom.Level.YEAR, TimeZoom.zoomOut(TimeZoom.Level.YEAR))
        assertEquals(TimeZoom.Level.DAY, TimeZoom.zoomIn(TimeZoom.Level.WEEK))
    }
}
