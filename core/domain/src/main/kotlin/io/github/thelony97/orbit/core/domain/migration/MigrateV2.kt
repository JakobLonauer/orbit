package io.github.thelony97.orbit.core.domain.migration

import io.github.thelony97.orbit.core.model.OrbitDoc
import io.github.thelony97.orbit.core.model.OrgLink
import io.github.thelony97.orbit.core.model.SCHEMA_V3

/**
 * Schema 2 -> 3 (PLAN 3.6 „Bestehende Org-Beziehungen werden zu Zuordnungen“): every relation
 * between a person and an organisation becomes an [OrgLink] with the matching role; everything
 * else stays exactly as it is. Pure and idempotent – running it on schema 3 changes nothing.
 *
 * Nothing is dropped: relations with a missing side, between two organisations or between two
 * people stay relations. The caller keeps a copy of the schema-2 file before writing (StoreUpgrade).
 */
object MigrateV2 {

    fun upgrade(doc: OrbitDoc): OrbitDoc {
        val byId = doc.contacts.associateBy { it.id }
        val moved = ArrayList<OrgLink>()
        val kept = doc.relations.filter { r ->
            val from = byId[r.fromId]
            val to = byId[r.toId]
            if (from == null || to == null || from.isOrg == to.isOrg) return@filter true
            // „<from> ist <kind> von <to>“: the person may stand on either side.
            val (person, org) = if (to.isOrg) from to to else to to from
            val (role, label) = MigrateV1.roleFor(r.kind)
            moved += OrgLink(id = r.id, personId = person.id, orgId = org.id, role = role, label = label)
            false
        }
        val links = (doc.orgLinks + moved).distinctBy { Triple(it.personId, it.orgId, it.role + "\u0000" + it.label) }
        return doc.copy(schema = SCHEMA_V3, relations = kept, orgLinks = links)
    }
}
