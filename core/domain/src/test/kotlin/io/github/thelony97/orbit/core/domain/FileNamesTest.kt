package io.github.thelony97.orbit.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNamesTest {
    @Test fun onlyTheLastPartSurvives() {
        val table = listOf(
            "../../files/orbit.json" to "orbit.json",
            "..\\..\\orbit.json" to "orbit.json",
            "Befund 2026.pdf" to "Befund 2026.pdf",
            ".." to "x.pdf",
            "." to "x.pdf",
            "" to "x.pdf",
            null to "x.pdf",
            "a/b/" to "x.pdf",
            "C:Rechnung.pdf" to "CRechnung.pdf",
        )
        table.forEach { (input, want) -> assertEquals(input.toString(), want, FileNames.safe(input, "x.pdf")) }
        assertEquals(120, FileNames.safe("a".repeat(300), "x").length)
    }
}
