package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.domain.Matcher.Via
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.ContactKind
import io.github.thelony97.orbit.core.model.LabeledValue
import io.github.thelony97.orbit.core.model.Member
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatcherTest {

    /** Austrian test normaliser: 0… -> +43…, 00… -> +…, strips separators. */
    private val at: (String) -> String? = { raw ->
        val d = raw.filter { it.isDigit() }
        when {
            d.length < 3 -> null
            raw.trim().startsWith("+") -> "+$d"
            d.startsWith("00") -> "+" + d.drop(2)
            d.startsWith("0") -> "+43" + d.drop(1)
            else -> d
        }
    }

    private fun phone(v: String) = LabeledValue("", v)
    private fun mail(v: String) = LabeledValue("", v)

    private val anna = Contact("anna", firstName = "Anna", phones = listOf(phone("0660 1234567")), emails = listOf(mail("Anna@Example.org")))
    private val alt = Contact("alt", firstName = "Alt", archived = true, phones = listOf(phone("0664 7654321")))
    private val trash = Contact("trash", deletedAt = 5, phones = listOf(phone("0699 1111111")))
    private val me = Contact("me", isMe = true, phones = listOf(phone("0650 9999999")))
    private val liles = Contact(
        "liles", kind = ContactKind.ORG, firstName = "LiLES Servicecenter",
        phones = listOf(phone("0732 2468 0")),
        members = listOf(Member("m1", "Frau Huber", phones = listOf(phone("0732 2468 123")), emails = listOf(mail("huber@liles.at")))),
        numberPrefixes = listOf("0732 2468"),
        emailDomains = listOf("@liles.at"),
    )
    private val jku = Contact("jku", kind = ContactKind.ORG, emailDomains = listOf("jku.at"), numberPrefixes = listOf("0732 24"))
    private val m = Matcher(listOf(anna, alt, trash, me, liles, jku), at)

    @Test fun exactAndFormattingInsensitive() {
        val r = m.matchNumber("+43 660 1234567")!!
        assertEquals("anna", r.contactId)
        assertEquals(Via.NUMBER, r.via)
        assertFalse(r.hiddenFromLists)
    }

    @Test fun tailMatchWhenCountryCodeDiffers() {
        assertEquals("anna", m.matchNumber("0049 660 1234567")?.contactId)
    }

    @Test fun archivedIsRecognisedButFlagged() {
        val r = m.matchNumber("06647654321")!!
        assertEquals("alt", r.contactId)
        assertTrue(r.archived)
        assertTrue(r.hiddenFromLists)
    }

    @Test fun trashAndMeAreIgnored() {
        assertNull(m.matchNumber("0699 1111111"))
        assertNull(m.matchNumber("0650 9999999"))
    }

    @Test fun memberExtensionBeatsRange() {
        val r = m.matchNumber("0732 2468123")!!
        assertEquals("liles", r.contactId)
        assertEquals("m1", r.memberId)
        assertEquals(Via.MEMBER, r.via)
        assertTrue(r.hiddenFromLists)
    }

    @Test fun longestRangeWins() {
        val r = m.matchNumber("0732 2468 555")!!
        assertEquals("liles", r.contactId)
        assertEquals(Via.PREFIX, r.via)
        assertTrue(r.org)
        assertEquals("jku", m.matchNumber("0732 2457 0")?.contactId)
        assertNull(m.matchNumber("0732 999"))
    }

    @Test fun mailExactMemberAndDomain() {
        assertEquals(Via.EMAIL, m.matchMail(" anna@example.ORG ")?.via)
        val h = m.matchMail("HUBER@liles.at")!!
        assertEquals("m1", h.memberId)
        assertEquals(Via.DOMAIN, m.matchMail("info@liles.at")?.via)
        assertEquals("jku", m.matchMail("k123@students.jku.at")?.contactId)
        assertNull(m.matchMail("x@notjku.at"))
        assertNull(m.matchMail("kaputt"))
    }

    @Test fun sharedNumberPrefersActivePerson() {
        val shared = Contact("org", kind = ContactKind.ORG, phones = listOf(phone("0660 1234567")))
        val mm = Matcher(listOf(shared, alt.copy(phones = anna.phones), anna), at)
        assertEquals("anna", mm.matchNumber("0660 1234567")?.contactId)
    }
}
