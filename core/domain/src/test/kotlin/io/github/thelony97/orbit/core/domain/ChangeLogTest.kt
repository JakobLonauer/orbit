package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.CheckItem
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.HistoryEntry
import io.github.thelony97.orbit.core.model.LabeledValue
import io.github.thelony97.orbit.core.model.OrbitDoc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeLogTest {
    private var n = 0
    private val ids = { "c${n++}" }

    @Test fun diffAndRevert() {
        val a = Contact("a", firstName = "Anna", phones = listOf(LabeledValue("", "0664 1")), entries = listOf(HistoryEntry("e", "a", "call", 1)))
        val b = a.copy(firstName = "Anni", phones = a.phones + LabeledValue("", "0732 2"), updatedAt = 5, topics = listOf(CheckItem("t", "Urlaub")))
        val ch = ChangeLog.diff(OrbitDoc(contacts = listOf(a)), OrbitDoc(contacts = listOf(b)), 10, ids)
        assertEquals(setOf("firstName", "phones", "topics"), ch.map { it.field }.toSet())
        val first = ch.first { it.field == "firstName" }
        assertEquals("Anna", ChangeLog.preview(first.old))
        assertEquals("0664 1, 0732 2", ChangeLog.preview(ch.first { it.field == "phones" }.new))
        val back = ChangeLog.revert(b, first)
        assertEquals("Anna", back.firstName)
        assertEquals(2, back.phones.size)
        assertEquals(1, back.entries.size)
        val noTopics = ChangeLog.revert(b, ch.first { it.field == "topics" })
        assertTrue(noTopics.topics.isEmpty())
    }

    @Test fun revertNeverOverwritesALaterChange() {
        val a = Contact("a", phones = listOf(LabeledValue("", "A")))
        val b = a.copy(phones = listOf(LabeledValue("", "B")))
        val c = b.copy(phones = listOf(LabeledValue("", "C")))
        val ab = ChangeLog.diff(OrbitDoc(contacts = listOf(a)), OrbitDoc(contacts = listOf(b)), 1, ids).single()
        assertTrue(!ChangeLog.canRevert(c, ab))
        assertEquals("C", ChangeLog.revert(c, ab).phones.single().value)
        assertTrue(ChangeLog.canRevert(b, ab))
        val back = ChangeLog.revert(b, ab)
        assertEquals("A", back.phones.single().value)
        assertEquals("B", ChangeLog.redo(back, ab).phones.single().value)
    }

    @Test fun onlyUpdatedAtIsNoChange() {
        val a = Contact("a", firstName = "Anna")
        assertTrue(ChangeLog.diff(OrbitDoc(contacts = listOf(a)), OrbitDoc(contacts = listOf(a.copy(updatedAt = 9))), 1, ids).isEmpty())
    }

    @Test fun prune() {
        val old = ChangeLog.Change("1", "a", "f", "1", "2", 0)
        val fresh = old.copy(id = "2", at = ChangeLog.KEEP_MS)
        assertEquals(listOf("2"), ChangeLog.prune(listOf(old, fresh), ChangeLog.KEEP_MS + 1).map { it.id })
    }
}
