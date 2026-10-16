package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.LabeledValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarBridgeTest {
    private val contacts = listOf(
        Contact("anna", firstName = "Anna", lastName = "Huber", emails = listOf(LabeledValue("", "anna@huber.at"))),
        Contact("tom", firstName = "Tom", lastName = "Berger"),
        Contact("tom2", firstName = "Tom", lastName = "Maier", nickname = "Tomi"),
    )

    @Test fun byAttendee() = assertEquals("anna", CalendarBridge.match("Projekt", listOf("Anna@Huber.at"), contacts))

    @Test fun byFirstNameWhenUnique() = assertEquals("anna", CalendarBridge.match("Kaffee mit Anna", emptyList(), contacts))

    @Test fun ambiguousFirstNameNeedsFullName() {
        assertNull(CalendarBridge.match("Bier mit Tom", emptyList(), contacts))
        assertEquals("tom", CalendarBridge.match("Bier mit Tom Berger", emptyList(), contacts))
        assertEquals("tom2", CalendarBridge.match("Laufen – Tomi", emptyList(), contacts))
    }

    @Test fun photoWindow() {
        val w = CalendarBridge.photoWindow(1_000_000L, 4_600_000L)
        assertEquals(1_000_000L - 15 * 60_000L, w.first)
        assertEquals(4_600_000L + 30 * 60_000L, w.last)
        // A broken appointment ending before it starts still gets a window after its start.
        assertEquals(1_000_000L + 30 * 60_000L, CalendarBridge.photoWindow(1_000_000L, 0L).last)
    }

    @Test fun noWordInside() = assertNull(CalendarBridge.match("Annahme Paket", emptyList(), contacts))
}
