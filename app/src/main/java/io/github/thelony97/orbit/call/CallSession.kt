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

    /** Contact whose note the call screen took (Save/Close) and when. */
    @Volatile var noteHandled: Pair<String, Long>? = null

    /**
     * The call screen shows (or just handled) the note line for [contactId]: the after-call
     * notification stays away for this contact only – other calls keep their prompts.
     */
    fun noteOnScreen(ctx: android.content.Context, contactId: String): Boolean {
        // Only when the note line is really in front of Jakob: screen visible and phone unlocked.
        val unlocked = ctx.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked != true
        if (screenVisible && unlocked && ended.value?.card?.contactId == contactId) return true
        val h = noteHandled ?: return false
        return h.first == contactId && System.currentTimeMillis() - h.second < 5 * 60_000L
    }

    @Volatile var service: OrbitInCallService? = null

    /** The call screen is in front (between its onStart and onStop). */
    @Volatile var screenVisible = false

    fun changed() {
        _version.value = _version.value + 1
    }

    fun add(call: Call) {
        // The after-call screen of an earlier call stays until it is closed; a new call shows first.
        _calls.value = _calls.value + call
        changed()
    }

    fun remove(call: Call, card: CallerCard?) {
        val connectedAt = runCatching { call.details.connectTimeMillis }.getOrDefault(0L)
        _calls.value = _calls.value - call
        // The after-call screen only when the call screen is in front; otherwise the after-call
        // notification takes over (never a stale „Beendet“ screen for a later call).
        if (_calls.value.isEmpty() && connectedAt > 0 && screenVisible) {
            keepDraft()
            ended.value = Ended(numberOf(call), card, connectedAt, System.currentTimeMillis())
        }
        changed()
    }

    /** Text typed into the note line, kept here so leaving the screen never loses it. */
    @Volatile var draft: String = ""

    /** A pending draft of the shown after-call screen goes into its Verlauf entry. */
    fun keepDraft() {
        val e = ended.value ?: return
        val text = draft.trim()
        draft = ""
        if (text.isNotEmpty()) service?.let { runCatching { CallNoteWorker.enqueue(it, e, text) } }
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

    /**
     * Dual SIM set to „ask every time“: Telecom waits in SELECT_PHONE_ACCOUNT until the phone app
     * picks one – without this the outgoing call would hang forever.
     */
    fun accounts(ctx: android.content.Context, call: Call): List<android.telecom.PhoneAccountHandle> {
        val fromCall = runCatching {
            call.details.extras?.getParcelableArrayList(Call.AVAILABLE_PHONE_ACCOUNTS, android.telecom.PhoneAccountHandle::class.java)
                ?: call.details.intentExtras?.getParcelableArrayList(Call.AVAILABLE_PHONE_ACCOUNTS, android.telecom.PhoneAccountHandle::class.java)
        }.getOrNull().orEmpty()
        if (fromCall.isNotEmpty()) return fromCall
        return runCatching { ctx.getSystemService(android.telecom.TelecomManager::class.java)?.callCapablePhoneAccounts }.getOrNull().orEmpty()
    }

    fun accountLabel(ctx: android.content.Context, handle: android.telecom.PhoneAccountHandle, index: Int): String =
        runCatching { ctx.getSystemService(android.telecom.TelecomManager::class.java)?.getPhoneAccount(handle)?.label?.toString() }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: "SIM ${index + 1}"

    fun pickAccount(ctx: android.content.Context, index: Int) = runCatching {
        val c = _calls.value.firstOrNull { stateOf(it) == Call.STATE_SELECT_PHONE_ACCOUNT } ?: return@runCatching
        accounts(ctx, c).getOrNull(index)?.let { c.phoneAccountSelected(it, false) }
    }

    fun toggleMute() = runCatching { service?.setMuted(!audio.value.muted) }

    @Suppress("DEPRECATION")
    fun toggleSpeaker() = runCatching {
        service?.setAudioRoute(
            if (audio.value.speaker) android.telecom.CallAudioState.ROUTE_WIRED_OR_EARPIECE else android.telecom.CallAudioState.ROUTE_SPEAKER,
        )
    }
}
