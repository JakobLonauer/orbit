package io.github.thelony97.orbit.util

import kotlinx.coroutines.CancellationException

/**
 * Extras of a page – ghosts, suggestions, what the phone's own apps know – never take the page
 * down: a failure lands in the Fehlerprotokoll and gives [fallback]. A cancellation still cancels.
 */
suspend fun <T> quietly(where: String, fallback: T, block: suspend () -> T): T {
    val result = runCatching { block() }
    val failure = result.exceptionOrNull() ?: return result.getOrThrow()
    if (failure is CancellationException) throw failure
    ErrorLog.shared?.log("Übersprungen: $where", failure)
    return fallback
}
