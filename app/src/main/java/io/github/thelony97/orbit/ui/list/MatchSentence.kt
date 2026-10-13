package io.github.thelony97.orbit.ui.list

import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.domain.Logic

/** Where a search hit sits in a contact, as a short excerpt (PLAN 1.4: „Suche zeigt den Satz“). */
data class Hit(val text: String, val start: Int, val end: Int)

/**
 * The first place [query] occurs outside the name: notes, how-we-met, job, topics, gift ideas,
 * then the Verlauf (notes and SMS, newest first). Null when only the name matched.
 */
fun matchSentence(c: Contact, query: String, before: Int = 24, after: Int = 44): Hit? {
    val q = Logic.fold(query.trim()).split(' ').firstOrNull { it.length >= 2 } ?: return null
    val name = Logic.fold(listOf(c.firstName, c.lastName, c.company).joinToString(" "))
    if (name.contains(q)) return null
    val sources = sequence {
        yield(c.notes); yield(c.pinnedNote); yield(c.howWeMet); yield(c.position)
        c.topics.forEach { yield(it.text) }
        c.gifts.forEach { yield(it.text) }
        c.entries.forEach { e ->
            yield(e.note)
            e.messages.forEach { yield(it.text) }
        }
    }
    for (raw in sources) {
        if (raw.isBlank()) continue
        val text = raw.replace('\n', ' ')
        val folded = Logic.fold(text)
        // Folding keeps positions only if it keeps the length (NFC input); otherwise no excerpt.
        if (folded.length != text.length) continue
        val i = folded.indexOf(q)
        if (i < 0) continue
        val from = (i - before).coerceAtLeast(0)
        val to = (i + q.length + after).coerceAtMost(text.length)
        val prefix = if (from > 0) "…" else ""
        val excerpt = prefix + text.substring(from, to).trim() + if (to < text.length) "…" else ""
        val leading = text.substring(from, to).length - text.substring(from, to).trimStart().length
        val s = prefix.length + (i - from) - leading
        return Hit(excerpt, s, s + q.length)
    }
    return null
}
