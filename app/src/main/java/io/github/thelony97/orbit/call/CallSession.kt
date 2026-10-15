package io.github.thelony97.orbit.call

import android.telecom.Call
import io.github.thelony97.orbit.core.domain.CallerCard
import io.github.thelony97.orbit.core.domain.Macros
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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

    /**
     * A call that connected and just ended: the screen stays for the note line (PLAN 3.12 „Danach“).
     * [keys]: what Jakob typed in it (PLAN 9 B4 „einmal selbst durchwählen = aufgenommen“).
     */
    data class Ended(
        val number: String,
        val card: CallerCard?,
        val connectedAt: Long,
        val endedAt: Long,
        val keys: List<Macros.Key> = emptyList(),
    )
    val ended = MutableStateFlow<Ended?>(null)

    /** Caller cards per number, looked up once when the call arrives. */
    val cards = MutableStateFlow<Map<String, CallerCard?>>(emptyMap())

    /** PLAN 9 B2: numbers known only through an organisation's range → their Durchwahl. */
    val extensions = MutableStateFlow<Map<String, String>>(emptyMap())

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
        // A new call on its own starts a new recording; call waiting keeps the running one.
        if (_calls.value.isEmpty()) synchronized(typed) { typed.clear() }
        // The after-call screen of an earlier call stays until it is closed; a new call shows first.
        _calls.value = _calls.value + call
        changed()
    }

    /** [quiet]: a Dranbleiben try (PLAN 9 B5) – the next one follows, no after-call screen. */
    fun remove(call: Call, card: CallerCard?, quiet: Boolean = false) {
        val connectedAt = runCatching { call.details.connectTimeMillis }.getOrDefault(0L)
        _calls.value = _calls.value - call
        // The after-call screen only when the call screen is in front; otherwise the after-call
        // notification takes over (never a stale „Beendet“ screen for a later call).
        if (_calls.value.isEmpty() && connectedAt > 0 && screenVisible && !quiet) {
            keepDraft()
            ended.value = Ended(numberOf(call), card, connectedAt, System.currentTimeMillis(), synchronized(typed) { typed.toList() })
        }
        if (_calls.value.isEmpty()) {
            MacroPlayer.stop()
            queue.value = null
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

    /** Calls that stand on their own: participants of a conference are shown as the conference. */
    private fun topLevel(): List<Call> = _calls.value.filter { runCatching { it.parent }.getOrNull() == null }

    fun primary(): Call? {
        val list = topLevel()
        return CallPick.primary(list.map(::stateOf))?.let { list[it] }
    }

    fun ringing(): Call? = _calls.value.firstOrNull { stateOf(it) == Call.STATE_RINGING }

    /** Anklopfen (PLAN 3.12): the second call next to the one on screen, if any. */
    fun other(): Call? {
        val p = primary() ?: return null
        return topLevel().firstOrNull { it !== p && stateOf(it) != Call.STATE_DISCONNECTED && stateOf(it) != Call.STATE_DISCONNECTING }
    }

    /** Switches to the held call; Telecom holds the current one. */
    fun swap() = runCatching {
        val o = other() ?: return@runCatching
        if (stateOf(o) == Call.STATE_HOLDING) o.unhold()
    }

    fun canMerge(): Boolean = runCatching {
        val p = primary() ?: return@runCatching false
        val o = other() ?: return@runCatching false
        o in p.conferenceableCalls || p.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)
    }.getOrDefault(false)

    /** Konferenz: both calls into one. */
    fun merge() = runCatching {
        val p = primary() ?: return@runCatching
        val o = other() ?: return@runCatching
        if (o in p.conferenceableCalls) p.conference(o)
        else if (p.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) p.mergeConference()
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

    // ---------------------------------------------------------------- DTMF (PLAN 9 B4)

    /** Long enough for every hotline (≥ 200 ms), one tone after the other, never cut short. */
    const val TONE_MS = 230L
    const val TONE_GAP_MS = 90L

    private val tones = Channel<Char>(capacity = 64)
    private val toneScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        toneScope.launch {
            for (d in tones) {
                val c = primary() ?: continue
                runCatching { c.playDtmfTone(d) }
                delay(TONE_MS)
                runCatching { c.stopDtmfTone() }
                delay(TONE_GAP_MS)
            }
        }
    }

    /** Keys Jakob typed in the current call, ms after it was answered. */
    private val typed = ArrayList<Macros.Key>()

    /** A key Jakob typed: played in full and recorded for „Als Makro speichern“. */
    fun dtmf(digit: Char) {
        val c = primary()
        val connected = c?.let { runCatching { it.details.connectTimeMillis }.getOrDefault(0L) } ?: 0L
        if (connected > 0) synchronized(typed) { typed += Macros.Key(digit, System.currentTimeMillis() - connected) }
        tones.trySend(digit)
    }

    /** A key a macro plays: same tone, not recorded. */
    fun playTone(digit: Char) {
        tones.trySend(digit)
    }

    /**
     * Dual SIM set to „ask every time“: Telecom waits in SELECT_PHONE_ACCOUNT until the phone app
     * picks one – without this the outgoing call would hang forever.
     */
    // Without READ_PHONE_STATE the SecurityException is caught (runCatching) and the list stays empty.
    @android.annotation.SuppressLint("MissingPermission")
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

    /** Sound one way directly (PLAN 9 B6: speaker for the queue, the handset again at the ear). */
    @Suppress("DEPRECATION")
    fun routeTo(type: Int) = runCatching {
        val a = audio.value
        if (a.type == type) return@runCatching
        val s = service ?: return@runCatching
        val target = a.available.firstOrNull { it.endpointType == type }
        if (target != null) {
            s.requestCallEndpointChange(target, java.util.concurrent.Executor { it.run() }, object : android.os.OutcomeReceiver<Void, android.telecom.CallEndpointException> {
                override fun onResult(result: Void?) {}
                override fun onError(error: android.telecom.CallEndpointException) {}
            })
        } else {
            s.setAudioRoute(
                if (type == android.telecom.CallEndpoint.TYPE_SPEAKER) android.telecom.CallAudioState.ROUTE_SPEAKER
                else android.telecom.CallAudioState.ROUTE_WIRED_OR_EARPIECE,
            )
        }
    }

    // ---------------------------------------------------------------- Warteschleife (PLAN 9 B6)

    /** Since when Jakob waits in a hotline's queue (speaker on, screen dark); null = talking. */
    val queue = MutableStateFlow<Long?>(null)

    fun enterQueue() {
        if (primary() == null) return
        queue.value = System.currentTimeMillis()
        routeTo(android.telecom.CallEndpoint.TYPE_SPEAKER)
        changed()
        service?.let { runCatching { CallNotification.update(it) } }
    }

    /** [ear]: lifted to the ear – the sound goes back to the handset. */
    fun leaveQueue(ear: Boolean) {
        if (queue.value == null) return
        queue.value = null
        if (ear) routeTo(android.telecom.CallEndpoint.TYPE_EARPIECE)
        changed()
        service?.let { runCatching { CallNotification.update(it) } }
    }
}
