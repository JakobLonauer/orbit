package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.LabeledValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DuplicatesTest {
    // Austrian numbers to one form, like the phone's E.164 helper.
    private val norm = { s: String -> s.filter { it.isDigit() }.let { d -> if (d.startsWith("0")) "43" + d.drop(1) else d }.takeIf { it.length >= 6 } }
    private fun c(id: String, vararg n: String, deleted: Boolean = false, archived: Boolean = false) =
        Contact(id = id, phones = n.map { LabeledValue("Mobil", it) }, deletedAt = if (deleted) 1 else null, archived = archived)

    @Test fun sameNumberInAnotherFormIsFound() {
        val all = listOf(c("anna", "0660 1234567"), c("weg", "0699 1", deleted = true), c("alt", "+43 664 555555", archived = true))
        assertEquals(Duplicates.Hit("anna", "+43 660 1234567"), Duplicates.sameNumber(c("neu", "+43 660 1234567"), all, norm))
        assertEquals("alt", Duplicates.sameNumber(c("neu", "0664 555555"), all, norm)?.contactId)
        assertNull(Duplicates.sameNumber(c("anna", "0660 1234567"), all, norm))
        assertNull(Duplicates.sameNumber(c("neu", "0699 1"), all, norm))
        assertNull(Duplicates.sameNumber(c("neu"), all, norm))
    }

    @Test fun lookupFindsTheSameAsTheSingleCheck() {
        val all = listOf(c("anna", "0660 1234567"), c("weg", "0699 1111111", deleted = true), c("alt", "+43 664 555555", archived = true))
        val lookup = Duplicates.Lookup(all, norm)
        assertEquals(Duplicates.Hit("anna", "+43 660 1234567"), lookup.hit(listOf("123", "+43 660 1234567")))
        assertEquals("alt", lookup.hit(listOf("0664555555"))?.contactId)
        assertNull(lookup.hit(listOf("0699 1111111")))
        assertNull(lookup.hit(emptyList()))
    }

    @Test fun lastNineDigitsDecideWhenTheFormsDisagree() {
        // Roaming: the phone reads "0660 …" with another country, so the E.164 forms differ.
        val plain = { s: String -> s.filter { it.isDigit() }.takeIf { it.length >= 6 } }
        val all = listOf(c("anna", "+43 660 1234567"))
        assertEquals("anna", Duplicates.sameNumber(c("neu", "0660 1234567"), all, plain)?.contactId)
        // Too short for the tail rule: no guess.
        assertNull(Duplicates.sameNumber(c("neu", "1234567"), listOf(c("kurz", "01234567")), plain))
    }
}
