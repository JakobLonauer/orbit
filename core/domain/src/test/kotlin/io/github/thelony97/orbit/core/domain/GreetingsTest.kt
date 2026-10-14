package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.Greeting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gratulations-Vorlage pro Kontakt (PLAN 3.5); an optional field old data does not have. */
class GreetingsTest {
    private val anna = Contact(id = "anna", firstName = "Anna", lastName = "Huber")

    // The shape 1.0.101 and the R2 candidate write: no greeting on the contact.
    private val old = """
        {"schema":2,"contacts":[{"id":"anna","firstName":"Anna","lastName":"Huber","simId":"sim-2","phones":[{"label":"Mobil","value":"+43 660 1"}]}],
         "settings":{"onboardingDone":true}}
    """.trimIndent()

    @Test fun oldDataHasNoGreetingAndGreetsAsBefore() {
        val c = DocJson.decode(old).contacts.single()
        assertNull(c.greeting)
        assertTrue(DocJson.decode(old).settings.meetingBrief)
        assertEquals("sim-2", c.simId)
        // Exactly the text R3 used before the template existed.
        assertEquals("Alles Gute zum Geburtstag, Anna!", Greetings.text(c))
    }

    @Test fun greetingRoundTrips() {
        val doc = DocJson.decode(old).let { d -> d.copy(contacts = d.contacts.map { it.copy(greeting = Greeting(formal = true, language = "en")) }) }
        val back = DocJson.decode(DocJson.encode(doc)).contacts.single()
        assertEquals(Greeting(formal = true, language = "en"), back.greeting)
        assertEquals("+43 660 1", back.phones.single().value)
    }

    @Test fun duUsesFirstNameSieTheFullName() {
        assertEquals("Herzlichen Glückwunsch zum Geburtstag, Anna Huber! Alles Gute für Sie.", Greetings.text(anna, Greeting(formal = true)))
        assertEquals("Happy birthday, Anna!", Greetings.text(anna, Greeting(language = "en")))
        assertEquals("¡Feliz cumpleaños, Anna Huber! Le deseo lo mejor.", Greetings.text(anna, Greeting(true, "es")))
        assertEquals("Joyeux anniversaire, Anna !", Greetings.text(anna, Greeting(language = "fr")))
    }

    @Test fun everyLanguageHasBothForms() {
        Greetings.LANGUAGES.forEach { (code, _) ->
            val du = Greetings.text(anna, Greeting(false, code))
            val sie = Greetings.text(anna, Greeting(true, code))
            assertTrue(code, "Anna" in du && "Huber" !in du)
            assertTrue(code, "Anna Huber" in sie)
        }
    }

    @Test fun unknownLanguageAndMissingNames() {
        assertEquals("Alles Gute zum Geburtstag, Anna!", Greetings.text(anna, Greeting(language = "xx")))
        // Sie without a last name, Du without a first name.
        assertEquals("Herzlichen Glückwunsch zum Geburtstag, Anna! Alles Gute für Sie.", Greetings.text(Contact(id = "a", firstName = "Anna"), Greeting(formal = true)))
        assertEquals("Happy birthday, Huber!", Greetings.text(Contact(id = "h", lastName = "Huber"), Greeting(language = "en")))
        assertEquals("Alles Gute zum Geburtstag!", Greetings.text(Contact(id = "x")))
        assertEquals("Joyeux anniversaire !", Greetings.text(Contact(id = "x"), Greeting(language = "fr")))
    }

    @Test fun languageChipCycles() {
        assertEquals("en", Greetings.nextLanguage("de"))
        assertEquals("de", Greetings.nextLanguage(Greetings.LANGUAGES.last().first))
        assertEquals("en", Greetings.nextLanguage("xx").let { Greetings.nextLanguage(it) })
        assertEquals("Deutsch", Greetings.languageName("xx"))
    }
}
