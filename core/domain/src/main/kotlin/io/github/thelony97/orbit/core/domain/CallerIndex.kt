package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.AssignmentRoles
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.OrbitDoc
import kotlinx.serialization.Serializable

/** What the call screen shows the moment it rings (PLAN 3.12). Small, no history. */
@Serializable
data class CallerCard(
    val contactId: String,
    val name: String,
    val photo: String? = null,
    /** "Hausarzt · Arzt Müller", "Empfang", "Sachbearbeiterin · LiLES" … */
    val role: String = "",
    val note: String = "",
    val customerNo: String = "",
    val archived: Boolean = false,
    val memberId: String? = null,
    val org: Boolean = false,
    /** Last contact (PLAN 3.12 „zuletzt“): time and kind name; the screen turns it into "vor 3 T". */
    val lastAt: Long? = null,
    val lastKind: String = "",
    /** Open "Nächstes Mal ansprechen" topics, a few at most. */
    val topics: List<String> = emptyList(),
    /** Birthday (persons); the screen turns it into „Morgen Geburtstag · wird 30“ (PLAN 3.12 „anstehender Anlass“). */
    val birthday: io.github.thelony97.orbit.core.model.CalendarDay? = null,
)

@Serializable
data class CallerPrefix(val prefix: String, val card: CallerCard)

/**
 * Anrufer-Kurzindex (PLAN 4): written as a small file on every save, read on ring without
 * loading the whole document. Same order as [Matcher]: exact → member → range.
 */
@Serializable
data class CallerIndex(
    /** Settings switch „Anrufer-Info beim Klingeln“, copied here so ringing needs no full load. */
    val callerInfo: Boolean = true,
    val exact: Map<String, CallerCard> = emptyMap(),
    val members: Map<String, CallerCard> = emptyMap(),
    val prefixes: List<CallerPrefix> = emptyList(),
) {
    fun lookup(raw: String, normalize: (String) -> String?): CallerCard? {
        val n = normalize(raw) ?: return null
        exact[n]?.let { return it }
        val t = Matcher.tail(n)
        if (t.length >= 6) exact.entries.filter { Matcher.tail(it.key) == t }
            .map { it.value }.distinctBy { it.contactId }.singleOrNull()?.let { return it }
        members[n]?.let { return it }
        return prefixes.firstOrNull { n.startsWith(it.prefix) && n.length > it.prefix.length }?.card
    }

    companion object {
        private const val NOTE_CHARS = 120
        private const val TOPICS = 5

        fun build(doc: OrbitDoc, normalize: (String) -> String?): CallerIndex {
            val byId = doc.contacts.associateBy { it.id }
            val live = doc.contacts.filter { it.deletedAt == null && !it.isMe }
                .sortedWith(compareBy({ it.archived }, { it.isOrg }))
            val exact = LinkedHashMap<String, CallerCard>()
            val members = LinkedHashMap<String, CallerCard>()
            val prefixes = ArrayList<CallerPrefix>()

            val kinds = doc.entryTypes.associate { it.id to it.name }
            for (c in live) {
                val last = c.entries.filter { it.memberId == null }.maxByOrNull { it.at }
                val base = CallerCard(
                    contactId = c.id,
                    name = nameOf(c),
                    photo = c.photo,
                    role = if (c.isOrg) "" else personRole(c, doc, byId),
                    note = (c.pinnedNote.ifBlank { c.notes }).trim().take(NOTE_CHARS),
                    customerNo = customerNo(c),
                    archived = c.archived,
                    org = c.isOrg,
                    lastAt = last?.at,
                    lastKind = last?.let { kinds[it.typeId] }.orEmpty(),
                    topics = if (doc.settings.topicsDuringCall) c.topics.filterNot { it.done }.map { it.text }.take(TOPICS) else emptyList(),
                    birthday = if (c.isOrg) null else c.birthday,
                )
                for (p in c.phones) {
                    val n = normalize(p.value) ?: continue
                    // For organisations the number's label is the useful role ("Empfang").
                    exact.putIfAbsent(n, if (c.isOrg) base.copy(role = p.label) else base)
                }
                if (!c.isOrg) continue
                for (m in c.members) {
                    val talk = c.entries.filter { it.memberId == m.id }.maxByOrNull { it.at }
                    val card = CallerCard(
                        contactId = c.id,
                        name = m.name,
                        photo = m.photo,
                        role = listOf(m.role, nameOf(c)).filter { it.isNotBlank() }.joinToString(" · "),
                        note = m.note.trim().take(NOTE_CHARS),
                        customerNo = base.customerNo,
                        archived = c.archived,
                        memberId = m.id,
                        org = true,
                        lastAt = talk?.at,
                        lastKind = talk?.let { kinds[it.typeId] }.orEmpty(),
                    )
                    for (p in m.phones) normalize(p.value)?.let { members.putIfAbsent(it, card) }
                }
                for (p in c.numberPrefixes) {
                    val digits = p.count { it.isDigit() }
                    if (digits < 3) continue
                    normalize(p.trim() + "0000")?.takeIf { it.endsWith("0000") }?.dropLast(4)
                        ?.let { prefixes += CallerPrefix(it, base) }
                }
            }
            prefixes.sortByDescending { it.prefix.length }
            return CallerIndex(doc.settings.callerInfo, exact, members, prefixes)
        }

        /** Organisations keep their name in `company` (edit page, 1.0.7 data); older test data in firstName. */
        fun nameOf(c: Contact): String {
            val person = listOf(c.firstName, c.lastName).filter { it.isNotBlank() }.joinToString(" ")
            return if (c.isOrg) c.company.trim().ifBlank { person } else person.ifBlank { c.company.trim() }
        }

        private fun personRole(c: Contact, doc: OrbitDoc, byId: Map<String, Contact>): String {
            val link = doc.orgLinks.firstOrNull { it.personId == c.id && byId[it.orgId]?.deletedAt == null }
            if (link != null) {
                val org = byId[link.orgId]?.let { nameOf(it) }.orEmpty()
                return listOf(AssignmentRoles.of(link).personSide, org).filter { it.isNotBlank() }.joinToString(" · ")
            }
            return listOf(c.position, c.company).filter { it.isNotBlank() }.joinToString(" · ")
        }

        private fun customerNo(c: Contact): String =
            c.fields.firstOrNull { f -> CUSTOMER_LABELS.any { it in f.label.lowercase() } }?.value.orEmpty()

        private val CUSTOMER_LABELS = listOf("kunden", "kd.-nr", "kdnr", "versicherungsn", "mitglieds", "patienten")
    }
}
