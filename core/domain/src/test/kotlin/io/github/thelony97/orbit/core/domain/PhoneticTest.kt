package io.github.thelony97.orbit.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneticTest {

    @Test fun referenceCodes() {
        // The two examples of the published algorithm.
        assertEquals("3412", Phonetic.code("Wikipedia"))
        assertEquals("65752682", Phonetic.code("Müller-Lüdenscheidt"))
    }

    @Test fun namesThatSoundAlike() {
        val maier = Phonetic.code("Maier")
        listOf("Meier", "Mayer", "Mayr", "Meyer", "Maier").forEach { assertEquals(it, maier, Phonetic.code(it)) }
        assertEquals("67", maier)
        assertEquals(Phonetic.code("Höller"), Phonetic.code("Hoeller"))
        assertEquals(Phonetic.code("Jakob"), Phonetic.code("Jacob"))
        assertEquals(Phonetic.code("Schmidt"), Phonetic.code("Schmitt"))
        assertEquals(Phonetic.code("Schmidt"), Phonetic.code("Schmid"))
        assertEquals(Phonetic.code("Strauß"), Phonetic.code("Strauss"))
    }

    @Test fun differentNamesStayApart() {
        assertTrue(Phonetic.code("Maier") != Phonetic.code("Müller"))
        assertTrue(Phonetic.code("Huber") != Phonetic.code("Hofer"))
        assertEquals("", Phonetic.code("1234"))
    }

    @Test fun matchingNeedsFourLetters() {
        val codes = Phonetic.codes("Anna-Lena Meier")
        assertTrue(Phonetic.matches("Mayr", codes))
        assertTrue(Phonetic.matches("maier", codes))
        assertFalse(Phonetic.matches("Mai", codes)) // too short, would hit half the list
        assertFalse(Phonetic.matches("Huber", codes))
        // A longer code may be the start of a name.
        assertTrue(Phonetic.matches("Lüdensch", Phonetic.codes("Hans Müller-Lüdenscheidt")))
    }
}
