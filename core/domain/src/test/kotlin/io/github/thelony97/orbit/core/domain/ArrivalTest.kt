package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.CheckItem
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.GeoPoint
import io.github.thelony97.orbit.core.model.WifiNet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ArrivalTest {
    private val geo = GeoPoint(48.3, 14.28)

    @Test fun onlyPlacesWithContent() {
        val list = listOf(
            Contact("a", geo = geo, topics = listOf(CheckItem("t", "Rezept"))),
            Contact("b", geo = geo),
            Contact("c", geo = geo, wifi = listOf(WifiNet("w", "Gast"))),
            Contact("d", topics = listOf(CheckItem("t", "x"))),
            Contact("e", geo = geo, topics = listOf(CheckItem("t", "x")), archived = true),
        )
        assertEquals(listOf("a", "c"), Arrival.places(list).map { it.contactId })
    }

    @Test fun summary() {
        val c = Contact("a", topics = listOf(CheckItem("1", "x"), CheckItem("2", "y"), CheckItem("3", "z", done = true)), wifi = listOf(WifiNet("w", "Gast")))
        assertEquals("2 Themen · WLAN", Arrival.summary(c))
    }

    @Test fun visit() {
        val zone = ZoneId.of("Europe/Vienna")
        val start = LocalDate.of(2026, 10, 15).atTime(14, 5).atZone(zone).toInstant().toEpochMilli()
        assertEquals("Besuch 14:05–14:40", Arrival.visitNote(start, start + 35 * 60_000L, zone))
        assertNull(Arrival.visitNote(start, start + 60_000L, zone))
    }

    @Test fun byKnownWifi() {
        val huber = io.github.thelony97.orbit.core.model.Contact(
            "huber", firstName = "Dr. Huber", wifi = listOf(io.github.thelony97.orbit.core.model.WifiNet("w", "Ordination-Gast")),
        )
        val old = io.github.thelony97.orbit.core.model.Contact(
            "old", firstName = "Alt", archived = true, wifi = listOf(io.github.thelony97.orbit.core.model.WifiNet("w2", "Cafe")),
        )
        val cs = listOf(old, huber)
        org.junit.Assert.assertEquals("huber", Arrival.byWifi(cs, "\"Ordination-Gast\""))
        org.junit.Assert.assertEquals("huber", Arrival.byWifi(cs, "Ordination-Gast"))
        // Exact name only; archived places never; unknown or no network → none.
        org.junit.Assert.assertNull(Arrival.byWifi(cs, "ordination-gast"))
        org.junit.Assert.assertNull(Arrival.byWifi(cs, "Cafe"))
        org.junit.Assert.assertNull(Arrival.byWifi(cs, "<unknown ssid>"))
        org.junit.Assert.assertNull(Arrival.byWifi(cs, null))
        org.junit.Assert.assertTrue(Arrival.anyWifi(cs))
        org.junit.Assert.assertFalse(Arrival.anyWifi(listOf(old)))
    }
}
