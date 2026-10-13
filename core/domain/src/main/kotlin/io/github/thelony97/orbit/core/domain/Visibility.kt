package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact

/**
 * Where a contact may appear (PLAN 3.6, 3.11). Hidden members are not contacts at all
 * and never reach these lists. Caller ID ignores these rules (see [Matcher]).
 */
object Visibility {
    fun inTrash(c: Contact): Boolean = c.deletedAt != null
    fun inArchive(c: Contact): Boolean = c.deletedAt == null && c.archived

    /** Contact list, search, Verlauf tab, Anlässe, net, map. */
    fun inLists(c: Contact, showOrgs: Boolean): Boolean =
        c.deletedAt == null && !c.archived && (showOrgs || !c.isOrg)
}
