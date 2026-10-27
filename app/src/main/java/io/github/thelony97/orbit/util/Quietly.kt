package io.github.thelony97.orbit.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Extras of a page – ghosts, suggestions, what the phone's own apps know – never take the page
 * down: a failure lands in the Fehlerprotokoll and gives [fallback]. A cancellation still cancels.
 *
 * [ExtraStatus] records which [where] last failed, so the page can show one shared, quiet
 * "didn't load" state instead of each extra failing silently and separately (E1-01/W2-01) –
 * and offer a retry via [ExtraStatus.retry] without the page needing to know how each extra
 * fetches its data.
 */
suspend fun <T> quietly(where: String, fallback: T, block: suspend () -> T): T {
    val result = runCatching { block() }
    val failure = result.exceptionOrNull()
    if (failure == null) {
        ExtraStatus.markOk(where)
        return result.getOrThrow()
    }
    if (failure is CancellationException) throw failure
    ErrorLog.shared?.log("Übersprungen: $where", failure)
    ExtraStatus.markFailed(where)
    return fallback
}

/** Which extras (by their [quietly] label) are currently in a failed state, and a way to retry them. */
object ExtraStatus {
    private val _failed = MutableStateFlow<Set<String>>(emptySet())
    val failed: StateFlow<Set<String>> get() = _failed

    private val _retryTick = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Collect this and key a produceState on retryTicks[where] so a [retry] call re-runs it. */
    val retryTicks: StateFlow<Map<String, Int>> get() = _retryTick

    private fun tick(where: String): Int = _retryTick.value[where] ?: 0

    fun markFailed(where: String) {
        _failed.value = _failed.value + where
    }

    fun markOk(where: String) {
        if (where in _failed.value) _failed.value = _failed.value - where
    }

    /** Bumps the retry counter for [where]; callers key their produceState on [tick] to re-fetch. */
    fun retry(where: String) {
        _retryTick.value = _retryTick.value + (where to (tick(where) + 1))
    }

    fun retryAll(labels: Collection<String>) = labels.forEach(::retry)
}
