package io.github.thelony97.orbit.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ErrorLogTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun logsWithStackAndClears() {
        val log = ErrorLog(File(tmp.root, "logs/errors.txt"))
        log.log("Mail-Abruf", IllegalStateException("kaputt"))
        val text = log.read()
        assertTrue(text.contains("Mail-Abruf"))
        assertTrue(text.contains("IllegalStateException: kaputt"))
        log.clear()
        assertEquals("", log.read())
    }

    @Test fun staysBelowCap() {
        val log = ErrorLog(File(tmp.root, "e.txt"), maxBytes = 2_000)
        repeat(200) { log.log("Eintrag $it") }
        val text = log.read()
        assertTrue(text.length <= 2_000)
        assertTrue(text.contains("Eintrag 199"))
        assertFalse(text.contains("Eintrag 0\n"))
    }
}
