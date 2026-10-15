package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Meeting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveMeetingTest {
    private val min = 60_000L
    private val now = 1_000_000_000L

    @Test fun runningBeforeSoon() {
        val on = Meeting("a", "anna", now - 10 * min, now + 25 * min + 1)
        val soon = Meeting("b", "tom", now + 12 * min, now + 70 * min)
        assertEquals(LiveMeeting.Phase.On(on, 26), LiveMeeting.now(listOf(soon, on), now) { true })
        assertEquals(LiveMeeting.Phase.Soon(soon, 12), LiveMeeting.now(listOf(soon), now) { true })
    }

    @Test fun farOrDoneIsNothing() {
        assertNull(LiveMeeting.now(listOf(Meeting("c", "x", now + 31 * min, now + 90 * min)), now) { true })
        assertNull(LiveMeeting.now(listOf(Meeting("d", "x", now - 60 * min, now - 1)), now) { true })
        assertNull(LiveMeeting.now(listOf(Meeting("e", "x", now - min, now + min, resolved = true)), now) { true })
        assertNull(LiveMeeting.now(listOf(Meeting("f", "hidden", now - min, now + min)), now) { it != "hidden" })
    }

    @Test fun dragSnaps() {
        assertEquals(0, LiveMeeting.stepFor(20f))
        assertEquals(5, LiveMeeting.stepFor(40f))
        assertEquals(10, LiveMeeting.stepFor(80f))
        assertEquals(15, LiveMeeting.stepFor(500f))
        assertEquals("Bin ca. 10 Min später da.", LiveMeeting.message(10))
    }
}
