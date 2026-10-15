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
    /** First group colour (ARGB), so the caller has the same colour as in the list. */
    val color: Long? = null,
    /** PLAN 9 B7 „Familie im Blick“: partner and children, with birthdays for „wird 7 am Freitag“. */
    val family: List<FamilyMember> = emptyList(),
    /** PLAN 9 B7: how to address them – „Sie · Frau Mag.a Huber“, „Du · Hansi“; empty when nothing special. */
    val anrede: String = "",
    /** PLAN 9 B11: the Anruf-Poster's picture (a gallery file); null = the profile photo. */
    val poster: String? = null,
)

/** PLAN 9 B7: a partner („partner“) or a child („child“) of the caller. */
@Serializable
data class FamilyMember(val name: String, val kind: String, val birthday: io.github.thelony97.orbit.core.model.CalendarDay? = null)

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
    /** PLAN 9 B8: the call filter's rules, copied here so screening needs no full load; null = off. */
    val screening: Screening.Rules? = null,
    /** PLAN 9 B12: motion gestures during calls (turn over = silent, on the table = speaker). */
    val gestures: Boolean = false,
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

    /**
     * PLAN 9 B2: the Durchwahl when [raw] is known only through an organisation's number range
     * („Zentrale · Durchwahl 410“); null for exact numbers and members.
     */
    fun extension(raw: String, normalize: (String) -> String?): String? {
        val n = normalize(raw) ?: return null
        if (exact.containsKey(n) || members.containsKey(n)) return null
        val t = Matcher.tail(n)
        if (t.length >= 6 && exact.keys.any { Matcher.tail(it) == t }) return null
        return prefixes.firstOrNull { n.startsWith(it.prefix) && n.length > it.prefix.length }?.let { n.substring(it.prefix.length) }
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
            val groupColors = doc.groups.associate { it.id to it.color }
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
                    color = c.groupIds.firstNotNullOfOrNull { groupColors[it] },
                    family = if (c.isOrg) emptyList() else familyOf(c, doc, byId),
                    anrede = if (c.isOrg) "" else anrede(c),
                    poster = if (c.isOrg) null else c.poster,
                )
                for (p in c.phones) {
                    val n = normalize(p.value) ?: continue
                    // For organisations the number's label is the useful role ("Empfang").
                    exact.putIfAbsent(n, if (c.isOrg) base.copy(role = p.label) else base)
                }
                if (!c.isOrg) continue
                addMembers(c, base, kinds, normalize, members)
                for (p in c.numberPrefixes) {
                    val digits = p.count { it.isDigit() }
                    if (digits < 3) continue
                    normalize(p.trim() + "0000")?.takeIf { it.endsWith("0000") }?.dropLast(4)
                        ?.let { prefixes += CallerPrefix(it, base) }
                }
            }
            prefixes.sortByDescending { it.prefix.length }
            val s = doc.settings
            return CallerIndex(s.callerInfo, exact, members, prefixes, rulesOf(s), s.motionGestures)
        }

        /** Hidden members of an organisation by their own numbers (PLAN 3.6). */
        private fun addMembers(
            c: Contact,
            base: CallerCard,
            kinds: Map<String, String>,
            normalize: (String) -> String?,
            members: MutableMap<String, CallerCard>,
        ) {
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
        }

        /** PLAN 9 B8: the filter's rules while it is on; series kept as typed (see Screening.prefix). */
        private fun rulesOf(s: io.github.thelony97.orbit.core.model.Settings): Screening.Rules? = if (!s.screening) null else Screening.Rules(
            night = s.screenNight,
            premium = s.screenPremium,
            countries = s.screenCountries.map { it.trim().uppercase() }.filter { it.length == 2 },
            series = s.screenSeries.map { it.trim() }.filter { p -> p.count(Char::isDigit) >= 3 },
        )

        private val PARTNER_KINDS = setOf("partner", "spouse")

        /** Partners (either side) and children („<c> ist Elternteil von <x>“), visible ones only. */
        private fun familyOf(c: Contact, doc: OrbitDoc, byId: Map<String, Contact>): List<FamilyMember> {
            fun ok(id: String) = byId[id]?.takeIf { it.deletedAt == null && !it.archived && !it.isMe && !it.isOrg }
            fun short(x: Contact) = x.nickname.ifBlank { x.firstName }.ifBlank { nameOf(x) }
            val partners = doc.relations.filter { it.kind in PARTNER_KINDS && (it.fromId == c.id || it.toId == c.id) }
                .mapNotNull { ok(if (it.fromId == c.id) it.toId else it.fromId) }
                .distinctBy { it.id }.map { FamilyMember(short(it), "partner", it.birthday) }
            val children = doc.relations.filter { it.kind == "parent" && it.fromId == c.id }
                .mapNotNull { ok(it.toId) }
                .distinctBy { it.id }.map { FamilyMember(short(it), "child", it.birthday) }
            return partners + children
        }

        /**
         * PLAN 9 B7: „Sie · Frau Mag.a Huber“ for people Jakob says Sie to, „Du · Hansi“ when there is
         * a Rufname; empty otherwise (W5).
         */
        fun anrede(c: Contact): String = when {
            c.greeting?.formal == true -> "Sie · " + listOf(c.salutation, c.title, c.lastName.ifBlank { c.firstName })
                .filter { it.isNotBlank() }.joinToString(" ")
            c.nickname.isNotBlank() -> "Du · " + c.nickname
            else -> ""
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

        private fun customerNo(c: Contact): String = idField(c)?.value.orEmpty()

        private val CUSTOMER_LABELS = listOf("kunden", "kd.-nr", "kdnr", "versicherungsn", "mitglieds", "patienten", "karte", "ausweis", "polizz", "vertrag")

        /** PLAN 9 D4: the field that works as a card (Kunden-, Mitglieds-, Patientennummer …). */
        fun isIdLabel(label: String): Boolean = CUSTOMER_LABELS.any { it in label.lowercase() }

        fun idField(c: Contact): io.github.thelony97.orbit.core.model.CustomField? = c.fields.firstOrNull { f -> f.value.isNotBlank() && isIdLabel(f.label) }
    }
}
