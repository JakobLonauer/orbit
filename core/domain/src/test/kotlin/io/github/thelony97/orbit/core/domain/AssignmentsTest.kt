package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.ContactKind
import io.github.thelony97.orbit.core.model.OrbitDoc
import io.github.thelony97.orbit.core.model.OrgLink
import org.junit.Assert.assertEquals
import org.junit.Test

class AssignmentsTest {
    private fun org(id: String) = Contact(id = id, kind = ContactKind.ORG, company = id)
    private val doc = OrbitDoc(
        contacts = listOf(
            Contact(id = "me", isMe = true),
            Contact(id = "anna"), Contact(id = "tom"), Contact(id = "alt", archived = true),
            org("arzt"), org("lab"), org("weg").copy(deletedAt = 1),
        ),
        orgLinks = listOf(
            OrgLink("l1", "anna", "arzt", "gp"),
            OrgLink("l2", "anna", "lab", "colleague"),
            OrgLink("l3", "tom", "lab", "lead"),
            OrgLink("l4", "alt", "lab", "colleague"),
            OrgLink("l5", "me", "lab", "employer"),
            OrgLink("l6", "anna", "weg", "bank"),
            OrgLink("l7", "tom", "arzt", "custom", "Vertretung"),
        ),
    )
    private val visible = { c: Contact -> c.deletedAt == null && !c.archived }

    @Test fun personPageReadsWhatTheOrgIsToThem() {
        assertEquals(
            listOf(Assignments.Row("l1", "arzt", "Hausarzt"), Assignments.Row("l2", "lab", "Kolleg:in")),
            Assignments.ofPerson(doc, "anna", visible),
        )
        assertEquals("Vertretung", Assignments.ofPerson(doc, "tom", visible).first { it.otherId == "arzt" }.role)
    }

    @Test fun orgPageListsPeopleWithTheirSideButNotTheArchive() {
        assertEquals(
            listOf(Assignments.Row("l2", "anna", "Kolleg:in"), Assignments.Row("l3", "tom", "Projektleitung"), Assignments.Row("l5", "me", "Mitarbeiter:in")),
            Assignments.ofOrg(doc, "lab", visible),
        )
    }

    @Test fun alsoThereSkipsYourselfAndTheArchive() {
        assertEquals(listOf("arzt" to listOf("tom"), "lab" to listOf("tom")), Assignments.alsoThere(doc, "anna", visible))
    }
}
