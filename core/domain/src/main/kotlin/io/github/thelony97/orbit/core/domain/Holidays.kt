package io.github.thelony97.orbit.core.domain

import java.time.LocalDate

/** Österreichische Feiertage (PLAN 9 D2), offline: fixed days plus the ones that follow Easter. */
object Holidays {

    /** Easter Sunday (anonymous Gregorian computus). */
    fun easter(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }

    fun austria(year: Int): Set<LocalDate> {
        val e = easter(year)
        val fixed = listOf(1 to 1, 1 to 6, 5 to 1, 8 to 15, 10 to 26, 11 to 1, 12 to 8, 12 to 25, 12 to 26)
            .map { (m, d) -> LocalDate.of(year, m, d) }
        return (fixed + listOf(e.plusDays(1), e.plusDays(39), e.plusDays(50), e.plusDays(60))).toSet()
    }

    fun isHoliday(day: LocalDate): Boolean = day in austria(day.year)
}
