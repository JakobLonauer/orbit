package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact

/**
 * Kalender-Brücke (PLAN 9 E9): an appointment belongs to a contact when one of its attendees has
 * their mail address, or its title names them – the full name anywhere, a first name or Rufname
 * only as a whole word and only when no one else carries it.
 */
object CalendarBridge {

    /** How far back finished appointments are offered. */
    const val DAYS_BACK = 3L

    fun match(title: String, attendees: List<String>, contacts: List<Contact>): String? {
        val live = contacts.filter { it.deletedAt == null && !it.archived && !it.isMe && !it.isOrg }
        val mails = attendees.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        live.firstOrNull { c -> c.emails.any { it.value.trim().lowercase() in mails } }?.let { return it.id }
        val words = Fold.fold(title).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
        val text = " " + words.joinToString(" ") + " "
        live.filter { c ->
            val full = Fold.fold(listOf(c.firstName, c.lastName).filter { it.isNotBlank() }.joinToString(" "))
            full.contains(' ') && text.contains(" $full ")
        }.singleOrNull()?.let { return it.id }
        val byWord = live.flatMap { c -> listOf(c.firstName, c.nickname).filter { it.isNotBlank() }.map { Fold.fold(it) to c.id } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.distinct() }
        return words.mapNotNull { w -> byWord[w]?.singleOrNull() }.distinct().singleOrNull()
    }

    /** „Treffen mit Anna“ unless the title says more. */
    fun title(eventTitle: String, name: String): String = eventTitle.trim().ifBlank { "Treffen mit $name" }
}
