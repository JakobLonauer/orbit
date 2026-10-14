package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.OccasionKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Reiter „Angepinnt“ und „Alle Anlässe“ (PLAN 3.5): every occasion's next date with filters
 * (Art, Gruppe, Zeitraum, nur runde, nur angepinnte) and four sortings, each reversible.
 */
object OccasionList {

    enum class Sort(val label: String) { NEXT("Nächste zuerst"), CALENDAR("Kalender"), NAME("Name"), YEARS("Jahre") }

    data class Filter(
        /** Empty = every kind. */
        val kinds: Set<OccasionKind> = emptySet(),
        val groupId: String? = null,
        /** 7 / 30 / 90 days; null = the whole year. */
        val withinDays: Int? = null,
        /** „Nur runde“: the count reached is divisible by 5. */
        val onlyRound: Boolean = false,
        val pinnedOnly: Boolean = false,
    )

    data class Group(val title: String, val rows: List<Upcoming>)

    fun build(
        contacts: List<Contact>,
        today: LocalDate,
        visible: (Contact) -> Boolean,
        filter: Filter = Filter(),
        sort: Sort = Sort.NEXT,
        reversed: Boolean = false,
        name: (String) -> String = { it },
    ): List<Group> {
        val byId = contacts.associateBy { it.id }
        val rows = UpcomingIndex.build(contacts, today, visible).filter { u ->
            (filter.kinds.isEmpty() || u.occasion.kind in filter.kinds) &&
                (filter.groupId == null || listOfNotNull(u.ownerId, u.withId).any { id -> byId[id]?.groupIds?.contains(filter.groupId) == true }) &&
                (filter.withinDays == null || u.daysUntil <= filter.withinDays) &&
                (!filter.onlyRound || (u.turning != null && u.turning > 0 && u.turning % 5 == 0)) &&
                (!filter.pinnedOnly || u.occasion.pinned)
        }
        fun ordered(list: List<Upcoming>) = if (reversed) list.reversed() else list
        return when (sort) {
            Sort.NEXT -> {
                val endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                val endOfMonth = today.with(TemporalAdjusters.lastDayOfMonth())
                val sections = rows.groupBy { u ->
                    when {
                        u.daysUntil == 0L -> "Heute"
                        !u.date.isAfter(endOfWeek) -> "Diese Woche"
                        !u.date.isAfter(endOfMonth) -> "Dieser Monat"
                        else -> "Später"
                    }
                }
                ordered(listOf("Heute", "Diese Woche", "Dieser Monat", "Später").mapNotNull { t -> sections[t]?.let { Group(t, ordered(it)) } })
            }
            Sort.CALENDAR -> {
                val byMonth = rows.sortedWith(compareBy({ it.occasion.date.month }, { it.occasion.date.day }, { name(it.ownerId).lowercase() }))
                    .groupBy { it.occasion.date.month }
                ordered((1..12).mapNotNull { m -> byMonth[m]?.let { Group(BirthdayTab.MONTHS[m - 1], ordered(it)) } })
            }
            Sort.NAME -> listOf(Group("", ordered(rows.sortedWith(compareBy({ name(it.ownerId).lowercase() }, { it.date })))))
            // Highest count first; occasions without a year go last.
            Sort.YEARS -> listOf(Group("", ordered(rows.sortedWith(compareBy({ -(it.turning ?: Int.MIN_VALUE / 2) }, { it.date })))))
        }.filter { it.rows.isNotEmpty() }
    }

    /** Line under the name: „Geburtstag · wird 30“, „5. Hochzeitstag“, „Vertrag endet“. */
    fun line(u: Upcoming): String {
        val n = u.turning?.takeIf { it > 0 }
        return when (u.occasion.kind) {
            OccasionKind.BIRTHDAY -> "Geburtstag" + (n?.let { " · wird $it" } ?: "")
            else -> (n?.let { "$it. " } ?: "") + u.occasion.title
        }
    }
}
