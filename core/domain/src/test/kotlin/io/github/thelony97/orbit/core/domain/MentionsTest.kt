package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class MentionsTest {
    private val anna = Contact("anna", firstName = "Anna", lastName = "Huber")
    private val tom = Contact("tom", firstName = "Tom", lastName = "Berger", entries = listOf(HistoryEntry("e", "tom", "meet", 5, note = "Mit @Anna im Kino")))
    private val tom2 = Contact("tom2", firstName = "Tom", lastName = "Maier")
    private val all = listOf(anna, tom, tom2)

    @Test fun resolves() {
        assertEquals(setOf("anna"), Mentions.find("Gruß an @anna!", all))
        assertEquals(emptySet<String>(), Mentions.find("@Tom kommt", all))
        assertEquals(setOf("tom2"), Mentions.find("@Tom_Maier kommt", all))
    }

    @Test fun backlinks() {
        val b = Mentions.backlinks("anna", all).single()
        assertEquals("tom", b.fromId)
        assertEquals("e", b.entryId)
    }

    @Test fun typingAndComplete() {
        assertEquals("An", Mentions.typing("Wir waren mit @An"))
        assertEquals("Wir waren mit @Anna ", Mentions.complete("Wir waren mit @An", anna, unique = true))
        assertEquals("@Tom_Maier ", Mentions.complete("@To", tom2, unique = false))
    }
}
