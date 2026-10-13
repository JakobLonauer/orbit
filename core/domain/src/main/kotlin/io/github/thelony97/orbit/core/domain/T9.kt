package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import java.text.Normalizer

/**
 * Tastenfeld-Suche (PLAN 3.12 „Wählen“): typed digits find contacts by name (T9 letters) or by
 * number. Never archived or deleted contacts; hidden members are not contacts and never appear
 * (PLAN 3.6, 3.11). Organisations are included – calling the practice is what a dialpad is for.
 */
object T9 {

    /** Why a contact matched; lower is better. */
    enum class How { FIRST_WORD, WORD, INITIALS, NUMBER }

    /**
     * One hit: the contact, the number to dial, and which characters to highlight – indices into
     * the name passed in, or into that phone's value for a number match.
     */
    data class Hit(
        val contactId: String,
        val name: String,
        val phoneIndex: Int,
        val how: How,
        val nameMarks: List<Int> = emptyList(),
        val numberMarks: List<Int> = emptyList(),
    )

    private val keys = mapOf(
        '2' to "abc", '3' to "def", '4' to "ghi", '5' to "jkl",
        '6' to "mno", '7' to "pqrs", '8' to "tuv", '9' to "wxyz",
    ).flatMap { (d, ls) -> ls.map { it to d } }.toMap()

    /** The key a character is on: letters by T9 (umlauts on their base letter), digits on themselves. */
    fun digitOf(ch: Char): Char? {
        if (ch in '0'..'9') return ch
        val l = ch.lowercaseChar()
        keys[l]?.let { return it }
        if (l == 'ß') return '7'
        val base = Normalizer.normalize(l.toString(), Normalizer.Form.NFD).firstOrNull() ?: return null
        return keys[base]
    }

    /**
     * Contacts for [digits], best first: a name word starting with it (first word before the
     * others), the initials, then numbers containing it. Within a kind: favourites, then the more
     * recent [lastAt], then the name. Contacts without a number cannot be dialled and never show.
     */
    fun search(
        contacts: List<Contact>,
        digits: String,
        name: (Contact) -> String,
        lastAt: (Contact) -> Long? = { null },
        limit: Int = 20,
    ): List<Hit> {
        val d = digits.filter { it in '0'..'9' }
        if (d.isEmpty()) return emptyList()
        val hits = ArrayList<Pair<Hit, Contact>>()
        for (c in contacts) {
            if (c.deletedAt != null || c.archived || c.isMe) continue
            val phones = c.phones.withIndex().filter { it.value.value.any(Char::isDigit) }
            if (phones.isEmpty()) continue
            val n = name(c)
            val dial = phones.firstOrNull { it.index == c.defaultPhone }?.index ?: phones.first().index
            val byName = nameMatch(n, d)
            if (byName != null) {
                hits += Hit(c.id, n, dial, byName.first, nameMarks = byName.second) to c
                continue
            }
            phones.firstNotNullOfOrNull { (i, p) -> numberMarks(p.value, d)?.let { i to it } }?.let { (i, marks) ->
                hits += Hit(c.id, n, i, How.NUMBER, numberMarks = marks) to c
            }
        }
        return hits.sortedWith(
            compareBy<Pair<Hit, Contact>> { it.first.how.ordinal }
                .thenBy { !it.second.favorite }
                .thenByDescending { lastAt(it.second) ?: Long.MIN_VALUE }
                .thenBy { it.first.name.lowercase() },
        ).take(limit).map { it.first }
    }

    /** Word prefix (also running on into the next word: "annab" → Anna Berger) or initials. */
    fun nameMatch(name: String, d: String): Pair<How, List<Int>>? {
        // Characters that sit on a key, with their position and whether a word starts there.
        data class K(val at: Int, val digit: Char, val wordStart: Boolean)
        val ks = ArrayList<K>()
        var prevLetter = false
        name.forEachIndexed { i, ch ->
            val k = digitOf(ch)
            if (k != null) {
                ks += K(i, k, !prevLetter)
                prevLetter = true
            } else {
                prevLetter = false
            }
        }
        val starts = ks.indices.filter { ks[it].wordStart }
        for ((w, s) in starts.withIndex()) {
            if (s + d.length > ks.size) continue
            if ((d.indices).all { ks[s + it].digit == d[it] }) {
                return (if (w == 0) How.FIRST_WORD else How.WORD) to (s until s + d.length).map { ks[it].at }
            }
        }
        if (d.length in 2..starts.size) {
            for (w in 0..starts.size - d.length) {
                val picked = (0 until d.length).map { starts[w + it] }
                if (picked.withIndex().all { (j, s) -> ks[s].digit == d[j] }) return How.INITIALS to picked.map { ks[it].at }
            }
        }
        return null
    }

    /**
     * [d] inside a number, in the stored form or the national one ("0660 …" for "+43 660 …").
     * Returns the positions of the matched digits in [value], or null.
     */
    fun numberMarks(value: String, d: String): List<Int>? {
        val positions = value.indices.filter { value[it] in '0'..'9' }
        val digits = positions.map { value[it] }.joinToString("")
        digits.indexOf(d).takeIf { it >= 0 }?.let { at -> return (at until at + d.length).map { positions[it] } }
        // Typed nationally, stored internationally: the leading 0 stands for the country code.
        if (d.length > 1 && d[0] == '0' && value.trimStart().startsWith("+")) {
            val rest = d.substring(1)
            for (cc in 1..3) {
                if (digits.length > cc && digits.startsWith(rest, cc)) {
                    return (cc until cc + rest.length).map { positions[it] }
                }
            }
        }
        return null
    }
}
