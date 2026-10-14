package io.github.thelony97.orbit.data

import io.github.thelony97.orbit.core.model.ContactKind
import io.github.thelony97.orbit.core.model.Member
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemberMutationTest {
    private val talk = Entry(id = "e1", contactId = "bank", typeId = TypeIds.CALL, at = 5, note = "Kredit", memberId = "m1")
    private val doc = OrbitData(contacts = listOf(
        Contact(id = "bank", kind = ContactKind.ORG, members = listOf(Member("m1", "Herr Mayer")), entries = listOf(talk)),
        Contact(id = "anna"),
    ))

    @Test fun onlyOrganisationsHaveMembers() {
        assertEquals(doc, Mutations.upsertMember(doc, "anna", Member("x", "X")))
        val d = Mutations.upsertMember(doc, "bank", Member("m2", "Frau Huber", role = "Empfang"))
        assertEquals(listOf("m1", "m2"), d.contacts.first().members.map { it.id })
        val renamed = Mutations.upsertMember(d, "bank", Member("m1", "Hr. Mayer"))
        assertEquals("Hr. Mayer", renamed.contacts.first().members.first().name)
    }

    @Test fun removingAMemberKeepsTheConversation() {
        val d = Mutations.removeMember(doc, "bank", "m1")
        val bank = d.contacts.first()
        assertEquals(emptyList<Member>(), bank.members)
        assertEquals("Kredit", bank.entries.single().note)
        assertNull(bank.entries.single().memberId)
    }
}
