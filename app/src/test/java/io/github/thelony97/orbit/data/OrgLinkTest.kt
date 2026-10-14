package io.github.thelony97.orbit.data

import io.github.thelony97.orbit.core.model.ContactKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrgLinkTest {
    private val doc = OrbitData(
        contacts = listOf(Contact(id = "anna"), Contact(id = "arzt", kind = ContactKind.ORG), Contact(id = "lab", kind = ContactKind.ORG)),
    )

    @Test fun addsOncePerRoleAndOnlyPersonToOrg() {
        val d1 = Mutations.addOrgLink(doc, "anna", "arzt", "gp")
        val d2 = Mutations.addOrgLink(d1, "anna", "arzt", "gp")
        assertEquals(1, d2.orgLinks.size)
        assertEquals(2, Mutations.addOrgLink(d2, "anna", "arzt", "dentist").orgLinks.size)
        // Wrong way round or between two organisations: nothing.
        assertEquals(doc, Mutations.addOrgLink(doc, "arzt", "anna", "gp"))
        assertEquals(doc, Mutations.addOrgLink(doc, "lab", "arzt", "gp"))
        // A custom role needs its name.
        assertEquals(doc, Mutations.addOrgLink(doc, "anna", "lab", "custom", "  "))
        assertEquals("Vertretung", Mutations.addOrgLink(doc, "anna", "lab", "custom", " Vertretung ").orgLinks.single().label)
    }

    @Test fun purgeTakesTheZuordnungenAlong() {
        val d = Mutations.addOrgLink(Mutations.addOrgLink(doc, "anna", "arzt", "gp"), "anna", "lab", "colleague")
        assertTrue(Mutations.purge(d, "arzt").orgLinks.none { it.orgId == "arzt" })
        assertTrue(Mutations.purge(d, "anna").orgLinks.isEmpty())
        val link = d.orgLinks.first()
        assertEquals(1, Mutations.removeOrgLink(d, link.id).orgLinks.size)
    }
}
