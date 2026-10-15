package io.github.thelony97.orbit.core.domain

import java.time.LocalDate
import java.time.temporal.IsoFields

/**
 * Kneifen als zweite Achse (PLAN 9 E19): the Verlauf by day, week, month or year; above the day
 * every span is one line – „KW 41 · 5 Anrufe · 2 Treffen“.
 */
object TimeZoom {

    enum class Level { DAY, WEEK, MONTH, YEAR }

    fun zoomOut(l: Level): Level = Level.entries[(l.ordinal + 1).coerceAtMost(Level.entries.lastIndex)]
    fun zoomIn(l: Level): Level = Level.entries[(l.ordinal - 1).coerceAtLeast(0)]

    /** Sortable key of the span [d] lies in. */
    fun key(d: LocalDate, l: Level): String = when (l) {
        Level.DAY -> d.toString()
        Level.WEEK -> "%d-W%02d".format(d.get(IsoFields.WEEK_BASED_YEAR), d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
        Level.MONTH -> "%d-%02d".format(d.year, d.monthValue)
        Level.YEAR -> d.year.toString()
    }

    /** „KW 41“, „Oktober“, „2025“ – the year only when it is not this one. */
    fun label(d: LocalDate, l: Level, today: LocalDate): String {
        val other = if (d.year != today.year) " ${d.year}" else ""
        return when (l) {
            Level.DAY -> AtFormat.weekdayDate(d) + other
            Level.WEEK -> "KW ${d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}" + (if (d.get(IsoFields.WEEK_BASED_YEAR) != today.year) " ${d.get(IsoFields.WEEK_BASED_YEAR)}" else "")
            Level.MONTH -> AtFormat.month(d.monthValue) + other
            Level.YEAR -> d.year.toString()
        }
    }

    private val PLURALS = mapOf(
        "Anruf" to "Anrufe", "Treffen" to "Treffen", "Nachricht" to "Nachrichten", "E-Mail" to "E-Mails", "Videocall" to "Videocalls",
        "Event" to "Events", "Notiz" to "Notizen", "Auftrag" to "Aufträge", "Geschenk" to "Geschenke", "Gratuliert" to "Gratuliert",
    )

    /** „5 Anrufe · 2 Treffen“ from the kinds' names of a span's entries, most first. */
    fun summary(kindNames: List<String>): String = kindNames.groupingBy { it }.eachCount().entries
        .sortedByDescending { it.value }
        .joinToString(" · ") { (name, n) -> if (n == 1) "1 $name" else "$n ${PLURALS[name] ?: name}" }
}
