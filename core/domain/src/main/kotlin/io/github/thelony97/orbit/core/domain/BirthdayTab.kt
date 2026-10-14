package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.Occasion
import io.github.thelony97.orbit.core.model.OccasionKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * The „Geburtstage“ tab (PLAN 3.5): every person with a birthday, from today on, in the sections
 * Gerade vorbei (7 Tage) · Heute · Diese Woche · Diesen Monat · then one per month. Pure, so the
 * screen only draws what this returns.
 */
object BirthdayTab {

    enum class Section { JUST_PASSED, TODAY, THIS_WEEK, THIS_MONTH, MONTH }

    /** [daysUntil] is negative in „Gerade vorbei“; [turning] is the age reached on [date], if known. */
    data class Row(
        val contactId: String,
        val occasion: Occasion,
        val date: LocalDate,
        val daysUntil: Long,
        val turning: Int?,
    )

    data class Group(val section: Section, val title: String, val rows: List<Row>)

    data class Result(val groups: List<Group>, val withoutBirthday: Int)

    /** de-AT month names (W9 formats: „Jänner“). */
    val MONTHS = listOf("Jänner", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember")

    private const val JUST_PASSED_DAYS = 7L

    /** „Gratuliert“ for this occurrence: a Verlauf entry with the occasion and its year (status per year). */
    fun congratulated(c: Contact, occasionId: String, year: Int): Boolean =
        c.entries.any { it.typeId == io.github.thelony97.orbit.core.model.BuiltinTypes.CONGRATS && it.occasionId == occasionId && it.occasionYear == year }

    /**
     * [visible] decides who may appear (callers pass the list rules: no archive, no trash).
     * Organisations never appear; „ohne Geburtstag“ counts people only, not yourself.
     * Someone whose birthday was in the last seven days shows only under „Gerade vorbei“.
     */
    fun build(contacts: List<Contact>, today: LocalDate, visible: (Contact) -> Boolean, name: (Contact) -> String = { it.id }): Result {
        val people = contacts.filter { !it.isOrg && visible(it) }
        val rows = ArrayList<Pair<Row, Contact>>()
        var without = 0
        for (c in people) {
            val o = c.dates.firstOrNull { it.kind == OccasionKind.BIRTHDAY }
            if (o == null) {
                if (!c.isMe) without++
                continue
            }
            val last = DateMath.nextOccurrence(o.date, today.minusDays(JUST_PASSED_DAYS))
            val date = if (last.isBefore(today)) last else DateMath.nextOccurrence(o.date, today)
            val turning = o.date.year?.let { date.year - it }
            rows += Row(c.id, o, date, ChronoUnit.DAYS.between(today, date), turning) to c
        }
        val endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val endOfMonth = today.with(TemporalAdjusters.lastDayOfMonth())
        fun sectionOf(r: Row): Section = when {
            r.daysUntil < 0 -> Section.JUST_PASSED
            r.daysUntil == 0L -> Section.TODAY
            !r.date.isAfter(endOfWeek) -> Section.THIS_WEEK
            !r.date.isAfter(endOfMonth) -> Section.THIS_MONTH
            else -> Section.MONTH
        }
        val sorted = rows.sortedWith(compareBy<Pair<Row, Contact>>({ it.first.date }, { name(it.second).lowercase() }, { it.first.contactId }))
        val groups = ArrayList<Group>()
        for ((row, _) in sorted) {
            val section = sectionOf(row)
            val title = when (section) {
                Section.JUST_PASSED -> "Gerade vorbei"
                Section.TODAY -> "Heute"
                Section.THIS_WEEK -> "Diese Woche"
                Section.THIS_MONTH -> "Diesen Monat"
                Section.MONTH -> MONTHS[row.date.monthValue - 1] + if (row.date.year != today.year) " ${row.date.year}" else ""
            }
            val lastGroup = groups.lastOrNull()
            if (lastGroup != null && lastGroup.section == section && lastGroup.title == title) {
                groups[groups.size - 1] = lastGroup.copy(rows = lastGroup.rows + row)
            } else {
                groups += Group(section, title, listOf(row))
            }
        }
        return Result(groups, without)
    }
}
