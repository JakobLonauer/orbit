package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact

/**
 * Zuordnungs-Maschine (PLAN 4): who is behind a number or a mail address?
 *
 * Order: exact number → hidden member's number → longest number range of an organisation
 * → exact mail → mail domain. Archived contacts and hidden members ARE recognised (caller ID);
 * the result carries flags so lists and search can drop them.
 *
 * [normalize] turns raw input into a comparable key (E.164 on the phone); injected so this
 * stays pure and testable.
 */
class Matcher(
    contacts: List<Contact>,
    private val normalize: (String) -> String?,
) {
    enum class Via { NUMBER, MEMBER, PREFIX, EMAIL, DOMAIN }

    data class Match(
        val contactId: String,
        val via: Via,
        val memberId: String? = null,
        val archived: Boolean = false,
        val org: Boolean = false,
    ) {
        /** Lists, search and the dial-pad hint never show these; caller ID does. */
        val hiddenFromLists: Boolean get() = archived || memberId != null
    }

    private class Owner(val contact: Contact, val memberId: String?)

    private val byNumber = HashMap<String, Owner>()
    private val byTail = HashMap<String, MutableList<Owner>>()
    private val byMemberNumber = HashMap<String, Owner>()
    private val prefixes = ArrayList<Pair<String, Contact>>()
    private val byMail = HashMap<String, Owner>()
    private val byMemberMail = HashMap<String, Owner>()
    private val domains = ArrayList<Pair<String, Contact>>()

    init {
        // Active people first so that on a shared number they win over orgs and archived ones.
        val live = contacts.filter { it.deletedAt == null && !it.isMe }
            .sortedWith(compareBy({ it.archived }, { it.isOrg }))
        for (c in live) {
            for (p in c.phones) {
                val n = normalize(p.value) ?: continue
                byNumber.putIfAbsent(n, Owner(c, null))
                byTail.getOrPut(tail(n)) { ArrayList() }.add(Owner(c, null))
            }
            for (e in c.emails) mailKey(e.value)?.let { byMail.putIfAbsent(it, Owner(c, null)) }
            if (!c.isOrg) continue
            for (m in c.members) {
                for (p in m.phones) normalize(p.value)?.let { byMemberNumber.putIfAbsent(it, Owner(c, m.id)) }
                for (e in m.emails) mailKey(e.value)?.let { byMemberMail.putIfAbsent(it, Owner(c, m.id)) }
            }
            for (p in c.numberPrefixes) prefixKey(p)?.let { prefixes += it to c }
            for (d in c.emailDomains) domainKey(d)?.let { domains += it to c }
        }
        prefixes.sortByDescending { it.first.length }
        domains.sortByDescending { it.first.length }
    }

    fun matchNumber(raw: String?): Match? {
        if (raw.isNullOrBlank()) return null
        val n = normalize(raw) ?: return null
        byNumber[n]?.let { return it.toMatch(Via.NUMBER) }
        val t = tail(n)
        if (t.length >= 6) byTail[t]?.distinctBy { it.contact.id }?.singleOrNull()?.let { return it.toMatch(Via.NUMBER) }
        byMemberNumber[n]?.let { return it.toMatch(Via.MEMBER) }
        prefixes.firstOrNull { n.startsWith(it.first) && n.length > it.first.length }
            ?.let { return Owner(it.second, null).toMatch(Via.PREFIX) }
        return null
    }

    fun matchMail(raw: String?): Match? {
        val key = raw?.let { mailKey(it) } ?: return null
        byMail[key]?.let { return it.toMatch(Via.EMAIL) }
        byMemberMail[key]?.let { return it.toMatch(Via.MEMBER) }
        val host = key.substringAfter('@')
        domains.firstOrNull { host == it.first || host.endsWith("." + it.first) }
            ?.let { return Owner(it.second, null).toMatch(Via.DOMAIN) }
        return null
    }

    private fun Owner.toMatch(via: Via) = Match(contact.id, via, memberId, contact.archived, contact.isOrg)

    /**
     * The Durchwahl of [raw] in one of [prefixes] („0732 6596“ and „0732 6596 410“ → „410“), with
     * the same padding rule as the range match; null when no prefix fits.
     */
    fun extension(raw: String?, prefixes: List<String>): String? {
        val n = raw?.let(normalize) ?: return null
        return prefixes.mapNotNull { prefixKey(it) }.sortedByDescending { it.length }
            .firstOrNull { n.startsWith(it) && n.length > it.length }
            ?.let { n.substring(it.length) }
    }

    /** E.164 of a partial number: pad, normalise, cut the padding off again. */
    private fun prefixKey(p: String): String? {
        val digits = p.count { it.isDigit() }
        if (digits < 3) return null
        return normalize(p.trim() + PAD)?.takeIf { it.endsWith(PAD) }?.dropLast(PAD.length)
    }

    companion object {
        private const val PAD = "0000"

        fun tail(n: String): String = n.filter { it.isDigit() }.takeLast(9)

        fun mailKey(raw: String): String? {
            val m = raw.trim().lowercase()
            val at = m.indexOf('@')
            return if (at <= 0 || at == m.lastIndex) null else m
        }

        fun domainKey(raw: String): String? =
            raw.trim().lowercase().removePrefix("@").removePrefix("*.").takeIf { it.contains('.') }
    }
}
