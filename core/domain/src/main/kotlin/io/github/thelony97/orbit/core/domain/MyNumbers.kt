package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.OrbitDoc

/**
 * Nummern-Schublade (PLAN 9 A9): the numbers Jakob reads out or types into forms – the own
 * fields of his ICH card (SV-Nr., Polizzen, Matrikel, IBAN, Kennzeichen …) and the customer
 * numbers at his services (organisations ICH is assigned to).
 */
object MyNumbers {

    /** [owner]: the organisation for a service's number, null for his own. */
    data class Item(val owner: String?, val label: String, val value: String)

    fun of(doc: OrbitDoc): List<Item> {
        val me = doc.contacts.firstOrNull { it.isMe } ?: return emptyList()
        val own = me.fields.filter { it.value.isNotBlank() }.map { Item(null, it.label.ifBlank { "Nummer" }, it.value.trim()) }
        val orgIds = doc.orgLinks.filter { it.personId == me.id }.map { it.orgId }.distinct()
        val services = orgIds
            .mapNotNull { id -> doc.contacts.firstOrNull { it.id == id && it.deletedAt == null && !it.archived } }
            .sortedBy { CallerIndex.nameOf(it).lowercase() }
            .flatMap { org -> org.fields.filter { it.value.isNotBlank() }.map { Item(CallerIndex.nameOf(org), it.label.ifBlank { "Nummer" }, it.value.trim()) } }
        return own + services
    }
}
