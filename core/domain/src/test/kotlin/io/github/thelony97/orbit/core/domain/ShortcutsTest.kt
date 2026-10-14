package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.BuiltinTypes
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.ContactKind
import io.github.thelony97.orbit.core.model.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutsTest {
    private val day = 86_400_000L
    private val now = 1_000 * day

    private fun e(contact: String, daysAgo: Long, type: String = BuiltinTypes.MEET) =
        HistoryEntry(id = "$contact-$daysAgo-$type", contactId = contact, typeId = type, at = now - daysAgo * day)

    private fun c(id: String, fav: Boolean = true, vararg entries: HistoryEntry, archived: Boolean = false, org: Boolean = false, me: Boolean = false) =
        Contact(id = id, firstName = id, favorite = fav, archived = archived, isMe = me, kind = if (org) ContactKind.ORG else ContactKind.PERSON, entries = entries.toList())

    private val lists = { x: Contact -> Visibility.inLists(x, showOrgs = false) }

    @Test fun mostTalkedToComesFirstThenRecentThenName() {
        val all = listOf(
            c("Berta", true, e("Berta", 1)),
            c("Anna", true, e("Anna", 3), e("Anna", 4)),
            c("Cleo", true),
            c("Dora", true, e("Dora", 200)),
            c("Emil", false, e("Emil", 1), e("Emil", 2), e("Emil", 3)),
            c("Alt", true, e("Alt", 1), e("Alt", 2), archived = true),
            c("Firma", true, e("Firma", 1), e("Firma", 2), org = true),
            c("Ich", true, e("Ich", 1), me = true),
        )
        assertEquals(listOf("Anna", "Berta", "Dora", "Cleo"), Shortcuts.topFavorites(all, now, lists, { it.firstName }).map { it.id })
        // Organisations once they are shown.
        assertEquals("Firma", Shortcuts.topFavorites(all, now, { Visibility.inLists(it, true) }, { it.firstName }).first().id)
    }

    @Test fun futureEntriesDoNotCount() {
        val all = listOf(c("Anna", true, e("Anna", -5), e("Anna", -6)), c("Berta", true, e("Berta", 10)))
        assertEquals(listOf("Berta", "Anna"), Shortcuts.topFavorites(all, now, lists, { it.firstName }).map { it.id })
    }

    @Test fun lastCallIsTheNewestCallOutsideArchiveAndTrash() {
        val all = listOf(
            c("Anna", true, e("Anna", 2, BuiltinTypes.CALL), e("Anna", 0, BuiltinTypes.MEET)),
            c("Firma", false, e("Firma", 1, BuiltinTypes.CALL), org = true),
            c("Alt", false, e("Alt", 0, BuiltinTypes.CALL), archived = true),
            Contact(id = "Weg", deletedAt = 1, entries = listOf(e("Weg", 0, BuiltinTypes.CALL))),
        )
        assertEquals("Firma-1-call", Shortcuts.lastCall(all, now)?.id)
        assertNull(Shortcuts.lastCall(listOf(c("Anna", true, e("Anna", 1))), now))
    }
}
