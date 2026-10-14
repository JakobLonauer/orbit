package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.HistoryEntry
import io.github.thelony97.orbit.core.model.Member

/**
 * Versteckte Mitarbeiter (PLAN 3.6): people who exist only inside one organisation. Never in the
 * list, search, Verlauf tab, Anlässe, Netz, Karte or dialpad – only on their organisation's page
 * and their own. Their conversations are the organisation's entries carrying their `memberId`.
 */
object Members {

    /** Searching „Nur hier“ is worth it from this many on. */
    const val SEARCH_FROM = 8

    fun talks(org: Contact, memberId: String): List<HistoryEntry> =
        org.entries.filter { it.memberId == memberId }.sortedByDescending { it.at }

    fun lastTalk(org: Contact, memberId: String): Long? = org.entries.filter { it.memberId == memberId }.maxOfOrNull { it.at }

    /** Members with the most recent conversation first, then by name. */
    fun ordered(org: Contact): List<Member> {
        val last = org.entries.filter { it.memberId != null }.groupBy { it.memberId }.mapValues { (_, e) -> e.maxOf { it.at } }
        return org.members.sortedWith(compareByDescending<Member> { last[it.id] ?: Long.MIN_VALUE }.thenBy { it.name.lowercase() })
    }

    fun matches(m: Member, query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        val digits = q.filter { it.isDigit() }
        return m.name.lowercase().contains(q) || m.role.lowercase().contains(q) || m.note.lowercase().contains(q) ||
            m.emails.any { it.value.lowercase().contains(q) } ||
            (digits.length >= 2 && m.phones.any { p -> p.value.filter { it.isDigit() }.contains(digits) })
    }

    /** One field (PLAN 3.6 „mit einem Feld anlegen“): „Herr Mayer, Kundenberatung“ or „Herr Mayer (Kundenberatung)“. */
    fun parse(text: String): Pair<String, String>? {
        val t = text.trim()
        if (t.isEmpty()) return null
        Regex("^(.+?)\\s*\\((.+)\\)$").find(t)?.let { return it.groupValues[1].trim() to it.groupValues[2].trim() }
        val comma = t.indexOf(',')
        if (comma > 0) return t.substring(0, comma).trim() to t.substring(comma + 1).trim()
        return t to ""
    }
}
