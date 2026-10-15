package io.github.thelony97.orbit.domain

import io.github.thelony97.orbit.core.model.CustomField
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    private val anna = Contact(
        "a", firstName = "Anna", lastName = "Berger", notes = "Spielt Cello",
        pinnedNote = "Lieber SMS",
        fields = listOf(CustomField("f", "Kundennummer", "KD-48213")),
    )
    private val tom = Contact("t", firstName = "Tom", lastName = "Huber")
    private val idx = Index(OrbitData(contacts = listOf(anna, tom)))

    @Test fun findsOwnFieldsPinnedNoteAndUmlautFree() {
        assertTrue(Logic.matches(anna, "kd-48213", idx))
        assertTrue(Logic.matches(anna, "kundennummer", idx))
        assertTrue(Logic.matches(anna, "lieber", idx))
        assertTrue(Logic.matches(anna, "cello berger", idx))
        assertFalse(Logic.matches(tom, "cello", idx))
    }

    @Test fun soundsAlikeOnlyBehindExactHits() {
        val mayr = Contact("m", firstName = "Sepp", lastName = "Mayr", nickname = "Peppi")
        val i = Index(OrbitData(contacts = listOf(anna, tom, mayr)))
        // Not an exact hit, but sounds alike (Kölner Phonetik).
        assertFalse(Logic.matches(mayr, "Maier", i))
        assertTrue(Logic.soundsLike(mayr, "Maier", i))
        // Mixed: one word found, one only alike.
        assertTrue(Logic.soundsLike(mayr, "Sepp Meier", i))
        // Exact hits are never „alike“; unrelated names neither.
        assertFalse(Logic.soundsLike(mayr, "Mayr", i))
        assertFalse(Logic.soundsLike(tom, "Maier", i))
        // Rufname and title are found exactly.
        assertTrue(Logic.matches(mayr, "peppi", i))
        assertTrue(Logic.matches(anna.copy(title = "Mag.a"), "mag.a", i))
    }

    @Test fun shortNamePrefersTheRufname() {
        assertTrue(Logic.shortName(Contact("h", firstName = "Johann", nickname = "Hansi")) == "Hansi")
        assertTrue(Logic.titledName(Contact("x", firstName = "Anna", lastName = "Huber", title = "Dr.in")) == "Dr.in Anna Huber")
    }

    @Test fun cachedAndFreshContactsAgree() {
        // Same answer on repeat (cached) and for an edited copy that is not in this Index.
        assertTrue(Logic.matches(anna, "cello", idx))
        assertTrue(Logic.matches(anna, "cello", idx))
        val edited = anna.copy(notes = "Spielt Geige")
        assertFalse(Logic.matches(edited, "cello", idx))
        assertTrue(Logic.matches(edited, "geige", idx))
    }
}
