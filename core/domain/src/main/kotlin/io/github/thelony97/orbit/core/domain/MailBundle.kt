package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.BuiltinTypes
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.EntrySource
import io.github.thelony97.orbit.core.model.HistoryEntry
import io.github.thelony97.orbit.core.model.MailRef
import java.time.Instant
import java.time.ZoneId

/**
 * E-Mail v2 (PLAN 3.9): mails become Verlauf entries, one per contact, day and direction
 * („3 E-Mails empfangen“ with the subjects). Pure: the sync hands in headers, this says which
 * entries to add or change. A hidden member's mails stay apart (their own entry at the org).
 */
object MailBundle {

    /** A fetched header, before it is known whose it is. */
    data class Header(
        val account: String,
        val folder: String,
        val uidValidity: Long,
        val uid: Long,
        val messageId: String?,
        val at: Long,
        val from: String,
        val to: List<String>,
        val subject: String,
    )

    data class Owner(val contactId: String, val memberId: String?)

    fun externalId(day: java.time.LocalDate, incoming: Boolean, memberId: String?): String =
        "mail:$day:" + (if (incoming) "in" else "out") + (memberId?.let { ":$it" } ?: "")

    /**
     * Who a header belongs to: an incoming mail to its sender, an outgoing one (sent from one of
     * [own] addresses) to every recipient Orbit knows. [match] is the Zuordnungs-Maschine.
     */
    fun owners(h: Header, own: Set<String>, match: (String) -> Owner?): List<Pair<Owner, MailRef>> {
        val from = h.from.trim().lowercase()
        val outgoing = from in own
        val sides = if (outgoing) h.to.map { it.trim().lowercase() }.filter { it.isNotEmpty() && it !in own } else listOf(from)
        return sides.mapNotNull { address ->
            val owner = match(address) ?: return@mapNotNull null
            owner to MailRef(
                account = h.account, folder = h.folder, uidValidity = h.uidValidity, uid = h.uid, at = h.at,
                incoming = !outgoing, subject = h.subject.ifBlank { "(kein Betreff)" }, address = address, messageId = h.messageId,
            )
        }.distinctBy { it.first }
    }

    /**
     * New refs for one contact merged into its entries: returns only entries that are new or
     * changed. A mail already in any entry of the contact (same [MailRef.key]) is skipped.
     */
    fun merge(contact: Contact, refs: List<Pair<String?, MailRef>>, zone: ZoneId, newId: () -> String): List<HistoryEntry> {
        val known = contact.entries.flatMap { e -> e.mails.map { it.key } }.toHashSet()
        // A known mail seen again in the same folder under a new UIDVALIDITY/uid: only its
        // address on the server moves, so „öffnen“ finds the right mail again.
        val where = HashMap<String, HistoryEntry>()
        contact.entries.forEach { e -> e.mails.forEach { where[it.key] = e } }
        val moved = LinkedHashMap<String, HistoryEntry>()
        refs.forEach { (_, r) ->
            val e = moved[where[r.key]?.id] ?: where[r.key] ?: return@forEach
            val old = e.mails.firstOrNull { it.key == r.key } ?: return@forEach
            if (old.account == r.account && old.folder == r.folder && (old.uidValidity != r.uidValidity || old.uid != r.uid)) {
                moved[e.id] = e.copy(mails = e.mails.map { if (it.key == r.key) it.copy(uidValidity = r.uidValidity, uid = r.uid) else it })
            }
        }
        val fresh = refs.filter { (_, r) -> known.add(r.key) }
        if (fresh.isEmpty()) return moved.values.toList()
        val byExt = contact.entries.filter { it.externalId != null }.associateBy { it.externalId }.toMutableMap()
        moved.values.forEach { e -> e.externalId?.let { byExt[it] = e } }
        val changed = LinkedHashMap<String, HistoryEntry>()
        moved.values.forEach { e -> e.externalId?.let { changed[it] = e } }
        for ((memberId, r) in fresh.sortedBy { it.second.at }) {
            val day = Instant.ofEpochMilli(r.at).atZone(zone).toLocalDate()
            val ext = externalId(day, r.incoming, memberId)
            val old = changed[ext] ?: byExt[ext]
            val next = if (old == null) {
                HistoryEntry(
                    id = newId(), contactId = contact.id, typeId = BuiltinTypes.EMAIL, at = r.at,
                    source = EntrySource.MAIL, incoming = r.incoming, externalId = ext, memberId = memberId, mails = listOf(r),
                )
            } else {
                val mails = (old.mails + r).sortedByDescending { it.at }
                old.copy(mails = mails, at = maxOf(old.at, r.at))
            }
            changed[ext] = next
            byExt[ext] = next
        }
        return changed.values.toList()
    }

    /** „3 E-Mails empfangen“, „E-Mail gesendet“. */
    fun title(e: HistoryEntry): String {
        val n = e.mails.size
        val what = if (n == 1) "E-Mail" else "$n E-Mails"
        return what + if (e.incoming == false) " gesendet" else " empfangen"
    }
}
