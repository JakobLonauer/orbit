package io.github.thelony97.orbit.call

import io.github.thelony97.orbit.core.domain.Herold
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.ContactKind
import io.github.thelony97.orbit.data.LabeledValue
import io.github.thelony97.orbit.data.newId

/** A new contact from an unknown number (PLAN 3.12 „Danach“), filled with what Herold knew (PLAN 9 B1). */
object HeroldDraft {
    fun of(number: String, org: Boolean, hit: Herold.Hit?): Contact {
        val base = Contact(
            id = newId(),
            kind = if (org) ContactKind.ORG else ContactKind.PERSON,
            phones = listOf(LabeledValue(if (org) "Zentrale" else "Mobil", number)),
        )
        if (hit == null) return base
        val withName = if (org) {
            base.copy(company = hit.name)
        } else {
            // A person's entry: „Huber Maria“ in the phone book order is not known for sure –
            // the last word becomes the family name, as everywhere else in Orbit.
            val words = hit.name.split(' ').filter { it.isNotBlank() }
            base.copy(firstName = words.dropLast(1).joinToString(" ").ifBlank { words.firstOrNull().orEmpty() }, lastName = if (words.size > 1) words.last() else "")
        }
        val withAddress = if (hit.address.isNotBlank()) withName.copy(addresses = listOf(LabeledValue(if (org) "Arbeit" else "Privat", hit.address))) else withName
        // The trade („Installateur“) as the first note line until D5 gives it its own field.
        return if (hit.category.isNotBlank()) withAddress.copy(notes = hit.category) else withAddress
    }
}
