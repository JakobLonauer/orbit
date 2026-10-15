package io.github.thelony97.orbit.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DictationTest {

    @Test fun austrianAlphabet() {
        assertEquals("Anton", Dictation.word('a'))
        assertEquals("Zürich", Dictation.word('Z'))
        assertEquals("Österreich", Dictation.word('ö'))
        assertEquals("scharfes S", Dictation.word('ß'))
        assertNull(Dictation.word('7'))
    }

    @Test fun blocksKeepTheOwnGrouping() {
        assertEquals(listOf("AT61", "1904", "3002"), Dictation.blocks("AT61 1904 3002"))
        assertEquals(listOf("1234", "5678", "90"), Dictation.blocks("1234567890"))
        assertEquals(listOf("K", "12345"), Dictation.blocks("K-12345"))
        assertEquals(emptyList<String>(), Dictation.blocks("  "))
    }

    @Test fun spelledLettersOnly() {
        assertEquals(listOf('K' to "Konrad", 'A' to "Anton"), Dictation.spelled("K-1a"))
    }
}
