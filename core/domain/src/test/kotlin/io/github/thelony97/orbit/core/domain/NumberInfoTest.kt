package io.github.thelony97.orbit.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberInfoTest {

    @Test fun austrianLandlineHasItsTown() {
        val i = NumberInfo.of("0732 555123", "AT")!!
        assertEquals(NumberInfo.Kind.LANDLINE, i.kind)
        assertTrue(i.place, i.place.contains("Linz"))
        assertEquals("", i.country)
        assertEquals("+43732555123", i.e164)
        assertTrue(i.line, i.line.endsWith("· Festnetz"))
        assertFalse(i.costs)
    }

    @Test fun viennaFromE164() {
        val i = NumberInfo.of("+43 1 5551234", "AT")!!
        assertEquals(NumberInfo.Kind.LANDLINE, i.kind)
        assertTrue(i.place, i.place.contains("Wien"))
    }

    @Test fun mobileAtHomeAndAbroad() {
        assertEquals("Mobil", NumberInfo.of("0664 1234567", "AT")!!.line)
        val de = NumberInfo.of("+49 171 1234567", "AT")!!
        assertEquals(NumberInfo.Kind.MOBILE, de.kind)
        assertEquals("Deutschland", de.country)
        assertEquals("Mobil · Deutschland", de.line)
        assertTrue(de.abroad)
    }

    @Test fun serviceNumbersSayWhatTheyCost() {
        val free = NumberInfo.of("0800 123456", "AT")!!
        assertEquals(NumberInfo.Kind.FREE, free.kind)
        assertEquals("0800 · gratis", free.line)
        assertFalse(free.costs)
        val premium = NumberInfo.of("0900 123456", "AT")!!
        assertEquals(NumberInfo.Kind.PREMIUM, premium.kind)
        assertEquals("0900 · teuer", premium.line)
        assertTrue(premium.costs)
        val shared = NumberInfo.of("0810 123456", "AT")!!
        assertEquals(NumberInfo.Kind.SHARED_COST, shared.kind)
        assertTrue(shared.costs)
        assertEquals("0810 · kostet extra", shared.line)
    }

    @Test fun foreignLandline() {
        val i = NumberInfo.of("+49 30 1234567", "AT")!!
        assertEquals(NumberInfo.Kind.LANDLINE, i.kind)
        assertEquals("Deutschland", i.country)
        assertTrue(i.line, i.line.startsWith("Berlin") && i.line.endsWith("· Deutschland"))
    }

    @Test fun notANumber() {
        assertNull(NumberInfo.of("abc", "AT"))
        assertNull(NumberInfo.of("12", "AT"))
        assertNull(NumberInfo.of(null, "AT"))
    }

    @Test fun lineRules() {
        assertEquals("Festnetz", NumberInfo.line(NumberInfo.Kind.LANDLINE, "", "", ""))
        assertEquals("Festnetz · Italien", NumberInfo.line(NumberInfo.Kind.LANDLINE, "", "Italien", ""))
        assertEquals("0720 · ortsunabhängig", NumberInfo.line(NumberInfo.Kind.VOIP, "", "", "0720"))
        assertEquals("Serbien", NumberInfo.line(NumberInfo.Kind.UNKNOWN, "", "Serbien", ""))
    }

    @Test fun pingSuspect() {
        val abroad = NumberInfo.of("+381 11 1234567", "AT")
        assertTrue(NumberInfo.pingSuspect(abroad, known = false, missed = true))
        assertFalse(NumberInfo.pingSuspect(abroad, known = true, missed = true))
        assertFalse(NumberInfo.pingSuspect(abroad, known = false, missed = false))
        assertFalse(NumberInfo.pingSuspect(NumberInfo.of("0732 555123", "AT"), known = false, missed = true))
    }
}
