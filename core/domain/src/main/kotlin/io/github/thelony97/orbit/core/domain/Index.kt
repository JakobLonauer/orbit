package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.HistoryEntry
import io.github.thelony97.orbit.core.model.Occasion
import io.github.thelony97.orbit.core.model.OrbitDoc
import io.github.thelony97.orbit.core.model.Repeat
import java.text.Normalizer
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Search folding: lower case, no diacritics, ß -> ss. "Müller" and "muller" meet here. */
object Fold {
    private val marks = Regex("\\p{Mn}+")
    fun fold(s: String): String =
        Normalizer.normalize(s.lowercase().replace("ß", "ss"), Normalizer.Form.NFD).replace(marks, "")
}

/** "When did I last …?" per contact and entry type (PLAN 3.4; no due dates, only facts). */
data class LastContact(val byType: Map<String, Long>, val any: Long?)

object LastContactIndex {
    fun build(history: Sequence<HistoryEntry>): Map<String, LastContact> {
        val byContact = HashMap<String, HashMap<String, Long>>()
        for (e in history) {
            if (e.memberId != null) continue // member talks belong to the member, not the org row
            val m = byContact.getOrPut(e.contactId) { HashMap() }
            val prev = m[e.typeId]
            if (prev == null || e.at > prev) m[e.typeId] = e.at
        }
        return byContact.mapValues { (_, m) -> LastContact(m, m.values.maxOrNull()) }
    }
}

object SearchText {
    /** Everything findable for one contact, folded. Hidden members are deliberately absent. */
    fun of(c: Contact, orgNames: List<String> = emptyList()): String = buildString {
        fun add(s: String) { if (s.isNotBlank()) append(Fold.fold(s)).append('\n') }
        add(c.firstName); add(c.lastName); add(c.company); add(c.position); add(c.trade)
        add(c.notes); add(c.pinnedNote); add(c.howWeMet)
        c.phones.forEach { add(it.value); add(it.value.filter(Char::isDigit)) }
        c.emails.forEach { add(it.value) }
        c.addresses.forEach { add(it.value) }
        c.fields.forEach { add(it.label); add(it.value) }
        c.links.forEach { add(it.title) }
        c.topics.forEach { add(it.text) }
        orgNames.forEach(::add)
    }

    /** Org names each person is assigned to ("gehört zu …" in search). */
    fun orgNamesByPerson(doc: OrbitDoc): Map<String, List<String>> {
        val names = doc.contacts.filter { it.isOrg }.associate { it.id to CallerIndex.nameOf(it) }
        return doc.orgLinks.groupBy({ it.personId }, { names[it.orgId].orEmpty() })
    }
}

/** One upcoming occurrence of an [Occasion]. [turning] = age / count reached, if the year is known. */
data class Upcoming(
    val ownerId: String,
    val withId: String?,
    val occasion: Occasion,
    val date: LocalDate,
    val daysUntil: Long,
    val turning: Int?,
)

object UpcomingIndex {
    /**
     * Next occurrences on or after [today] for contacts allowed by [visible], soonest first.
     * Yearly ones always have a next date; one-off ones only until their day has passed.
     * A shared occasion shows up once, at its owner, carrying [Upcoming.withId].
     */
    fun build(contacts: List<Contact>, today: LocalDate, visible: (Contact) -> Boolean): List<Upcoming> {
        val ok = contacts.filter(visible).associateBy { it.id }
        val out = ArrayList<Upcoming>()
        for (c in ok.values) for (o in c.dates) {
            val withId = o.sharedWith?.takeIf { it in ok }
            val date = when (o.repeat) {
                Repeat.YEARLY -> DateMath.nextOccurrence(o.date, today)
                Repeat.ONCE -> o.date.year?.let { DateMath.inYear(o.date, it) }?.takeIf { !it.isBefore(today) }
            } ?: continue
            val turning = if (o.repeat == Repeat.YEARLY) o.date.year?.let { date.year - it } else null
            out += Upcoming(c.id, withId, o, date, ChronoUnit.DAYS.between(today, date), turning)
        }
        out.sortWith(compareBy({ it.date }, { it.ownerId }, { it.occasion.id }))
        return out
    }
}
