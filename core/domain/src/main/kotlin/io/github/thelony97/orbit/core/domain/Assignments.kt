package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.AssignmentRole
import io.github.thelony97.orbit.core.model.AssignmentRoles
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.OrbitDoc

/**
 * Zuordnungen (PLAN 3.6): person → organisation with a role, read from both sides. The person page
 * shows „Hausarzt: Arzt Müller“, the organisation page „Anna · Patient:in“. [visible] decides which
 * other side may show (callers: not deleted, not archived).
 */
object Assignments {

    data class Row(val linkId: String, val otherId: String, val role: String)

    /** What the organisation is to the person; „Team“/„Projekt“ say more from the person's side. */
    fun label(r: AssignmentRole): String =
        if (r.orgSide.isBlank() || r.orgSide == "Team" || r.orgSide == "Projekt") r.personSide else r.orgSide

    fun ofPerson(doc: OrbitDoc, personId: String, visible: (Contact) -> Boolean): List<Row> {
        val byId = doc.contacts.associateBy { it.id }
        return doc.orgLinks.filter { it.personId == personId }.mapNotNull { l ->
            val org = byId[l.orgId]?.takeIf { it.isOrg && visible(it) } ?: return@mapNotNull null
            Row(l.id, org.id, label(AssignmentRoles.of(l)))
        }
    }

    fun ofOrg(doc: OrbitDoc, orgId: String, visible: (Contact) -> Boolean): List<Row> {
        val byId = doc.contacts.associateBy { it.id }
        return doc.orgLinks.filter { it.orgId == orgId }.mapNotNull { l ->
            val person = byId[l.personId]?.takeIf { !it.isOrg && visible(it) } ?: return@mapNotNull null
            Row(l.id, person.id, AssignmentRoles.of(l).personSide)
        }
    }

    /** „Auch im COOL Lab: Anna, Tom“: for each organisation of [personId], the other people there. */
    fun alsoThere(doc: OrbitDoc, personId: String, visible: (Contact) -> Boolean): List<Pair<String, List<String>>> {
        val byId = doc.contacts.associateBy { it.id }
        val orgs = ofPerson(doc, personId, visible).map { it.otherId }.distinct()
        return orgs.mapNotNull { orgId ->
            val others = doc.orgLinks.filter { it.orgId == orgId && it.personId != personId }
                .mapNotNull { l -> byId[l.personId]?.takeIf { !it.isOrg && !it.isMe && visible(it) }?.id }
                .distinct()
            if (others.isEmpty()) null else orgId to others
        }
    }
}
