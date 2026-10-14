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

/** „Zu eigenem Kontakt machen“ (PLAN 3.6): nothing written about the member is lost. */
class MemberToContactTest {
    private val talk1 = Entry(id = "e1", contactId = "jku", typeId = TypeIds.CALL, at = 5, note = "Anrechnung", memberId = "m1")
    private val talk2 = Entry(id = "e2", contactId = "jku", typeId = TypeIds.MEET, at = 9, memberId = "m1")
    private val other = Entry(id = "e3", contactId = "jku", typeId = TypeIds.CALL, at = 7)
    private val mayer = io.github.thelony97.orbit.core.model.Member(
        "m1", "Herr Mayer", role = "Studienservice", note = "Spricht schnell",
        phones = listOf(LabeledValue("Durchwahl", "0732 2468 1234")), emails = listOf(LabeledValue("Arbeit", "mayer@jku.at")),
    )
    private val doc = OrbitData(contacts = listOf(
        Contact(id = "jku", kind = io.github.thelony97.orbit.core.model.ContactKind.ORG, members = listOf(mayer), entries = listOf(talk2, other, talk1)),
        Contact(id = "max", firstName = "Max", lastName = "Mayer", phones = listOf(LabeledValue("Mobil", "0660 1")), emails = listOf(LabeledValue("Privat", "MAYER@jku.at")), notes = "Nachbar"),
    ))

    @Test fun newPersonTakesConversationsNoteAndGetsTheZuordnung() {
        val (d, id) = Mutations.memberToContact(doc, "jku", "m1", newContactId = "p1", at = 100)
        assertEquals("p1", id)
        val jku = d.contacts.first { it.id == "jku" }
        assertEquals(emptyList<io.github.thelony97.orbit.core.model.Member>(), jku.members)
        assertEquals(listOf("e3"), jku.entries.map { it.id })
        val p = d.contacts.first { it.id == "p1" }
        assertEquals("" to "Mayer", p.firstName to p.lastName)
        assertEquals(listOf("e2", "e1"), p.entries.map { it.id })
        assertEquals(listOf("p1", "p1"), p.entries.map { it.contactId })
        assertEquals(listOf(null, null), p.entries.map { it.memberId })
        assertEquals("Spricht schnell", p.notes)
        assertEquals("0732 2468 1234", p.phones.single().value)
        val link = d.orgLinks.single()
        assertEquals(Triple("p1", "jku", "custom"), Triple(link.personId, link.orgId, link.role))
        assertEquals("Studienservice", link.label)
    }

    @Test fun mergeIntoExistingKeepsTheirDataAndAddsOnlyNew() {
        val (d, id) = Mutations.memberToContact(doc, "jku", "m1", intoId = "max", at = 100)
        assertEquals("max", id)
        val max = d.contacts.first { it.id == "max" }
        assertEquals(listOf("0660 1", "0732 2468 1234"), max.phones.map { it.value })
        // Same address in another case is no second one.
        assertEquals(1, max.emails.size)
        assertEquals("Nachbar\nSpricht schnell", max.notes)
        assertEquals(listOf("e2", "e1"), max.entries.map { it.id })
        assertEquals(2, d.contacts.size)
    }

    @Test fun unknownTargetsChangeNothing() {
        assertEquals(doc to null, Mutations.memberToContact(doc, "jku", "weg"))
        assertEquals(doc to null, Mutations.memberToContact(doc, "jku", "m1", intoId = "jku"))
        assertEquals("" to "Huber", Mutations.splitMemberName("Frau Huber"))
        assertEquals("Anna Maria" to "Huber", Mutations.splitMemberName("Anna Maria Huber"))
        assertEquals("Berger" to "", Mutations.splitMemberName("Berger"))
    }
}

/** Review R4: a call both at the member and (synced) at the person is merged, its note kept. */
class MemberMergeDuplicateTest {
    @Test fun sameCallTwiceKeepsOneRowWithTheNote() {
        val memberCall = Entry(id = "o1", contactId = "jku", typeId = TypeIds.CALL, at = 5, note = "Anrechnung", memberId = "m1", externalId = "call:5")
        val personCall = Entry(id = "p1", contactId = "max", typeId = TypeIds.CALL, at = 5, externalId = "call:5")
        val doc = OrbitData(contacts = listOf(
            Contact(id = "jku", kind = io.github.thelony97.orbit.core.model.ContactKind.ORG,
                members = listOf(io.github.thelony97.orbit.core.model.Member("m1", "Herr Mayer")), entries = listOf(memberCall)),
            Contact(id = "max", firstName = "Max", entries = listOf(personCall)),
        ))
        val (d, _) = Mutations.memberToContact(doc, "jku", "m1", intoId = "max")
        val max = d.contacts.first { it.id == "max" }
        assertEquals(listOf("p1"), max.entries.map { it.id })
        assertEquals("Anrechnung", max.entries.single().note)
        assertEquals(emptyList<Entry>(), d.contacts.first { it.id == "jku" }.entries)
    }

    @Test fun editSaveKeepsMembersAddedMeanwhile() {
        val stale = Contact(id = "jku", kind = io.github.thelony97.orbit.core.model.ContactKind.ORG, company = "JKU")
        val now = stale.copy(members = listOf(io.github.thelony97.orbit.core.model.Member("m9", "Neu")))
        val d = Mutations.upsertContact(OrbitData(contacts = listOf(now)), stale.copy(company = "JKU Linz"))
        assertEquals("JKU Linz", d.contacts.single().company)
        assertEquals(listOf("m9"), d.contacts.single().members.map { it.id })
    }
}
