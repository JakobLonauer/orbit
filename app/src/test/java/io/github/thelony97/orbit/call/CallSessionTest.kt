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

    @Test fun outgoingStates() {
        assertTrue(CallPick.isOutgoing(Call.STATE_DIALING))
        assertTrue(CallPick.isOutgoing(Call.STATE_CONNECTING))
        assertFalse(CallPick.isOutgoing(Call.STATE_ACTIVE))
        assertFalse(CallPick.isOutgoing(Call.STATE_RINGING))
    }
}
