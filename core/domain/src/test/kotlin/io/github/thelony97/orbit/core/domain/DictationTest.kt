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

    @Test fun sixSignsStayWhole() {
        // SV-Nr.: the birth date part is read in one go.
        assertEquals(listOf("1234", "170597"), Dictation.blocks("1234 170597"))
        assertEquals(listOf("1234", "5678", "9"), Dictation.blocks("123456789"))
    }

    @Test fun signsStayVisible() {
        assertEquals(
            listOf(Dictation.Block("WS", '-'), Dictation.Block("55", '-'), Dictation.Block("1029", '-'), Dictation.Block("3")),
            Dictation.pieces("WS-55-1029-3"),
        )
        assertEquals(listOf(Dictation.Block("RB", '-'), Dictation.Block("100"), Dictation.Block("2834")), Dictation.pieces("RB-100 2834"))
        assertEquals(listOf(Dictation.Block("AB", '-'), Dictation.Block("12")), Dictation.pieces("AB -12"))
        // A long piece keeps its sign on the last part only.
        assertEquals(
            listOf(Dictation.Block("1234"), Dictation.Block("5678", '/'), Dictation.Block("9")),
            Dictation.pieces("12345678/9"),
        )
    }

    @Test fun spelledLettersOnly() {
        assertEquals(listOf('K' to "Konrad", 'A' to "Anton"), Dictation.spelled("K-1a"))
    }
}
