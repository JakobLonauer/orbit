package io.github.thelony97.orbit.core.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * Kamerafahrt im Bogen (PLAN 1.3 Karte): raus – rüber – rein. Decides whether a jump needs the
 * arc and how far out it goes so both places are on screen at the top of the arc.
 */
object FlyPath {

    data class Hop(val lat: Double, val lng: Double, val zoom: Double)

    /** World width at zoom 0 in MapLibre (512 dp). */
    private const val WORLD = 512.0

    /** Web-Mercator x/y in 0..1 at zoom 0. */
    private fun mercX(lng: Double) = (lng + 180.0) / 360.0
    private fun mercY(lat: Double): Double {
        val r = lat.coerceIn(-85.0, 85.0) * PI / 180.0
        return (1.0 - ln(tan(r) + 1.0 / kotlin.math.cos(r)) / PI) / 2.0
    }

    /**
     * The top of the arc from (from, fromZoom) to (to, toZoom) on a [viewportDp] wide view (dp, MapLibre zoom units), or
     * null when one plain move is enough (close by, or already zoomed out far enough).
     */
    fun top(fromLat: Double, fromLng: Double, fromZoom: Double, toLat: Double, toLng: Double, toZoom: Double, viewportDp: Double): Hop? {
        val span = max(abs(mercX(fromLng) - mercX(toLng)), abs(mercY(fromLat) - mercY(toLat)))
        if (span <= 0.0 || viewportDp <= 0.0) return null
        // Both places within 60 % of the view.
        val fit = log2(viewportDp * 0.6 / (WORLD * span))
        val out = min(min(fromZoom, toZoom), fit)
        if (out >= min(fromZoom, toZoom) - 0.75) return null
        return Hop((fromLat + toLat) / 2.0, (fromLng + toLng) / 2.0, max(out, 1.0))
    }
}
