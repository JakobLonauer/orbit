package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Zur Zeit senden (PLAN 9 E4): the Mitternachts-Rad. The dial is a day (00:00 at the top,
 * clockwise); the finger's angle picks the time in 5-minute steps. Near the top it snaps to 00:00
 * of the contact's next occasion – the birthday wish at midnight.
 */
object SendLater {

    const val STEP_MIN = 5
    private const val DAY_MIN = 24 * 60
    /** How close to the top (in minutes of the dial) the midnight magnet pulls. */
    const val MAGNET_MIN = 30
    /** Occasions further away are no magnet. */
    const val MAGNET_DAYS = 14L

    /** Minute of the day for an angle in degrees (0 = top, clockwise), on the 5-minute grid. */
    fun minuteOf(angleDeg: Float): Int {
        val a = ((angleDeg % 360f) + 360f) % 360f
        val m = Math.round(a / 360f * DAY_MIN / STEP_MIN) * STEP_MIN
        return m % DAY_MIN
    }

    fun angleOf(minute: Int): Float = minute.toFloat() / DAY_MIN * 360f

    /** The next occasion of [c] (own or shared) within [MAGNET_DAYS], as its day. */
    fun nextOccasion(c: Contact, all: List<Contact>, today: LocalDate): Upcoming? =
        UpcomingIndex.build(all, today) { it.deletedAt == null && !it.archived }
            .filter { (it.ownerId == c.id || it.withId == c.id) && it.daysUntil in 1..MAGNET_DAYS }
            .minByOrNull { it.daysUntil }

    /**
     * When it goes out: near the top with an occasion → its 00:00 (magnet); otherwise today at
     * that minute, or tomorrow when that is already past.
     */
    fun at(minute: Int, now: LocalDateTime, occasion: LocalDate?): Pick {
        val nearTop = minute <= MAGNET_MIN || minute >= DAY_MIN - MAGNET_MIN
        if (nearTop && occasion != null) return Pick(occasion.atStartOfDay(), magnet = true)
        val t = LocalTime.of(minute / 60, minute % 60)
        val today = now.toLocalDate().atTime(t)
        return Pick(if (today.isAfter(now)) today else today.plusDays(1), magnet = false)
    }

    data class Pick(val at: LocalDateTime, val magnet: Boolean)

    /** „heute 18:30“, „morgen 07:00“, „Fr 00:00“. */
    fun label(at: LocalDateTime, now: LocalDateTime): String {
        val days = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), at.toLocalDate())
        val day = when (days) {
            0L -> "heute"
            1L -> "morgen"
            else -> AtFormat.weekdayShort(at.toLocalDate())
        }
        return "$day %02d:%02d".format(at.hour, at.minute)
    }
}
