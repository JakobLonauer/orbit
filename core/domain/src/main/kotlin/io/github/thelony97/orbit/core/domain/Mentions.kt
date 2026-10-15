package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact

/**
 * @-Erwähnungen (PLAN 9 E13): „@Anna“ or „@Anna_Huber“ in a note points to that contact; on her
 * page the note shows up as „Erwähnt bei Tom“. A first name counts only when it is unique.
 */
object Mentions {

    private val AT = Regex("@([\\p{L}][\\p{L}_-]*)")

    data class Backlink(val fromId: String, val entryId: String?, val text: String, val at: Long)

    /** Contact ids mentioned in [text]. */
    fun find(text: String, contacts: List<Contact>): Set<String> {
        if ('@' !in text) return emptySet()
        val live = contacts.filter { it.deletedAt == null && !it.isMe }
        return AT.findAll(text).mapNotNull { m -> resolve(m.groupValues[1], live) }.toSet()
    }

    private fun resolve(token: String, live: List<Contact>): String? {
        val q = Fold.fold(token.replace('_', ' ').replace('-', ' ')).trim()
        val full = live.filter { c -> Fold.fold(listOf(c.firstName, c.lastName).filter { it.isNotBlank() }.joinToString(" ")) == q || Fold.fold(CallerIndex.nameOf(c)) == q }
        full.singleOrNull()?.let { return it.id }
        return live.filter { c -> Fold.fold(c.firstName) == q || (c.nickname.isNotBlank() && Fold.fold(c.nickname) == q) }.singleOrNull()?.id
    }

    /** Where [id] is mentioned: entry notes and the notes of other contacts, newest first. */
    fun backlinks(id: String, contacts: List<Contact>): List<Backlink> = contacts
        .filter { it.id != id && it.deletedAt == null }
        .flatMap { c ->
            val inEntries = c.entries.filter { e -> '@' in e.note && id in find(e.note, contacts) }.map { e -> Backlink(c.id, e.id, e.note, e.at) }
            val inNotes = listOf(c.notes, c.pinnedNote).filter { n -> '@' in n && id in find(n, contacts) }.map { Backlink(c.id, null, it, c.updatedAt) }
            inEntries + inNotes
        }
        .sortedByDescending { it.at }

    /** The @word being typed at the end of [text] („Wir waren mit @An“ → „An“); null when none. */
    fun typing(text: String): String? = Regex("@([\\p{L}_-]*)$").find(text)?.groupValues?.get(1)

    /** Puts [c]'s @handle in place of the @word being typed. */
    fun complete(text: String, c: Contact, unique: Boolean): String {
        val handle = if (unique) c.firstName.ifBlank { CallerIndex.nameOf(c) } else listOf(c.firstName, c.lastName).filter { it.isNotBlank() }.joinToString("_")
        return text.replace(Regex("@[\\p{L}_-]*$"), "@" + handle.replace(' ', '_') + " ")
    }
}
