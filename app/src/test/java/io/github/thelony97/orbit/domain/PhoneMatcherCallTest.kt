package io.github.thelony97.orbit.domain

import io.github.thelony97.orbit.core.domain.Matcher
import io.github.thelony97.orbit.core.model.ContactKind
import io.github.thelony97.orbit.core.model.LabeledValue
import io.github.thelony97.orbit.core.model.Member
import io.github.thelony97.orbit.data.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Calls run through the Zuordnungs-Maschine: own number → member's Durchwahl → number range. */
@RunWith(RobolectricTestRunner::class)
class PhoneMatcherCallTest {
    private val contacts = listOf(
        Contact(id = "anna", phones = listOf(LabeledValue("Mobil", "0660 1234567"))),
        Contact(
            id = "jku", kind = ContactKind.ORG, phones = listOf(LabeledValue("Zentrale", "0732 2468 0")),
            numberPrefixes = listOf("0732 2468"),
            members = listOf(Member("m1", "Herr Mayer", phones = listOf(LabeledValue("Durchwahl", "0732 2468 1234")))),
        ),
    )
    private val m = PhoneMatcher(contacts, "AT")

    @Test fun ownNumberThenMemberThenRange() {
        assertEquals("anna", m.matchCall("+436601234567")?.contactId)
        assertEquals(Matcher.Via.NUMBER, m.matchCall("0732 24680")?.via)
        val member = m.matchCall("+43 732 2468 1234")!!
        assertEquals("jku" to "m1", member.contactId to member.memberId)
        val range = m.matchCall("0732 2468 9999")!!
        assertEquals("jku", range.contactId)
        assertEquals(Matcher.Via.PREFIX, range.via)
        assertNull(range.memberId)
        assertNull(m.matchCall("0732 9999 1234"))
        // The plain contact match is unchanged.
        assertNull(m.match("0732 2468 9999"))
    }
}
