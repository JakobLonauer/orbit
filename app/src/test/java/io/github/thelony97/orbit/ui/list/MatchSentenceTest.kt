package io.github.thelony97.orbit.ui.list

import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchSentenceTest {
    private val c = Contact(
        "a", firstName = "Anna", notes = "Arbeitet seit 2024 im Krankenhaus der Barmherzigen Brüder in Linz",
        entries = listOf(Entry(id = "1", contactId = "a", typeId = "meet", at = 1, note = "Kaffee am Hauptplatz")),
    )

    @Test fun findsNoteSentenceAndHighlightRange() {
        val h = matchSentence(c, "kranken")!!
        assertEquals("kranken", h.text.substring(h.start, h.end).lowercase())
        assertEquals(false, h.text.startsWith("…"))
    }

    @Test fun findsHistoryAndIgnoresDiacritics() {
        val h = matchSentence(c, "hauptplatz")!!
        assertEquals("Hauptplatz", h.text.substring(h.start, h.end))
        val b = matchSentence(c, "bruder")!!
        assertEquals("Brüder", b.text.substring(b.start, b.end))
        assertEquals(true, b.text.startsWith("…"))
    }

    @Test fun nameMatchNeedsNoSentence() {
        assertNull(matchSentence(c, "anna"))
        assertNull(matchSentence(c, "x"))
    }
}
