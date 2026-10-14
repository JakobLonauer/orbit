package io.github.thelony97.orbit.call

import android.telecom.Call
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which call the screen shows: a ringing one first – it must never go unseen (PLAN 3.12). */
class CallSessionTest {
    @Test fun ringingBeatsActiveAndHeld() {
        assertEquals(2, CallPick.primary(listOf(Call.STATE_ACTIVE, Call.STATE_HOLDING, Call.STATE_RINGING)))
    }

    @Test fun activeBeatsHeldAndDialing() {
        assertEquals(1, CallPick.primary(listOf(Call.STATE_HOLDING, Call.STATE_ACTIVE)))
        assertEquals(1, CallPick.primary(listOf(Call.STATE_DIALING, Call.STATE_ACTIVE)))
    }

    @Test fun heldCallStillShowsWhenItIsTheOnlyOne() {
        assertEquals(0, CallPick.primary(listOf(Call.STATE_HOLDING)))
    }

    @Test fun nothingLeft() {
        assertNull(CallPick.primary(emptyList()))
        assertNull(CallPick.primary(listOf(Call.STATE_DISCONNECTED)))
    }

    @Test fun audioMovesOnHandsetSpeakerBluetooth() {
        val e = android.telecom.CallEndpoint.TYPE_EARPIECE
        val s = android.telecom.CallEndpoint.TYPE_SPEAKER
        val b = android.telecom.CallEndpoint.TYPE_BLUETOOTH
        val w = android.telecom.CallEndpoint.TYPE_WIRED_HEADSET
        // Only handset and speaker: a plain on/off.
        assertEquals(s, AudioCycle.next(e, listOf(e, s)))
        assertEquals(e, AudioCycle.next(s, listOf(e, s)))
        // With Bluetooth: handset → speaker → Bluetooth → handset.
        assertEquals(b, AudioCycle.next(s, listOf(e, s, b)))
        assertEquals(e, AudioCycle.next(b, listOf(e, s, b)))
        // A plugged-in headset takes the handset's place.
        assertEquals(w, AudioCycle.next(s, listOf(w, s)))
        // Bluetooth gone while in use: back to the handset.
        assertEquals(e, AudioCycle.next(b, listOf(e, s)))
    }

    @Test fun outgoingStates() {
        assertTrue(CallPick.isOutgoing(Call.STATE_DIALING))
        assertTrue(CallPick.isOutgoing(Call.STATE_CONNECTING))
        assertFalse(CallPick.isOutgoing(Call.STATE_ACTIVE))
        assertFalse(CallPick.isOutgoing(Call.STATE_RINGING))
    }
}
