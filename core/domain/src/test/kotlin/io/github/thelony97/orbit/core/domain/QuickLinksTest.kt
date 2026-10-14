package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.ContactKind
import io.github.thelony97.orbit.core.model.Link
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickLinksTest {
    @Test fun pinnedOnlyOrganisationsFirstNoArchive() {
        val contacts = listOf(
            Contact(id = "anna", firstName = "Anna", links = listOf(Link("a1", "LinkedIn", "https://linkedin.com", pinned = true))),
            Contact(id = "jku", kind = ContactKind.ORG, company = "JKU", links = listOf(
                Link("j1", "KUSSS", "https://kusss.jku.at", pinned = true),
                Link("j2", "Moodle", "https://moodle.jku.at"),
                Link("j3", "Mensa", "https://mensen.at", pinned = true),
            )),
            Contact(id = "alt", archived = true, links = listOf(Link("x", "X", "https://x.at", pinned = true))),
            Contact(id = "weg", deletedAt = 1, links = listOf(Link("y", "Y", "https://y.at", pinned = true))),
        )
        assertEquals(listOf("j1", "j3", "a1"), QuickLinks.pinned(contacts) { it.firstName + it.company }.map { it.link.id })
    }
}
