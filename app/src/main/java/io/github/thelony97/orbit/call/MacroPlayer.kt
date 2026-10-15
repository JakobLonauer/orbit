package io.github.thelony97.orbit.call

import io.github.thelony97.orbit.core.domain.Macros
import io.github.thelony97.orbit.core.model.Macro
import io.github.thelony97.orbit.data.Contact
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Hotline-Makro abspielen (PLAN 9 B4, Orbit as the phone app): the keypad plays itself – one tone
 * after the other with the call's own DTMF, pauses as a running segment, „Warten“ until Jakob taps
 * „Weiter“. Any key Jakob presses himself takes over (the player stops).
 */
object MacroPlayer {

    data class State(
        val name: String,
        val plan: List<Macros.Play>,
        /** The step being played now. */
        val index: Int,
        /** Wall-clock start of the current pause (for the segment bar). */
        val since: Long,
    ) {
        val step: Macros.Play? get() = plan.getOrNull(index)
        val waiting: Boolean get() = step == Macros.Play.AwaitTap
    }

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var tap: CompletableDeferred<Unit>? = null

    /** The digits of an own field: the organisation's first, then Jakob's (ICH). */
    fun fieldValue(org: Contact?, me: Contact?, id: String): String? =
        org?.fields?.firstOrNull { it.id == id }?.value ?: me?.fields?.firstOrNull { it.id == id }?.value

    fun start(macro: Macro, org: Contact?, me: Contact?, delayFirst: Long = 0) {
        stop()
        val plan = Macros.plan(macro.steps) { fieldValue(org, me, it) }
        if (plan.isEmpty()) return
        val name = macro.name.ifBlank { "Makro" }
        job = scope.launch {
            if (delayFirst > 0) delay(delayFirst)
            plan.forEachIndexed { i, p ->
                _state.value = State(name, plan, i, System.currentTimeMillis())
                when (p) {
                    is Macros.Play.Tone -> {
                        CallSession.playTone(p.char)
                        delay(CallSession.TONE_MS + CallSession.TONE_GAP_MS)
                    }
                    is Macros.Play.Wait -> delay(p.ms)
                    Macros.Play.AwaitTap -> {
                        val t = CompletableDeferred<Unit>()
                        tap = t
                        t.await()
                    }
                }
            }
            _state.value = null
        }
    }

    /** „Weiter“ during a wait. */
    fun next() {
        tap?.complete(Unit)
    }

    /** Jakob took over (or the call ended). */
    fun stop() {
        job?.cancel()
        job = null
        tap = null
        _state.value = null
    }

    /**
     * PLAN 9 B4 from the contact page: the call is placed, the macro starts once it is answered.
     * Held until the call to [number] is active.
     */
    data class Pending(val number: String, val macro: Macro, val org: Contact?, val me: Contact?)

    @Volatile var pending: Pending? = null
}
