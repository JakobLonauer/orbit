package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Live-Status einer Organisation (PLAN 9 D1/D2) in einer Zeile: Urlaub (mit Vertretung) vor
 * Feiertag vor Telefonzeit vor Öffnungszeit – „Urlaub bis 15.8. → Dr. Mayr“, „Feiertag · zu“,
 * „Telefon ab 7:30“, „offen bis 12“.
 */
object ServiceStatus {

    enum class Tone { OPEN, CLOSED, AWAY }

    data class Line(val text: String, val tone: Tone)

    private val SHORT = mapOf(
        DayOfWeek.MONDAY to "Mo", DayOfWeek.TUESDAY to "Di", DayOfWeek.WEDNESDAY to "Mi", DayOfWeek.THURSDAY to "Do",
        DayOfWeek.FRIDAY to "Fr", DayOfWeek.SATURDAY to "Sa", DayOfWeek.SUNDAY to "So",
    )

    /** „12“ for full hours, „7:30“ otherwise. */
    fun clock(t: LocalTime): String = if (t.minute == 0) "${t.hour}" else "${t.hour}:%02d".format(t.minute)

    /** [schoolHoliday]: today lies in the school holidays (only rules with „SH off“ care). */
    fun of(c: Contact, now: LocalDateTime, substituteName: String?, schoolHoliday: Boolean = false): Line? {
        val today = now.toLocalDate()
        val until = c.vacationUntil?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (until != null && !today.isAfter(until)) {
            val sub = substituteName?.takeIf { it.isNotBlank() }?.let { " → $it" }.orEmpty()
            return Line("Urlaub bis ${until.dayOfMonth}.${until.monthValue}.$sub", Tone.AWAY)
        }
        val hours = listOf(c.phoneHours, c.openingHours).filter { it.isNotBlank() }
        if (hours.isEmpty()) return null
        if (Holidays.isHoliday(today) && hours.none { "PH" in it.uppercase() }) return Line("Feiertag · zu", Tone.CLOSED)
        if (schoolHoliday && hours.any { Regex("(?i)\\bSH\\s+off\\b").containsMatchIn(it) }) return Line("Ferien · zu", Tone.CLOSED)
        OpeningHours.parse(c.phoneHours)?.let { return phoneLine(OpeningHours.status(it, now)) }
        OpeningHours.parse(c.openingHours)?.let { return openLine(OpeningHours.status(it, now)) }
        return null
    }

    private fun phoneLine(s: OpeningHours.Status): Line = when (s) {
        is OpeningHours.Status.Open -> Line(s.until?.let { "Telefon bis ${clock(it)}" } ?: "Telefon erreichbar", Tone.OPEN)
        is OpeningHours.Status.Closed -> Line(next("Telefon", s), Tone.CLOSED)
    }

    private fun openLine(s: OpeningHours.Status): Line = when (s) {
        is OpeningHours.Status.Open -> Line(s.until?.let { "offen bis ${clock(it)}" } ?: "offen", Tone.OPEN)
        is OpeningHours.Status.Closed -> Line(next("zu", s), Tone.CLOSED)
    }

    private fun next(head: String, s: OpeningHours.Status.Closed): String {
        val opens = s.opens ?: return if (head == "zu") "zu" else "$head –"
        val day = s.day?.let { SHORT[it] + " " }.orEmpty()
        return if (head == "zu") "zu · ab $day${clock(opens)}" else "$head ab $day${clock(opens)}"
    }
}
