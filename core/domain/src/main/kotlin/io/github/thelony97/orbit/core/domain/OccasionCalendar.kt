package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Occasion
import io.github.thelony97.orbit.core.model.Repeat
import java.time.LocalDate

/**
 * „Übernahme in den Handy-Kalender“ (PLAN 3.5, optional): one all-day event per occasion, yearly
 * ones repeating. The title has no count („Annas Geburtstag“), since it repeats.
 */
object OccasionCalendar {

    data class Event(val title: String, val day: LocalDate, val rrule: String?, val description: String)

    /** Null when a one-off occasion has no year (it cannot be placed). */
    fun event(o: Occasion, ownerId: String, today: LocalDate, nameOf: (String) -> String): Event? {
        val day = when (o.repeat) {
            Repeat.YEARLY -> DateMath.nextOccurrence(o.date, today)
            Repeat.ONCE -> o.date.year?.let { y -> runCatching { LocalDate.of(y, o.date.month, o.date.day) }.getOrNull() } ?: return null
        }
        val title = ReminderPlan.phrase(Upcoming(ownerId, o.sharedWith, o, day, 0, turning = null), nameOf).trim()
        // 29 February: the last day of February every year (28th, 29th in leap years).
        val rule = when {
            o.repeat == Repeat.ONCE -> null
            o.date.month == 2 && o.date.day == 29 -> "FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=-1"
            else -> "FREQ=YEARLY"
        }
        return Event(title, day, rule, o.note.trim())
    }
}
