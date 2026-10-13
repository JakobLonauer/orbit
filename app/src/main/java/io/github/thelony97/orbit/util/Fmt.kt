package io.github.thelony97.orbit.util

import io.github.thelony97.orbit.data.DayOfYear
import io.github.thelony97.orbit.domain.zone
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object Fmt {
    val locale: Locale = Locale.GERMAN
    private val dateFmt = DateTimeFormatter.ofPattern("d. MMM yyyy", locale)
    private val shortDate = DateTimeFormatter.ofPattern("d. MMM", locale)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val weekday = DateTimeFormatter.ofPattern("EEE, d. MMM", locale)

    /** "heute", "gestern", "vor 5 Tagen", "vor 3 Wochen" … */
    fun ago(days: Long): String = when {
        days <= 0L -> "heute"
        days == 1L -> "gestern"
        days < 14 -> "vor $days Tagen"
        days < 60 -> "vor ${days / 7} Wochen"
        days < 365 -> (days / 30).let { if (it == 1L) "vor 1 Monat" else "vor $it Monaten" }
        else -> (days / 365).let { if (it == 1L) "vor 1 Jahr" else "vor $it Jahren" }
    }

    /** Very short relative age for list rows: "heute", "3 T", "5 W", "4 M", "2 J". */
    fun short(days: Long): String = when {
        days <= 0L -> "heute"
        days < 14 -> "$days T"
        days < 60 -> "${days / 7} W"
        days < 365 -> "${days / 30} M"
        else -> "${days / 365} J"
    }

    /** "in 3 Tagen", "morgen", "heute" */
    fun until(days: Long): String = when {
        days < 0L -> "seit ${-days} ${if (days == -1L) "Tag" else "Tagen"}"
        days == 0L -> "heute"
        days == 1L -> "morgen"
        days < 14 -> "in $days Tagen"
        days < 60 -> "in ${days / 7} Wochen"
        else -> (days / 30).let { if (it == 1L) "in 1 Monat" else "in $it Monaten" }
    }

    fun duration(seconds: Long): String = when {
        seconds < 60 -> "$seconds s"
        seconds < 3600 -> "${seconds / 60} Min"
        else -> "${seconds / 3600} h ${(seconds % 3600) / 60} Min"
    }

    fun date(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone()).format(dateFmt)
    fun date(d: LocalDate): String = d.format(dateFmt)
    fun shortDate(d: LocalDate): String = d.format(shortDate)
    fun weekdayDate(d: LocalDate): String = d.format(weekday)
    fun time(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone()).format(timeFmt)
    fun dateTime(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone()).let { "${it.format(weekday)}, ${it.format(timeFmt)}" }

    fun dayOfYear(d: DayOfYear): String {
        val month = java.time.Month.of(d.month.coerceIn(1, 12)).getDisplayName(TextStyle.FULL, locale)
        return if (d.year != null) "${d.day}. $month ${d.year}" else "${d.day}. $month"
    }

    fun monthName(month: Int): String =
        java.time.Month.of(month.coerceIn(1, 12)).getDisplayName(TextStyle.FULL, locale)
}
