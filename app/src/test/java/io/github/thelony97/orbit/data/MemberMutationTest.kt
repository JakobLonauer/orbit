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

class MemberFromCallTest {
    private val call = Entry(id = "c1", contactId = "jku", typeId = TypeIds.CALL, at = 9)
    private val doc = OrbitData(contacts = listOf(
        Contact(id = "jku", kind = io.github.thelony97.orbit.core.model.ContactKind.ORG, entries = listOf(call),
            members = listOf(io.github.thelony97.orbit.core.model.Member("m1", "Frau Huber", phones = listOf(LabeledValue("Durchwahl", "0732 2468-55"))))),
    ))

    @Test fun typedNameBecomesAMemberWithTheNumber() {
        val d = Mutations.memberFromCall(doc, "jku", "c1", "Herr Mayer, Studienservice", "+43 732 2468 1234", newMemberId = "m2")
        val jku = d.contacts.single()
        val m = jku.members.first { it.id == "m2" }
        assertEquals("Herr Mayer" to "Studienservice", m.name to m.role)
        assertEquals("+43 732 2468 1234", m.phones.single().value)
        assertEquals("m2", jku.entries.single().memberId)
    }

    @Test fun knownNumberJustGetsTheCallAndEmptyTextDoesNothing() {
        val d = Mutations.memberFromCall(doc, "jku", "c1", "egal", "0732 2468-55", newMemberId = "mX")
        assertEquals(1, d.contacts.single().members.size)
        assertEquals("m1", d.contacts.single().entries.single().memberId)
        assertEquals(doc, Mutations.memberFromCall(doc, "jku", "c1", "  ", "0732 9", newMemberId = "mY"))
        assertEquals(doc, Mutations.memberFromCall(doc, "jku", "weg", "Herr X", "0732 9"))
    }
}
