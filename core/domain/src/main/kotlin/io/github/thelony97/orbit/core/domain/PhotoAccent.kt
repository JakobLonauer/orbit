package io.github.thelony97.orbit.core.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The person's colour from their profile picture (PLAN 1.3): the dominant vivid hue of a small
 * thumbnail. Saturation counts squared, so a coloured jumper or background wins over skin tones
 * and grey walls. Returns a hue in degrees (0..360) or `null` for black-and-white photos.
 */
object PhotoAccent {

    private const val BINS = 18

    fun hue(argb: IntArray): Float? {
        val weight = DoubleArray(BINS)
        val sx = DoubleArray(BINS)
        val sy = DoubleArray(BINS)
        var total = 0.0
        for (p in argb) {
            if ((p ushr 24) < 128) continue
            val r = (p shr 16 and 0xFF) / 255.0
            val g = (p shr 8 and 0xFF) / 255.0
            val b = (p and 0xFF) / 255.0
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val v = max
            val s = if (max == 0.0) 0.0 else (max - min) / max
            if (s < 0.22 || v < 0.18) continue
            val d = max - min
            val h = when (max) {
                r -> 60 * (((g - b) / d) % 6)
                g -> 60 * ((b - r) / d + 2)
                else -> 60 * ((r - g) / d + 4)
            }.let { if (it < 0) it + 360 else it }
            val w = s * s * v
            val bin = (h / (360.0 / BINS)).toInt().coerceIn(0, BINS - 1)
            weight[bin] += w
            val rad = Math.toRadians(h)
            sx[bin] += cos(rad) * w
            sy[bin] += sin(rad) * w
            total += w
        }
        // Too little colour (grey or black-and-white picture): keep the normal colour.
        if (total <= 0.0 || total < argb.size * 0.02) return null
        val best = weight.indices.maxBy { i -> weight[i] + 0.5 * (weight[(i + 1) % BINS] + weight[(i + BINS - 1) % BINS]) }
        var x = 0.0
        var y = 0.0
        for (i in listOf((best + BINS - 1) % BINS, best, (best + 1) % BINS)) {
            x += sx[i]
            y += sy[i]
        }
        val deg = Math.toDegrees(atan2(y, x)).let { if (it < 0) it + 360 else it }
        return deg.toFloat()
    }
}
