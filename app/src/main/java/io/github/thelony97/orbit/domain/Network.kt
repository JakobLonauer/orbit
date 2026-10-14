package io.github.thelony97.orbit.domain

import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.Defaults
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.Relation
import kotlin.math.PI

data class NetNode(
    val contact: Contact,
    val level: Int,
    val parentId: String?,
    /** Relation to the parent (null for the root). */
    val via: Relation?,
    /** Angle in radians around the root. */
    val angle: Double,
)

data class NetEdge(val a: String, val b: String, val relation: Relation, val tree: Boolean)

data class Network(
    val root: Contact,
    val nodes: List<NetNode>,
    val edges: List<NetEdge>,
    val maxLevel: Int,
    /** People/organisations that have no relation at all (not reachable from anywhere). */
    val isolated: Int,
)

/**
 * Ego network: breadth-first search from a root up to [depth] steps, laid out radially.
 * Every contact appears once (first path wins); further links become cross edges.
 */
object NetworkLayout {

    private const val ORG_KIND = "org:"

    /**
     * Relations plus Zuordnungen (PLAN 3.6) as one list of links: a Zuordnung is drawn like a
     * relation from the person to the organisation; [roleOf] reads its role from both sides.
     */
    fun links(data: OrbitData): List<Relation> =
        data.relations + data.orgLinks.map { Relation(it.id, it.personId, it.orgId, ORG_KIND + it.role + ":" + it.label) }

    fun build(data: OrbitData, root: Contact, depth: Int, include: (Contact) -> Boolean, lastFirst: Boolean = false): Network {
        val contacts = Logic.active(data).filter { it.id == root.id || include(it) }.associateBy { it.id }
        val adjacency = HashMap<String, MutableList<Pair<String, Relation>>>()
        val links = links(data)
        links.forEach { r ->
            if (r.fromId in contacts && r.toId in contacts && r.fromId != r.toId) {
                adjacency.getOrPut(r.fromId) { mutableListOf() } += r.toId to r
                adjacency.getOrPut(r.toId) { mutableListOf() } += r.fromId to r
            }
        }
        val level = HashMap<String, Int>()
        val parent = HashMap<String, Pair<String, Relation>>()
        val children = HashMap<String, MutableList<String>>()
        val order = ArrayList<String>()
        level[root.id] = 0
        order += root.id
        var i = 0
        while (i < order.size) {
            val id = order[i++]
            val l = level.getValue(id)
            if (l >= depth) continue
            adjacency[id].orEmpty()
                .sortedBy { (other, _) -> contacts[other]?.let { Logic.sortKey(it, lastFirst) } ?: "" }
                .forEach { (other, rel) ->
                    if (other !in level) {
                        level[other] = l + 1
                        parent[other] = id to rel
                        children.getOrPut(id) { mutableListOf() } += other
                        order += other
                    }
                }
        }

        // Subtree weight = number of leaves, so busy branches get more room.
        val weight = HashMap<String, Int>()
        for (id in order.asReversed()) {
            weight[id] = children[id]?.sumOf { weight.getValue(it) }?.coerceAtLeast(1) ?: 1
        }
        val angle = HashMap<String, Double>()
        angle[root.id] = 0.0
        fun assign(id: String, start: Double, span: Double) {
            val kids = children[id] ?: return
            val total = kids.sumOf { weight.getValue(it) }.toDouble()
            var a = start
            kids.forEach { k ->
                val s = span * weight.getValue(k) / total
                angle[k] = a + s / 2
                assign(k, a, s)
                a += s
            }
        }
        assign(root.id, -PI / 2, 2 * PI)

        val nodes = order.map { id ->
            NetNode(
                contact = contacts.getValue(id),
                level = level.getValue(id),
                parentId = parent[id]?.first,
                via = parent[id]?.second,
                angle = angle[id] ?: 0.0,
            )
        }
        val treeRelations = parent.values.map { it.second.id }.toSet()
        val edges = links
            .filter { it.fromId in level && it.toId in level && it.fromId != it.toId }
            .map { NetEdge(it.fromId, it.toId, it, tree = it.id in treeRelations) }
        val linked = links.flatMap { listOf(it.fromId, it.toId) }.toSet()
        val isolated = contacts.values.count { !it.isMe && it.id !in linked }
        return Network(root, nodes, edges, nodes.maxOfOrNull { it.level } ?: 0, isolated)
    }

    /** How [other] relates to [self] through [r] – e.g. "Partner:in", "Kind", "Arbeitgeber". */
    fun roleOf(other: String, r: Relation): String {
        if (r.kind.startsWith(ORG_KIND)) {
            val (role, label) = r.kind.removePrefix(ORG_KIND).split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            val ar = io.github.thelony97.orbit.core.model.AssignmentRoles.of(io.github.thelony97.orbit.core.model.OrgLink(r.id, r.fromId, r.toId, role, label))
            // The person stands on the „from“ side.
            return if (r.fromId == other) ar.personSide else io.github.thelony97.orbit.core.domain.Assignments.label(ar)
        }
        val kind = Defaults.relationKind(r.kind)
        return if (r.fromId == other) kind.forward else kind.backward
    }
}
