package io.github.thelony97.orbit.call

import android.telecom.Call
import io.github.thelony97.orbit.core.domain.CallerCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which call the screen and the notification are about (pure, see [CallSessionTest]). */
object CallPick {
    private val order = listOf(
        Call.STATE_RINGING,
        Call.STATE_ACTIVE,
        Call.STATE_DIALING,
        Call.STATE_CONNECTING,
        Call.STATE_PULLING_CALL,
        Call.STATE_SELECT_PHONE_ACCOUNT,
        Call.STATE_NEW,
        Call.STATE_HOLDING,
        Call.STATE_DISCONNECTING,
    )

    /** Index of the call to show: a ringing call first (it needs an answer), then the live one. */
    fun primary(states: List<Int>): Int? {
        for (s in order) {
            val i = states.indexOf(s)
            if (i >= 0) return i
        }
        return states.indices.firstOrNull { states[it] != Call.STATE_DISCONNECTED }
    }

    fun isOutgoing(state: Int) = state == Call.STATE_DIALING || state == Call.STATE_CONNECTING ||
        state == Call.STATE_SELECT_PHONE_ACCOUNT || state == Call.STATE_PULLING_CALL
}

/**
 * Process-wide state of the calls Telecom gave to [OrbitInCallService] (PLAN 3.12). Call objects
 * are mutable, so every change bumps [version]; screens re-read the calls then.
 */
object CallSession {
    private val _calls = MutableStateFlow<List<Call>>(emptyList())
    val calls: StateFlow<List<Call>> = _calls.asStateFlow()

    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    data class Audio(val muted: Boolean = false, val speaker: Boolean = false)
    val audio = MutableStateFlow(Audio())

    /** A call that connected and just ended: the screen stays for the note line (PLAN 3.12 „Danach“). */
    data class Ended(val number: String, val card: CallerCard?, val connectedAt: Long, val endedAt: Long)
    val ended = MutableStateFlow<Ended?>(null)

    /** Caller cards per number, looked up once when the call arrives. */
    val cards = MutableStateFlow<Map<String, CallerCard?>>(emptyMap())

    /** When the call screen last offered the note line; the after-call notification then stays away. */
    @Volatile var noteOfferedAt = 0L

    @Volatile var service: OrbitInCallService? = null

    fun changed() {
        _version.value = _version.value + 1
    }

    fun add(call: Call) {
        _calls.value = _calls.value + call
        ended.value = null
        changed()
    }

    fun remove(call: Call, card: CallerCard?) {
        val connectedAt = runCatching { call.details.connectTimeMillis }.getOrDefault(0L)
        _calls.value = _calls.value - call
        if (_calls.value.isEmpty() && connectedAt > 0) {
            ended.value = Ended(numberOf(call), card, connectedAt, System.currentTimeMillis())
        }
        changed()
    }

    fun stateOf(call: Call): Int = runCatching { call.details.state }.getOrDefault(Call.STATE_DISCONNECTED)

    fun numberOf(call: Call): String = runCatching { call.details.handle?.schemeSpecificPart }.getOrNull().orEmpty()

    fun primary(): Call? {
        val list = _calls.value
        return CallPick.primary(list.map(::stateOf))?.let { list[it] }
    }

    fun ringing(): Call? = _calls.value.firstOrNull { stateOf(it) == Call.STATE_RINGING }

    // Each action is guarded: a failing call must never take the screen (or the call) with it.
    fun answer() = runCatching { ringing()?.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY) }
    fun decline() = runCatching { ringing()?.reject(false, null) }
    fun declineWithSms(text: String) = runCatching { ringing()?.reject(true, text) }
    fun hangUp() = runCatching { (primary())?.disconnect() }

    fun toggleHold() = runCatching {
        val c = primary() ?: return@runCatching
        if (stateOf(c) == Call.STATE_HOLDING) c.unhold() else c.hold()
    }

    fun dtmf(digit: Char) = runCatching {
        val c = primary() ?: return@runCatching
        c.playDtmfTone(digit)
        c.stopDtmfTone()
    }

    fun toggleMute() = runCatching { service?.setMuted(!audio.value.muted) }

    @Suppress("DEPRECATION")
    fun toggleSpeaker() = runCatching {
        service?.setAudioRoute(
            if (audio.value.speaker) android.telecom.CallAudioState.ROUTE_WIRED_OR_EARPIECE else android.telecom.CallAudioState.ROUTE_SPEAKER,
        )
    }
}
