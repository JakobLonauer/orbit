package io.github.thelony97.orbit.domain

import io.github.thelony97.orbit.core.domain.AtFormat
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.Entry
import io.github.thelony97.orbit.data.OrbitData
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Which kinds of contacts a list shows. People only is the default everywhere. */
enum class Audience(val label: String) {
    PEOPLE("Menschen"),
    ALL("Alle"),
    ORGS("Organisationen");

    fun accepts(c: Contact): Boolean = when (this) {
        PEOPLE -> !c.isOrg
        ALL -> true
        ORGS -> c.isOrg
    }
}

enum class StatusFilter(val label: String) {
    ALL("Alle"),
    FAVORITES("Favoriten"),
}

enum class SortMode(val key: String, val label: String, val reversedLabel: String) {
    NAME("name", "Name A–Z", "Name Z–A"),
    RECENT("recent", "Zuletzt Kontakt", "Am längsten her"),
    ADDED("added", "Neu hinzugefügt", "Älteste zuerst"),
    FREQUENT("frequent", "Häufigkeit", "Seltenste zuerst");

    fun label(reversed: Boolean) = if (reversed) reversedLabel else label

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: NAME
    }
}

data class ListSection(val title: String, val contacts: List<Contact>)

/** Builds the contact list: filter, search, sort and section headers. */
object ListQuery {

    fun lastEntry(c: Contact): Entry? = c.entries.maxByOrNull { it.at }

    /** Number of entries in the last 365 days. */
    fun frequency(c: Contact, today: LocalDate = today()): Int {
        val from = today.minusDays(365).startMillis()
        return c.entries.count { it.at >= from }
    }

    fun filter(
        data: OrbitData,
        idx: Index,
        audience: Audience,
        status: StatusFilter,
        groups: Set<String>,
        query: String,
        today: LocalDate = today(),
    ): List<Contact> = Logic.others(data).filter { c ->
        audience.accepts(c) &&
            when (status) {
                StatusFilter.ALL -> !c.archived
                StatusFilter.FAVORITES -> !c.archived && c.favorite
            } &&
            (groups.isEmpty() || c.groupIds.any { it in groups }) &&
            Logic.matches(c, query, idx)
    }

    /** PLAN 9 A11: same filters, but contacts that only sound like the query (Kölner Phonetik). */
    fun soundsLike(
        data: OrbitData,
        idx: Index,
        audience: Audience,
        status: StatusFilter,
        groups: Set<String>,
        query: String,
    ): List<Contact> {
        if (query.isBlank()) return emptyList()
        return Logic.others(data).filter { c ->
            audience.accepts(c) &&
                !c.archived &&
                (status != StatusFilter.FAVORITES || c.favorite) &&
                (groups.isEmpty() || c.groupIds.any { it in groups }) &&
                !Logic.matches(c, query, idx) &&
                Logic.soundsLike(c, query, idx)
        }
    }

    fun sections(
        contacts: List<Contact>,
        idx: Index,
        mode: SortMode,
        reversed: Boolean,
        lastFirst: Boolean,
        today: LocalDate = today(),
    ): List<ListSection> {
        val sorted: List<Contact> = when (mode) {
            SortMode.NAME -> contacts.sortedBy { Logic.sortKey(it, lastFirst) }
            SortMode.RECENT -> contacts.sortedWith(compareByDescending<Contact> { Logic.lastContactAt(it) ?: Long.MIN_VALUE }.thenBy { Logic.sortKey(it, lastFirst) })
            SortMode.ADDED -> contacts.sortedByDescending { it.createdAt }
            SortMode.FREQUENT -> contacts.sortedWith(compareByDescending<Contact> { frequency(it, today) }.thenBy { Logic.sortKey(it, lastFirst) })
        }.let { if (reversed) it.asReversed() else it }

        val header: (Contact) -> String = when (mode) {
            SortMode.NAME -> { c -> Logic.sectionLetter(c, lastFirst) }
            SortMode.RECENT -> { c -> recentBucket(Logic.lastContactAt(c), today) }
            SortMode.ADDED -> { c -> monthLabel(c.createdAt.takeIf { it > 0 }?.toLocalDate(), today) }
            SortMode.FREQUENT -> { c ->
                when (frequency(c, today)) {
                    0 -> "Kein Kontakt im letzten Jahr"
                    in 1..5 -> "Selten"
                    in 6..20 -> "Regelmäßig"
                    else -> "Sehr oft"
                }
            }
        }
        // Keep the sorted order, merge consecutive items with the same header.
        val out = ArrayList<ListSection>()
        sorted.forEach { c ->
            val h = header(c)
            if (out.isNotEmpty() && out.last().title == h) {
                out[out.lastIndex] = out.last().copy(contacts = out.last().contacts + c)
            } else {
                out += ListSection(h, listOf(c))
            }
        }
        if (mode == SortMode.NAME) {
            // Letters must stay unique even if the fold order differs from the letter order.
            return out.groupBy { it.title }
                .map { (t, l) -> ListSection(t, l.flatMap { it.contacts }) }
                .sortedBy { if (it.title == "#") 1 else 0 }
        }
        return out
    }

    private fun recentBucket(at: Long?, today: LocalDate): String {
        if (at == null) return "Noch nie"
        val days = ChronoUnit.DAYS.between(at.toLocalDate(), today)
        return when {
            days <= 0 -> "Heute"
            days == 1L -> "Gestern"
            days < 7 -> "Letzte 7 Tage"
            days < 31 -> "Letzte 30 Tage"
            days < 183 -> "Letztes halbes Jahr"
            else -> "Länger her"
        }
    }

    private fun monthLabel(date: LocalDate?, today: LocalDate): String {
        if (date == null) return "Unbekannt"
        val month = AtFormat.month(date.monthValue)
        return if (date.year == today.year) month else "$month ${date.year}"
    }
}
