package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact

/**
 * „Diese Nummer gibt es schon“ (PLAN 3.14): fixed rule – the same normalised number at another
 * contact (archived ones too, never the trash). [normalize] is the phone's E.164 helper.
 */
object Duplicates {

    data class Hit(val contactId: String, val number: String)

    fun sameNumber(candidate: Contact, contacts: List<Contact>, normalize: (String) -> String?): Hit? {
        val mine = candidate.phones.mapNotNull { p -> normalize(p.value)?.let { it to p.value } }
        if (mine.isEmpty()) return null
        for (other in contacts) {
            if (other.id == candidate.id || other.deletedAt != null || other.isMe) continue
            val theirs = other.phones.mapNotNull { normalize(it.value) }.toSet()
            mine.firstOrNull { (n, _) -> n in theirs }?.let { return Hit(other.id, it.second) }
        }
        return null
    }
}
