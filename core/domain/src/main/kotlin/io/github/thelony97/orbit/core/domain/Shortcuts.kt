package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.BuiltinTypes
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.HistoryEntry

/** PLAN 3.14 Komfort: who the App-Shortcuts and the Favoriten-Widget show, and the last call. */
object Shortcuts {

    private const val DAY = 86_400_000L

    /**
     * Favourites, the ones talked to most in the last [days] days first, then the most recent one,
     * then by name. [visible] decides archive and organisations (lists rule).
     */
    fun topFavorites(
        contacts: List<Contact>,
        now: Long,
        visible: (Contact) -> Boolean,
        name: (Contact) -> String,
        days: Int = 90,
    ): List<Contact> {
        val since = now - days * DAY
        return contacts
            .filter { it.favorite && !it.isMe && visible(it) }
            .map { c ->
                val past = c.entries.filter { it.at <= now }
                Triple(c, past.count { it.at >= since }, past.maxOfOrNull { it.at } ?: 0L)
            }
            .sortedWith(compareByDescending<Triple<Contact, Int, Long>> { it.second }.thenByDescending { it.third }.thenBy { name(it.first).lowercase() })
            .map { it.first }
    }

    /**
     * „Notiz zum letzten Anruf“: the newest call in the Verlauf of a contact that is not archived
     * or in the trash; organisations count too (their calls get notes as well).
     */
    fun lastCall(contacts: List<Contact>, now: Long): HistoryEntry? =
        contacts.asSequence()
            .filter { it.deletedAt == null && !it.archived && !it.isMe }
            .flatMap { it.entries.asSequence() }
            .filter { it.typeId == BuiltinTypes.CALL && it.at <= now }
            .maxByOrNull { it.at }
}
