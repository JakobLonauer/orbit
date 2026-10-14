package io.github.thelony97.orbit.call

import android.telecom.Call
import io.github.thelony97.orbit.core.domain.CallerCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Order in which one tap moves the sound on (pure, see [CallSessionTest]). */
object AudioCycle {
    private val E = android.telecom.CallEndpoint.TYPE_EARPIECE
    private val W = android.telecom.CallEndpoint.TYPE_WIRED_HEADSET
    private val S = android.telecom.CallEndpoint.TYPE_SPEAKER
    private val B = android.telecom.CallEndpoint.TYPE_BLUETOOTH

    fun next(current: Int, available: List<Int>): Int {
        val handset = if (W in available) W else E
        val order = listOfNotNull(handset, S, B.takeIf { it in available })
        val i = order.indexOf(if (current == E || current == W) handset else current)
        return order[(if (i < 0) 0 else i + 1) % order.size]
    }
}

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

    data class Audio(
        val muted: Boolean = false,
        val speaker: Boolean = false,
        /** CallEndpoint.TYPE_* of where the sound goes now. */
        val type: Int = android.telecom.CallEndpoint.TYPE_EARPIECE,
        val name: String = "",
        val current: android.telecom.CallEndpoint? = null,
        val available: List<android.telecom.CallEndpoint> = emptyList(),
    )
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

    /** SystemClock.elapsedRealtime of the last call Telecom handed to Orbit (fallback check). */
    @Volatile var lastCallAddedAt = 0L

    fun add(call: Call) {
        lastCallAddedAt = android.os.SystemClock.elapsedRealtime()
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

    /** Anklopfen (PLAN 3.12): the second call next to the one on screen, if any. */
    fun other(): Call? {
        val p = primary() ?: return null
        return _calls.value.firstOrNull { it !== p && stateOf(it) != Call.STATE_DISCONNECTED && stateOf(it) != Call.STATE_DISCONNECTING }
    }

    /** Switches to the held call; Telecom holds the current one. */
    fun swap() = runCatching {
        val o = other() ?: return@runCatching
        if (stateOf(o) == Call.STATE_HOLDING) o.unhold()
    }

    fun canMerge(): Boolean = runCatching {
        val p = primary() ?: return@runCatching false
        val o = other() ?: return@runCatching false
        o in p.conferenceableCalls
    }.getOrDefault(false)

    /** Konferenz: both calls into one. */
    fun merge() = runCatching {
        val p = primary() ?: return@runCatching
        val o = other() ?: return@runCatching
        if (o in p.conferenceableCalls) p.conference(o)
    }

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

    /** Whether this phone lets Orbit block numbers (the default phone app may). */
    fun canBlock(ctx: android.content.Context): Boolean =
        runCatching { android.provider.BlockedNumberContract.canCurrentUserBlockNumbers(ctx) }.getOrDefault(false)

    /** Puts [number] on the system's block list (shared with the system phone app and SMS). */
    fun block(ctx: android.content.Context, number: String): Boolean = runCatching {
        val values = android.content.ContentValues().apply {
            put(android.provider.BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
        }
        ctx.contentResolver.insert(android.provider.BlockedNumberContract.BlockedNumbers.CONTENT_URI, values) != null
    }.getOrDefault(false)

    fun toggleMute() = runCatching { service?.setMuted(!audio.value.muted) }

    /**
     * Lautsprecher / Bluetooth / Kopfhörer (PLAN 3.12): one tap moves on to the next way the
     * sound can go (handset or headset → speaker → Bluetooth → back).
     */
    @Suppress("DEPRECATION")
    fun toggleSpeaker() = runCatching {
        val a = audio.value
        val s = service ?: return@runCatching
        val next = AudioCycle.next(a.type, a.available.map { it.endpointType })
        val target = a.available.firstOrNull { it.endpointType == next }
        if (target != null) {
            s.requestCallEndpointChange(target, java.util.concurrent.Executor { it.run() }, object : android.os.OutcomeReceiver<Void, android.telecom.CallEndpointException> {
                override fun onResult(result: Void?) {}
                override fun onError(error: android.telecom.CallEndpointException) {}
            })
        } else {
            // No endpoint list from Telecom yet: the old way, speaker on/off.
            s.setAudioRoute(if (a.speaker) android.telecom.CallAudioState.ROUTE_WIRED_OR_EARPIECE else android.telecom.CallAudioState.ROUTE_SPEAKER)
        }
    }
}
