package io.github.thelony97.orbit.core.domain

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Öffnungszeiten mit Live-Anzeige (PLAN 3.6), in the OSM `opening_hours` style the field already
 * stores: „Mo-Fr 08:00-12:00,13:00-17:00; Sa 09:00-12:00; Su off“, „24/7“, ranges past midnight
 * („Fr 22:00-02:00“). A later rule for a day replaces an earlier one, as in OSM. Anything it does
 * not understand makes [parse] return null – the field is then shown as plain text.
 */
object OpeningHours {

    /** Minutes since midnight; [end] may pass 24:00 (overnight). */
    data class Span(val start: Int, val end: Int)

    /** Index 0 = Monday. */
    data class Week(val days: List<List<Span>>)

    sealed interface Status {
        /** Open now; [until] is when it closes (null = open round the clock). */
        data class Open(val until: LocalTime?, val closingSoon: Boolean) : Status
        /** Closed now; [day] null = later today, otherwise the weekday it opens next. */
        data class Closed(val opens: LocalTime?, val day: DayOfWeek?) : Status
    }

    private val DAYS = listOf("mo", "tu", "we", "th", "fr", "sa", "su")
    private val GERMAN = mapOf("di" to "tu", "mi" to "we", "do" to "th", "so" to "su")
    private const val SOON_MIN = 30

    fun parse(text: String): Week? {
        val t = text.trim()
        if (t.isEmpty()) return null
        if (t == "24/7") return Week(List(7) { listOf(Span(0, 24 * 60)) })
        val days = MutableList(7) { emptyList<Span>() }
        for (rule in t.split(';').map { it.trim() }.filter { it.isNotEmpty() }) {
            val space = rule.indexOf(' ')
            if (space < 0) return null
            val dayPart = rule.substring(0, space).trim()
            val timePart = rule.substring(space + 1).trim()
            // Public and school holidays need a calendar Orbit does not have: left out.
            if (dayPart.uppercase() == "PH" || dayPart.uppercase() == "SH") continue
            val which = dayIndexes(dayPart) ?: return null
            val spans = if (timePart.equals("off", true) || timePart.equals("closed", true)) emptyList() else times(timePart) ?: return null
            which.forEach { days[it] = spans }
        }
        return Week(days)
    }

    private fun dayIndexes(part: String): Set<Int>? {
        val out = LinkedHashSet<Int>()
        for (piece in part.lowercase().split(',')) {
            val range = piece.split('-')
            fun idx(s: String): Int? = DAYS.indexOf(GERMAN[s] ?: s).takeIf { it >= 0 }
            when (range.size) {
                1 -> out += idx(range[0]) ?: return null
                2 -> {
                    val a = idx(range[0]) ?: return null
                    val b = idx(range[1]) ?: return null
                    var i = a
                    while (true) {
                        out += i
                        if (i == b) break
                        i = (i + 1) % 7
                    }
                }
                else -> return null
            }
        }
        return out.takeIf { it.isNotEmpty() }
    }

    private fun times(part: String): List<Span>? = part.split(',').map { it.trim() }.map { r ->
        val (a, b) = r.split('-').takeIf { it.size == 2 } ?: return null
        val start = minutes(a) ?: return null
        var end = minutes(b) ?: return null
        if (end <= start) end += 24 * 60
        if (start >= 24 * 60) return null
        Span(start, end)
    }.sortedBy { it.start }

    private fun minutes(s: String): Int? {
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(s.trim()) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        if (h > 24 || min > 59 || (h == 24 && min != 0)) return null
        return h * 60 + min
    }

    fun status(week: Week, now: LocalDateTime): Status {
        val today = now.dayOfWeek.value - 1
        val minute = now.hour * 60 + now.minute
        // Yesterday's overnight span still running?
        val yesterday = (today + 6) % 7
        week.days[yesterday].firstOrNull { it.end > 24 * 60 && minute < it.end - 24 * 60 }?.let { s ->
            return open(s.end - 24 * 60, minute, chain(week, today, s.end - 24 * 60))
        }
        week.days[today].firstOrNull { minute >= it.start && minute < it.end }?.let { s ->
            return open(s.end, minute, chain(week, today, s.end))
        }
        week.days[today].firstOrNull { it.start > minute }?.let { return Status.Closed(time(it.start), null) }
        for (i in 1..7) {
            val d = (today + i) % 7
            week.days[d].firstOrNull()?.let { return Status.Closed(time(it.start), DayOfWeek.of(d + 1)) }
        }
        return Status.Closed(null, null)
    }

    /** A span that ends at midnight and continues the next day (24/7, „Mo-Su 00:00-24:00“). */
    private fun chain(week: Week, day: Int, end: Int): Int? {
        var d = day
        var e = end
        repeat(7) {
            if (e % (24 * 60) != 0) return e
            d = (d + 1) % 7
            val next = week.days[d].firstOrNull { it.start == 0 } ?: return e
            e = next.end
        }
        return null
    }

    private fun open(end: Int, minute: Int, chained: Int?): Status =
        if (chained == null) Status.Open(null, false)
        else Status.Open(time(chained), chained == end && end - minute <= SOON_MIN)

    private fun time(m: Int): LocalTime = LocalTime.of((m / 60) % 24, m % 60)

    /** „Geöffnet · bis 17:00“, „Schließt bald · 16:45“, „Geschlossen · öffnet 13:00“ / „öffnet Mo 08:00“. */
    fun line(s: Status, short: (DayOfWeek) -> String): String = when (s) {
        is Status.Open -> when {
            s.until == null -> "Rund um die Uhr geöffnet"
            s.closingSoon -> "Schließt bald · " + fmt(s.until)
            else -> "Geöffnet · bis " + fmt(s.until)
        }
        is Status.Closed -> when {
            s.opens == null -> "Geschlossen"
            s.day == null -> "Geschlossen · öffnet " + fmt(s.opens)
            else -> "Geschlossen · öffnet " + short(s.day) + " " + fmt(s.opens)
        }
    }

    private fun fmt(t: LocalTime) = "%02d:%02d".format(t.hour, t.minute)
}
