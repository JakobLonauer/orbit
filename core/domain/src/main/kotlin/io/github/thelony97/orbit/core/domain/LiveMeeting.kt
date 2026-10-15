package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Meeting

/**
 * Treffen live (PLAN 9 C2/C3): a meeting is „soon“ in the 30 minutes before it starts and „on“
 * while it runs. Late by dragging: the minutes snap at +5/+10/+15.
 */
object LiveMeeting {

    const val SOON_MS = 30 * 60_000L

    sealed interface Phase {
        val meeting: Meeting

        /** Starts in [minutes] (rounded up). */
        data class Soon(override val meeting: Meeting, val minutes: Int) : Phase

        /** Runs, [minutes] left (rounded up). */
        data class On(override val meeting: Meeting, val minutes: Int) : Phase
    }

    /** The meeting that matters now: a running one first, else the next one starting within 30 minutes. */
    fun now(meetings: List<Meeting>, now: Long, visible: (String) -> Boolean): Phase? {
        val open = meetings.filter { !it.resolved && it.end > now && visible(it.contactId) }
        open.filter { it.start <= now }.minByOrNull { it.end }?.let { return Phase.On(it, minutesUp(it.end - now)) }
        return open.filter { it.start > now && it.start - now <= SOON_MS }.minByOrNull { it.start }
            ?.let { Phase.Soon(it, minutesUp(it.start - now)) }
    }

    private fun minutesUp(ms: Long): Int = ((ms + 59_999) / 60_000).toInt()

    /** The late steps a drag snaps to. */
    val steps = listOf(5, 10, 15)

    /** Which step a drag of [dragDp] (downwards) has reached; 0 = none yet. Every 36 dp one step. */
    fun stepFor(dragDp: Float): Int {
        val n = (dragDp / STEP_DP).toInt().coerceIn(0, steps.size)
        return if (n == 0) 0 else steps[n - 1]
    }

    const val STEP_DP = 36f

    /** „Bin ca. 10 Min später da.“ */
    fun message(minutes: Int): String = "Bin ca. $minutes Min später da."
}
