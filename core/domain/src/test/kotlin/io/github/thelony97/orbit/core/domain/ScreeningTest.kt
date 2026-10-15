package io.github.thelony97.orbit.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreeningTest {
    private val rules = Screening.Rules(night = true, premium = true, countries = listOf("RS"), series = listOf("+43720"))

    @Test fun knownNumbersAreNeverTouched() {
        assertNull(Screening.rule(rules, "+43900123456", NumberInfo.of("0900 123456", "AT"), known = true, hour = 23))
        assertNull(Screening.rule(null, "+43900123456", NumberInfo.of("0900 123456", "AT"), known = false, hour = 23))
    }

    @Test fun rulesInOrder() {
        assertEquals(Screening.SERIES, Screening.rule(rules, "+43720123456", null, known = false, hour = 12))
        assertEquals(Screening.COUNTRY, Screening.rule(rules, "+381111234567", NumberInfo.of("+381 11 1234567", "AT"), known = false, hour = 12))
        assertEquals(Screening.PREMIUM, Screening.rule(rules, "+43900123456", NumberInfo.of("0900 123456", "AT"), known = false, hour = 12))
        assertEquals(Screening.NIGHT, Screening.rule(rules, "+43732555123", NumberInfo.of("0732 555123", "AT"), known = false, hour = 23))
        assertEquals(Screening.NIGHT, Screening.rule(rules, "+43732555123", NumberInfo.of("0732 555123", "AT"), known = false, hour = 6))
        assertNull(Screening.rule(rules, "+43732555123", NumberInfo.of("0732 555123", "AT"), known = false, hour = 7))
        assertNull(Screening.rule(rules.copy(night = false), "+43732555123", NumberInfo.of("0732 555123", "AT"), known = false, hour = 23))
    }

    @Test fun typedListsAreCleaned() {
        assertEquals(listOf("NG", "CI", "SN"), Screening.countriesOf("ng, CI ; sn, nigeria, NG"))
        assertEquals(listOf("0900", "+43 720"), Screening.seriesOf("0900, +43 720; 08, 0900"))
    }
}
