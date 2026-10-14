package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact

/**
 * „Diese Nummer gibt es schon“ (PLAN 3.14): fixed rule – the same normalised number at another
 * contact (archived ones too, never the trash). [normalize] is the phone's E.164 helper; when it
 * cannot agree (roaming, a number without area code) the last nine digits decide, like caller ID.
 */
object Duplicates {

    data class Hit(val contactId: String, val number: String)

    private const val TAIL = 9

    private fun tail(normalized: String): String? =
        normalized.filter { it.isDigit() }.takeIf { it.length >= TAIL }?.takeLast(TAIL)

    fun sameNumber(candidate: Contact, contacts: List<Contact>, normalize: (String) -> String?): Hit? =
        Lookup(contacts.filter { it.id != candidate.id }, normalize).hit(candidate.phones.map { it.value })

    /** Many candidates against the same contacts (Mehrfach-Import): normalised number → contact. */
    class Lookup(contacts: List<Contact>, private val normalize: (String) -> String?) {
        private val exact = HashMap<String, String>()
        private val byTail = HashMap<String, String>()

        init {
            for (c in contacts) {
                if (c.deletedAt != null || c.isMe) continue
                for (p in c.phones) {
                    val n = normalize(p.value) ?: continue
                    exact.putIfAbsent(n, c.id)
                    tail(n)?.let { byTail.putIfAbsent(it, c.id) }
                }
            }
        }

        fun hit(numbers: List<String>): Hit? {
            val normalized = numbers.mapNotNull { raw -> normalize(raw)?.let { it to raw } }
            normalized.firstNotNullOfOrNull { (n, raw) -> exact[n]?.let { Hit(it, raw) } }?.let { return it }
            return normalized.firstNotNullOfOrNull { (n, raw) -> tail(n)?.let { byTail[it] }?.let { Hit(it, raw) } }
        }
    }
}
