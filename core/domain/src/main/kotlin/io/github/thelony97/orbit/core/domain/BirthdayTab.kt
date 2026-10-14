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

    data class Result(val groups: List<Group>, val withoutBirthday: Int, val without: List<String> = emptyList())

    /** de-AT month names (W9 formats: „Jänner“). */
    val MONTHS = listOf("Jänner", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember")

    private const val JUST_PASSED_DAYS = 7L

    /**
     * Typed birthday in the row („14.10.“, „14.10.1996“, „14.10.96“, „1410“): a real calendar day
     * or null. A two-digit year means 19xx/20xx, whichever is not in the future.
     */
    fun parseDay(text: String, today: LocalDate): io.github.thelony97.orbit.core.model.CalendarDay? {
        val t = text.trim()
        val parts = if (t.contains('.') || t.contains('/') || t.contains('-') || t.contains(' ')) {
            t.split('.', '/', '-', ' ').filter { it.isNotEmpty() }
        } else if (t.length == 4 || t.length == 6 || t.length == 8) {
            listOf(t.substring(0, 2), t.substring(2, 4)) + listOfNotNull(t.substring(4).takeIf { it.isNotEmpty() })
        } else return null
        if (parts.size !in 2..3 || parts.any { p -> p.any { !it.isDigit() } }) return null
        if (parts[0].length > 2 || parts[1].length > 2) return null
        val day = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val year = parts.getOrNull(2)?.let { y ->
            when (y.length) {
                2 -> (2000 + y.toInt()).let { if (it > today.year) it - 100 else it }
                4 -> y.toIntOrNull() ?: return null
                else -> return null
            }
        }
        if (month !in 1..12) return null
        // 29 February is fine without a year; with a year only in leap years.
        val maxDay = if (year == null) java.time.Month.of(month).maxLength() else java.time.YearMonth.of(year, month).lengthOfMonth()
        if (day !in 1..maxDay) return null
        if (year != null && (year < 1900 || year > today.year)) return null
        return io.github.thelony97.orbit.core.model.CalendarDay(day, month, year)
    }

    /** „Gratuliert“ for this occurrence: a Verlauf entry with the occasion and its year (status per year). */
    fun congratulated(c: Contact, occasionId: String, year: Int): Boolean =
        c.entries.any { it.typeId == io.github.thelony97.orbit.core.model.BuiltinTypes.CONGRATS && it.occasionId == occasionId && it.occasionYear == year }

    /** The gift entry for this occurrence, if one was noted (PLAN 3.5 „Geschenk“). */
    fun gift(c: Contact, occasionId: String, year: Int): io.github.thelony97.orbit.core.model.HistoryEntry? =
        c.entries.firstOrNull { it.typeId == io.github.thelony97.orbit.core.model.BuiltinTypes.GIFT && it.occasionId == occasionId && it.occasionYear == year }

    /**
     * Geschenk-Historie im Kontakt (PLAN 3.5): „2025: Buch (mit Tom) · 2024: Gutschein“, newest
     * year first; [name] turns a co-giver's id into a short name.
     */
    fun giftHistory(c: Contact, name: (String) -> String?): String =
        c.entries.filter { it.typeId == io.github.thelony97.orbit.core.model.BuiltinTypes.GIFT && it.note.isNotBlank() }
            .sortedWith(compareByDescending<io.github.thelony97.orbit.core.model.HistoryEntry> { it.occasionYear ?: 0 }.thenByDescending { it.at })
            .joinToString(" · ") { e ->
                val with = e.withIds.mapNotNull(name).takeIf { it.isNotEmpty() }?.joinToString(", ")
                listOfNotNull(e.occasionYear?.let { "$it:" }, e.note.trim(), with?.let { "(mit $it)" }).joinToString(" ")
            }

    /** People who recently gave a gift together with Jakob, most recent first – the chips under „Was?“. */
    fun coGivers(contacts: List<Contact>, limit: Int = 4): List<String> {
        val alive = contacts.filter { it.deletedAt == null && !it.archived }.map { it.id }.toSet()
        return contacts.asSequence()
            .flatMap { c -> c.entries.asSequence().filter { it.typeId == io.github.thelony97.orbit.core.model.BuiltinTypes.GIFT } }
            .sortedByDescending { it.at }
            .flatMap { it.withIds.asSequence() }
            .filter { it in alive }
            .distinct()
            .take(limit)
            .toList()
    }

    /**
     * [visible] decides who may appear (callers pass the list rules: no archive, no trash).
     * Organisations never appear; „ohne Geburtstag“ counts people only, not yourself.
     * Someone whose birthday was in the last seven days shows only under „Gerade vorbei“.
     */
    fun build(contacts: List<Contact>, today: LocalDate, visible: (Contact) -> Boolean, name: (Contact) -> String = { it.id }): Result {
        val people = contacts.filter { !it.isOrg && visible(it) }
        val rows = ArrayList<Pair<Row, Contact>>()
        val without = ArrayList<Contact>()
        for (c in people) {
            val o = c.dates.firstOrNull { it.kind == OccasionKind.BIRTHDAY }
            if (o == null) {
                if (!c.isMe) without += c
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
        val missing = without.sortedBy { name(it).lowercase() }.map { it.id }
        return Result(groups, missing.size, missing)
    }
}
