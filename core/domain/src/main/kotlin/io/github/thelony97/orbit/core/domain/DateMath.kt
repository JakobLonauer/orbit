package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.CalendarDay
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Pure date arithmetic for recurring days (birthdays, anniversaries). No Android, no time zone. */
object DateMath {

    /** The day in [year]; 29 February falls back to 28 February in non-leap years. */
    fun inYear(d: CalendarDay, year: Int): LocalDate {
        val month = d.month.coerceIn(1, 12)
        val maxDay = YearMonth.of(year, month).lengthOfMonth()
        return LocalDate.of(year, month, d.day.coerceIn(1, maxDay))
    }

    /** Next occurrence on or after [today]. */
    fun nextOccurrence(d: CalendarDay, today: LocalDate): LocalDate {
        val thisYear = inYear(d, today.year)
        return if (thisYear.isBefore(today)) inYear(d, today.year + 1) else thisYear
    }

    fun daysUntil(d: CalendarDay, today: LocalDate): Long = ChronoUnit.DAYS.between(today, nextOccurrence(d, today))

    /** Age (or anniversary count) reached at the next occurrence, if the year is known. */
    fun turning(d: CalendarDay, today: LocalDate): Int? = d.year?.let { nextOccurrence(d, today).year - it }
}
