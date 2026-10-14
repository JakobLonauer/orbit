package io.github.thelony97.orbit.core.domain

import java.time.LocalDate

/**
 * Geometry of the Jahresrad (PLAN 1.3 Anlässe): the year as an orbit, 1 January at the top,
 * clockwise. Angles in degrees, 0° = top. Pure so the drawing and the taps agree.
 */
object YearRing {

    /** Where a day sits on the ring: 0° at 1 January, growing clockwise. */
    fun angleOf(date: LocalDate): Float = (date.dayOfYear - 1) * 360f / date.lengthOfYear()

    /** Start angle of a month (1–12) in [year]. */
    fun monthStart(month: Int, year: Int): Float = angleOf(LocalDate.of(year, month, 1))

    /**
     * The month under a tap. [dx]/[dy] are from the centre (y grows downwards, like the screen);
     * null inside the hub or outside the ring band.
     */
    fun monthAt(dx: Float, dy: Float, inner: Float, outer: Float, year: Int): Int? {
        val r = kotlin.math.hypot(dx, dy)
        if (r < inner || r > outer) return null
        // atan2 with 0° at the top, clockwise.
        var deg = Math.toDegrees(kotlin.math.atan2(dx.toDouble(), -dy.toDouble())).toFloat()
        if (deg < 0) deg += 360f
        return (12 downTo 1).first { monthStart(it, year) <= deg }
    }
}
