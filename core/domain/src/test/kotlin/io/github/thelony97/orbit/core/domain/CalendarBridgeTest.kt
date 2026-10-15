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

    @Test fun noWordInside() = assertNull(CalendarBridge.match("Annahme Paket", emptyList(), contacts))
}
