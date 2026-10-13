package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.CalendarDay
import java.time.LocalDate

/**
 * Austrian German formats (PLAN W7). Own tables instead of the platform locale so
 * "Jänner" never turns into "Januar" on a phone set to de-DE.
 */
object AtFormat {
    val months = listOf(
        "Jänner", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember",
    )
    val monthsShort = listOf(
        "Jän.", "Feb.", "März", "Apr.", "Mai", "Juni",
        "Juli", "Aug.", "Sep.", "Okt.", "Nov.", "Dez.",
    )
    val weekdays = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")
    val weekdaysShort = listOf("Mo.", "Di.", "Mi.", "Do.", "Fr.", "Sa.", "So.")

    fun month(m: Int): String = months[(m - 1).coerceIn(0, 11)]
    fun monthShort(m: Int): String = monthsShort[(m - 1).coerceIn(0, 11)]
    fun weekday(d: LocalDate): String = weekdays[d.dayOfWeek.value - 1]
    fun weekdayShort(d: LocalDate): String = weekdaysShort[d.dayOfWeek.value - 1]

    /** "13. Oktober 2026" */
    fun date(d: LocalDate): String = "${d.dayOfMonth}. ${month(d.monthValue)} ${d.year}"

    /** "13. Okt." */
    fun dateShort(d: LocalDate): String = "${d.dayOfMonth}. ${monthShort(d.monthValue)}"

    /** "Di., 13. Okt." */
    fun weekdayDate(d: LocalDate): String = "${weekdayShort(d)}, ${dateShort(d)}"

    /** "29. Februar 1964" or "3. Jänner" without year. */
    fun day(d: CalendarDay): String =
        "${d.day}. ${month(d.month)}" + (d.year?.let { " $it" } ?: "")

    /** "heute", "morgen", "übermorgen", "in 5 Tagen", "in 3 Wochen", "in 4 Monaten". */
    fun until(days: Long): String = when {
        days < 0 -> ago(-days)
        days == 0L -> "heute"
        days == 1L -> "morgen"
        days == 2L -> "übermorgen"
        days < 14 -> "in $days Tagen"
        days < 60 -> "in ${days / 7} Wochen"
        else -> (days / 30).let { if (it == 1L) "in 1 Monat" else "in $it Monaten" }
    }

    /** "heute", "gestern", "vorgestern", "vor 5 Tagen", "vor 3 Wochen", "vor 2 Jahren". */
    fun ago(days: Long): String = when {
        days <= 0 -> "heute"
        days == 1L -> "gestern"
        days == 2L -> "vorgestern"
        days < 14 -> "vor $days Tagen"
        days < 60 -> "vor ${days / 7} Wochen"
        days < 365 -> months(days).let { if (it == 1L) "vor 1 Monat" else "vor $it Monaten" }
        else -> years(days).let { if (it == 1L) "vor 1 Jahr" else "vor $it Jahren" }
    }

    /** Rounded, not cut off: 700 days are "2 Jahre", not "1 Jahr" (PLAN 1.4). */
    fun months(days: Long): Long = ((days + 15) / 30).coerceAtLeast(1)
    fun years(days: Long): Long = ((days + 182) / 365).coerceAtLeast(1)

    /** "wird 31" / "ist 31 geworden" relative to the occurrence. */
    fun turning(age: Int, past: Boolean = false): String = if (past) "ist $age geworden" else "wird $age"

    /** "5. Hochzeitstag" */
    fun nth(n: Int, what: String): String = "$n. $what"

    /** Round numbers worth highlighting ("Nur runde", PLAN 3.5). */
    fun isRound(n: Int): Boolean = n > 0 && n % 5 == 0
}
