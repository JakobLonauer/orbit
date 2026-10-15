package io.github.thelony97.orbit.core.domain

/** Sprachnotiz-Wellenform (PLAN 9 E12): amplitudes as one digit 0–9 per step, at most [MAX] steps. */
object Wave {
    const val MAX = 48
    private const val FULL = 32_767f

    /** [amps] (MediaRecorder.maxAmplitude samples) → „0247…“, squeezed to [MAX] buckets. */
    fun encode(amps: List<Int>): String {
        if (amps.isEmpty()) return ""
        val per = (amps.size + MAX - 1) / MAX
        return amps.chunked(per).joinToString("") { chunk ->
            val v = (chunk.max() / FULL).coerceIn(0f, 1f)
            // Square root: quiet speech still shows.
            (kotlin.math.sqrt(v) * 9f).toInt().coerceIn(0, 9).toString()
        }
    }

    fun decode(wave: String): List<Float> = wave.mapNotNull { it.digitToIntOrNull()?.let { d -> d / 9f } }
}
