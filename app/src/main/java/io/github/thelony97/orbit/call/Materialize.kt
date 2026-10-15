package io.github.thelony97.orbit.call

import android.content.Context
import io.github.thelony97.orbit.core.domain.Herold
import io.github.thelony97.orbit.core.domain.NumberInfo
import io.github.thelony97.orbit.domain.Phones
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Anrufer materialisiert (PLAN 9 B1): what Orbit knows about an unknown number – offline at once
 * (place, kind, cost), the Herold answer when it comes. One shared state for the call screen, the
 * call notification and the screen after the call.
 */
object Materialize {

    sealed interface Lookup {
        /** Not asked: abroad, switched off, or no network last time. */
        data object Off : Lookup
        data object Searching : Lookup
        data class Found(val hit: Herold.Hit) : Lookup
        /** Asked, Herold knows nobody with this number. */
        data object None : Lookup
    }

    data class Unknown(val info: NumberInfo.Info?, val lookup: Lookup) {
        val hit: Herold.Hit? get() = (lookup as? Lookup.Found)?.hit
    }

    private val _unknown = MutableStateFlow<Map<String, Unknown>>(emptyMap())
    val unknown: StateFlow<Map<String, Unknown>> = _unknown.asStateFlow()

    private fun put(number: String, u: Unknown) {
        // A handful of numbers is plenty (calls of the last minutes).
        _unknown.value = (_unknown.value - number).entries.toList().takeLast(7).associate { it.key to it.value } + (number to u)
    }

    /** Offline facts only, synchronous and cheap after the first call (tables are loaded once). */
    fun info(ctx: Context, number: String): NumberInfo.Info? =
        runCatching { NumberInfo.of(number, Phones.countryIso(ctx)) }.getOrNull()

    /**
     * Starts for [number] (once per number while it is remembered): the offline line at once, then
     * Herold in the background; [onChange] runs after each step.
     */
    fun start(ctx: Context, number: String, scope: CoroutineScope, onChange: () -> Unit) {
        if (number.isBlank() || number in _unknown.value) return
        val app = ctx.applicationContext
        // Taken at once, so a second start for the same call does nothing.
        put(number, Unknown(null, Lookup.Off))
        scope.launch {
            val info = withContext(Dispatchers.Default) { info(app, number) }
            val first = firstLookup(app, info)
            put(number, Unknown(info, first))
            onChange()
            if (first == Lookup.Searching && info != null) {
                // No network: the card simply stays without Herold (Off), the next call asks again.
                put(number, Unknown(info, CallerLookup.lookup(app, info.e164).asLookup()))
                onChange()
            }
        }
    }

    /** What is known before asking: the week's answer, or „searching“ when Orbit may ask. */
    private suspend fun firstLookup(ctx: Context, info: NumberInfo.Info?): Lookup {
        if (info == null || info.region != "AT") return Lookup.Off
        CallerLookup.known(ctx, info.e164)?.let { return it.asLookup() }
        return if (CallerLookup.enabled()) Lookup.Searching else Lookup.Off
    }

    private fun CallerLookup.Entry?.asLookup(): Lookup {
        val entry = this ?: return Lookup.Off
        return entry.hit?.let { Lookup.Found(it) } ?: Lookup.None
    }

    fun of(number: String?): Unknown? = number?.let { _unknown.value[it] }
}
