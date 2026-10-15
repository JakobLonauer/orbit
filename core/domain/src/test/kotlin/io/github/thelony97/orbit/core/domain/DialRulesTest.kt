package io.github.thelony97.orbit.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DialRulesTest {

    @Test fun athomeNothingChanges() {
        assertEquals("0664 1234567", DialRules.dial("0664 1234567", "at", "at"))
        assertEquals("0664 1234567", DialRules.dial("0664 1234567", "at", null))
    }

    @Test fun abroadNationalNumbersGetTheCountryCode() {
        assertEquals("+436641234567", DialRules.dial("0664 1234567", "at", "de"))
        assertEquals("+43732555123", DialRules.dial("0732 555123", "AT", "IT"))
        // Already international, or a short code: untouched.
        assertEquals("+49 30 123456", DialRules.dial("+49 30 123456", "at", "de"))
        assertEquals("0049 30 123456", DialRules.dial("0049 30 123456", "at", "de"))
        assertEquals("112", DialRules.dial("112", "at", "de"))
    }

    @Test fun hiddenNumberGoesOutWith31() {
        assertEquals("#31#0664 1234567", DialRules.dial("0664 1234567", "at", "at", hide = true))
        assertEquals("#31#+436641234567", DialRules.dial("0664 1234567", "at", "de", hide = true))
        assertEquals("#31#0664", DialRules.dial("#31#0664", "at", "at", hide = true))
    }
}
