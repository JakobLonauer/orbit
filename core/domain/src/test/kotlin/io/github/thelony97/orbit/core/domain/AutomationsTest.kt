package io.github.thelony97.orbit.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AutomationsTest {
    private val zone = ZoneId.of("Europe/Vienna")

    @Test fun text() {
        val end = LocalDateTime.of(2026, 10, 15, 15, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals("Bin gerade im Termin – ich rufe dich ab 15:30 zurück.", Automations.busyText(end, zone, formal = false))
        assertTrue(Automations.busyText(end, zone, formal = true).contains("Sie ab 15:30"))
    }

    @Test fun groups() {
        assertTrue(Automations.busyApplies(listOf("g"), emptyList()))
        assertTrue(Automations.busyApplies(listOf("g", "h"), listOf("h")))
        assertFalse(Automations.busyApplies(listOf("g"), listOf("h")))
    }
}
