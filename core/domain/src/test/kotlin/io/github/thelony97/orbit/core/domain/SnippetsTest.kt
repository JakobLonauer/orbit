package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.ContactKind
import io.github.thelony97.orbit.core.model.CustomField
import io.github.thelony97.orbit.core.model.Greeting
import io.github.thelony97.orbit.core.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SnippetsTest {
    private val today = LocalDate.of(2026, 10, 15)

    @Test fun fillsPerContact() {
        val hansi = Contact("h", firstName = "Johann", nickname = "Hansi")
        assertEquals("Alles Gute zum Geburtstag, Hansi!", Snippets.fill("Alles Gute zum Geburtstag, {Rufname}!", hansi, today))
        assertEquals("Hallo Hansi, heute 15.10.2026", Snippets.fill("{Anrede}, heute {Datum}", hansi, today))
    }

    @Test fun missingValueDropsCleanly() {
        val anna = Contact("a", firstName = "Anna")
        assertEquals("Meine Kundennummer:.", Snippets.fill("Meine Kundennummer: {Kundennummer}.", anna, today))
    }

    @Test fun anrede() {
        val bank = Contact("b", kind = ContactKind.ORG, company = "Bank", fields = listOf(CustomField("f", "Kundennummer", "4711")))
        assertEquals("Sehr geehrte Damen und Herren, 4711", Snippets.fill("{Anrede}, {Kundennummer}", bank, today))
        val frau = Contact("f", firstName = "Maria", lastName = "Huber", salutation = "Frau", title = "Mag.a", greeting = Greeting(formal = true))
        assertEquals("Sehr geehrte Frau Mag.a Huber", Snippets.anrede(frau))
    }

    @Test fun slashFinds() {
        assertEquals(listOf("termin"), Snippets.matching("/ter", Settings.DEFAULT_SNIPPETS).map { it.id })
        assertEquals(3, Snippets.matching("/", Settings.DEFAULT_SNIPPETS).size)
        assertEquals(0, Snippets.matching("ter", Settings.DEFAULT_SNIPPETS).size)
    }
}
