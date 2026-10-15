package io.github.thelony97.orbit.core.domain

import java.time.LocalDateTime

/**
 * Dranbleiben (PLAN 9 B5): what happens after one try. Fixed rules – busy, or the other side hangs
 * up within [ANNOUNCE_MS] of answering (a recorded announcement), or nobody answered: try again
 * after [PAUSE_MS], at most [MAX_TRIES] times. Jakob hanging up himself, an error or a real
 * conversation end it.
 */
object Redial {

    const val PAUSE_MS = 15_000L
    const val ANNOUNCE_MS = 20_000L
    const val MAX_TRIES = 30

    /** How a try ended, from the phone's disconnect cause. */
    enum class End { BUSY, REMOTE, LOCAL, ERROR, OTHER }

    enum class Next {
        /** Dial again after the pause. */
        AGAIN,
        /** Got through and talked: done. */
        DONE,
        /** Jakob stopped it (hung up, cancelled) or the phone cannot call. */
        STOP,
        /** Still busy after the last try. */
        GIVE_UP,
    }

    /** [talkedMs]: from answering to the end (0 when never answered); [tries] made so far. */
    fun next(end: End, talkedMs: Long, tries: Int): Next {
        val again = when (end) {
            End.BUSY -> true
            End.REMOTE, End.OTHER -> talkedMs < ANNOUNCE_MS
            End.LOCAL, End.ERROR -> return Next.STOP
        }
        return when {
            !again -> Next.DONE
            tries >= MAX_TRIES -> Next.GIVE_UP
            else -> Next.AGAIN
        }
    }

    /**
     * The times offered for „um … anrufen“: the next two half hours from [now], at least five
     * minutes ahead (today or tomorrow).
     */
    fun offers(now: LocalDateTime): List<LocalDateTime> {
        val start = now.plusMinutes(5).withSecond(0).withNano(0)
        val hour = start.withMinute(0)
        val first = when {
            start.minute == 0 -> start
            start.minute <= 30 -> hour.withMinute(30)
            else -> hour.plusHours(1)
        }
        return listOf(first, first.plusMinutes(30))
    }
}
