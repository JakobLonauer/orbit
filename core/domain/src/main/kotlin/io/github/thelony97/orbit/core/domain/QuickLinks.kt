package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.Link

/**
 * Schnellzugriff (PLAN 3.7): every pinned link, also of organisations while those are hidden;
 * never of archived or deleted contacts. Order: organisations first, then by name, then as on the page.
 */
object QuickLinks {
    data class Item(val ownerId: String, val link: Link)

    fun pinned(contacts: List<Contact>, name: (Contact) -> String = { it.id }): List<Item> =
        contacts.filter { it.deletedAt == null && !it.archived && it.links.any { l -> l.pinned } }
            .sortedWith(compareBy<Contact>({ !it.isOrg }, { name(it).lowercase() }))
            .flatMap { c -> c.links.filter { it.pinned }.map { Item(c.id, it) } }
}
