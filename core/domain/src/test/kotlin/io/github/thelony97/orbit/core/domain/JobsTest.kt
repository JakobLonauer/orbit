package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.BuiltinTypes
import io.github.thelony97.orbit.core.model.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class JobsTest {
    private val zone = ZoneId.of("Europe/Vienna")

    @Test fun amounts() {
        assertEquals(34_000L, Jobs.parseAmount("340"))
        assertEquals(34_050L, Jobs.parseAmount("340,5"))
        assertEquals(124_000L, Jobs.parseAmount("1.240,– €"))
        assertEquals(124_050L, Jobs.parseAmount("1.240,50"))
        assertEquals(8_990L, Jobs.parseAmount("€ 89.90"))
        assertNull(Jobs.parseAmount("gratis"))
    }

    @Test fun formats() {
        assertEquals("340 €", Jobs.format(34_000))
        assertEquals("1.240,50 €", Jobs.format(124_050))
        assertEquals("★★★★", Jobs.stars(4))
    }

    @Test fun lineOfLatestJob() {
        val at = { d: LocalDate -> d.atStartOfDay(zone).toInstant().toEpochMilli() }
        val entries = listOf(
            HistoryEntry("a", "o", BuiltinTypes.JOB, at(LocalDate.of(2025, 5, 12)), note = "Therme\nService und Dichtung", amountCents = 34_000, stars = 4),
            HistoryEntry("b", "o", BuiltinTypes.JOB, at(LocalDate.of(2023, 1, 2)), note = "Boiler", amountCents = 90_000),
            HistoryEntry("c", "o", BuiltinTypes.CALL, at(LocalDate.of(2026, 1, 2))),
        )
        val latest = Jobs.latest(entries)!!
        assertEquals("Therme 05/2025 · 340 € · ★★★★", Jobs.line(latest, zone))
    }
}
