package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact

/**
 * Geister aus SMS und Signaturen (PLAN 9 E3): a number, mail address or postal address that a
 * contact wrote (their incoming SMS and notes in their Verlauf) but that is not in their card.
 * Fixed patterns only; the newest mention wins; [dismissed] keys never come back.
 */
object FieldGhosts {

    enum class Kind { PHONE, EMAIL, ADDRESS }

    data class Ghost(val kind: Kind, val value: String, val source: String, val at: Long) {
        fun key(contactId: String) = "fg:$contactId:" + value.filter { it.isLetterOrDigit() }.lowercase()
    }

    private val PHONE = Regex("(?<![\\d+])(\\+\\d{2,3}|00\\d{2,3}|0)[\\s/-]?\\d{2,4}(?:[\\s/-]?\\d{2,4}){1,4}(?!\\d)")
    private val MAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    /** „Hauptstraße 12, 4020 Linz“ – street with number, then a 4- or 5-digit postcode and a town. */
    private val ADDRESS = Regex("[A-ZÄÖÜ][\\p{L}.-]+(?:\\s[A-ZÄÖÜ][\\p{L}.-]+)?\\s\\d{1,4}[a-z]?(?:/\\d+)*,?\\s\\d{4,5}\\s[A-ZÄÖÜ][\\p{L}-]+")
    private const val MIN_DIGITS = 8
    private const val SOURCE_CHARS = 80

    fun find(c: Contact, normalize: (String) -> String?, dismissed: Set<String>): List<Ghost> {
        val texts = c.entries.filter { it.memberId == null }.flatMap { e ->
            e.messages.filter { it.incoming }.map { it.text to it.at } + listOf(e.note to e.at)
        }.filter { it.first.isNotBlank() }.sortedByDescending { it.second }
        val ownPhones = c.phones.mapNotNull { normalize(it.value) ?: it.value.filter(Char::isDigit) }.toSet()
        val ownMails = c.emails.map { it.value.trim().lowercase() }.toSet()
        val ownAddr = c.addresses.map { Fold.fold(it.value).filter(Char::isLetterOrDigit) }
        val out = LinkedHashMap<String, Ghost>()
        for ((text, at) in texts) {
            PHONE.findAll(text).map { it.value.trim() }.filter { v -> v.count(Char::isDigit) >= MIN_DIGITS && (normalize(v) ?: v.filter(Char::isDigit)) !in ownPhones }
                .forEach { v -> out.putIfAbsent("p" + (normalize(v) ?: v), Ghost(Kind.PHONE, v, snippet(text, v), at)) }
            MAIL.findAll(text).map { it.value.trimEnd('.') }.filter { it.lowercase() !in ownMails }
                .forEach { v -> out.putIfAbsent("m" + v.lowercase(), Ghost(Kind.EMAIL, v, snippet(text, v), at)) }
            ADDRESS.findAll(text).map { it.value }.filter { v -> ownAddr.none { it.contains(Fold.fold(v).filter(Char::isLetterOrDigit)) } }
                .forEach { v -> out.putIfAbsent("a" + Fold.fold(v), Ghost(Kind.ADDRESS, v, snippet(text, v), at)) }
        }
        return out.values.filter { it.key(c.id) !in dismissed }
    }

    private fun snippet(text: String, v: String): String {
        val i = text.indexOf(v).coerceAtLeast(0)
        val from = (i - SOURCE_CHARS / 2).coerceAtLeast(0)
        val to = (i + v.length + SOURCE_CHARS / 2).coerceAtMost(text.length)
        return (if (from > 0) "…" else "") + text.substring(from, to).replace('\n', ' ').trim() + if (to < text.length) "…" else ""
    }
}
