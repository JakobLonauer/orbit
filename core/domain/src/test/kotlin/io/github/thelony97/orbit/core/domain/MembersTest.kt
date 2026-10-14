package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.ContactKind
import io.github.thelony97.orbit.core.model.HistoryEntry
import io.github.thelony97.orbit.core.model.LabeledValue
import io.github.thelony97.orbit.core.model.Member
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MembersTest {
    private val mayer = Member("m1", "Herr Mayer", role = "Kundenberatung", phones = listOf(LabeledValue("Durchwahl", "0732 1234-56")))
    private val huber = Member("m2", "Frau Huber", role = "Empfang")
    private val berger = Member("m3", "Berger")
    private val org = Contact(
        id = "bank", kind = ContactKind.ORG, members = listOf(mayer, huber, berger),
        entries = listOf(
            HistoryEntry("e1", "bank", "call", at = 30, memberId = "m2"),
            HistoryEntry("e2", "bank", "call", at = 10, memberId = "m1"),
            HistoryEntry("e3", "bank", "call", at = 50),
            HistoryEntry("e4", "bank", "call", at = 20, memberId = "m2"),
        ),
    )

    @Test fun mostRecentConversationFirst() {
        assertEquals(listOf("m2", "m1", "m3"), Members.ordered(org).map { it.id })
        assertEquals(30L, Members.lastTalk(org, "m2"))
        assertNull(Members.lastTalk(org, "m3"))
        assertEquals(listOf("e1", "e4"), Members.talks(org, "m2").map { it.id })
    }

    @Test fun searchByNameRoleAndExtension() {
        assertEquals(true, Members.matches(mayer, "kunden"))
        assertEquals(true, Members.matches(mayer, "56"))
        assertEquals(false, Members.matches(huber, "56"))
        assertEquals(true, Members.matches(berger, " "))
    }

    @Test fun oneFieldMakesNameAndRole() {
        assertEquals("Herr Mayer" to "Kundenberatung", Members.parse(" Herr Mayer, Kundenberatung "))
        assertEquals("Herr Mayer" to "Kundenberatung", Members.parse("Herr Mayer (Kundenberatung)"))
        assertEquals("Berger" to "", Members.parse("Berger"))
        assertNull(Members.parse("  "))
    }
}
