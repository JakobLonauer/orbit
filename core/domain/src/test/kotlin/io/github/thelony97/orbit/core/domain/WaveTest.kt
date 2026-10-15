package io.github.thelony97.orbit.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveTest {
    @Test fun roundTrip() {
        val w = Wave.encode(List(200) { if (it % 2 == 0) 32_767 else 0 })
        assertTrue(w.length <= Wave.MAX)
        assertTrue(w.all { it == '9' })
        assertEquals(listOf(0f, 1f), Wave.decode("09"))
        assertEquals("", Wave.encode(emptyList()))
    }
}
