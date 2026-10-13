package io.github.thelony97.orbit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** „Zurückholen“ from the caller card (PLAN 3.11): only the archive flag changes. */
class UnarchiveTest {
    private val archived = Contact(id = "a", firstName = "Lisa", archived = true, archivedAt = 5L, notes = "bleibt")
    private val other = Contact(id = "b", firstName = "Tom", archived = true, archivedAt = 7L)

    @Test fun bringsBackOnlyThatContactAndKeepsEverything() {
        val d = Mutations.unarchive(OrbitData(contacts = listOf(archived, other)), "a")
        val a = d.contacts.first { it.id == "a" }
        assertFalse(a.archived)
        assertNull(a.archivedAt)
        assertEquals("bleibt", a.notes)
        assertEquals(other, d.contacts.first { it.id == "b" })
    }

    @Test fun activeContactIsUntouched() {
        val active = Contact(id = "c", firstName = "Bea")
        val d = Mutations.unarchive(OrbitData(contacts = listOf(active)), "c")
        assertSame(active, d.contacts.single())
    }
}
