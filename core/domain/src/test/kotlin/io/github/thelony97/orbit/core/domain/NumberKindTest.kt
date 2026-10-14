package io.github.thelony97.orbit.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberKindTest {
    @Test fun fixedRanges() {
        val table = listOf(
            "+437322468" to true, // Linz
            "+4312345678" to true, // Wien
            "+436601234567" to false, // A1/Magenta/Drei …
            "+436991234567" to false,
            "+49301234567" to true, // Berlin
            "+491701234567" to false,
            "+491511234567" to false,
            "+41441234567" to true, // Zürich
            "+41791234567" to false,
            "+390612345678" to false, // unknown country: maybe mobile
            "07322468" to false, // not E.164
            "" to false,
            null to false,
        )
        table.forEach { (n, want) -> assertEquals(n.toString(), want, NumberKind.isLandline(n)) }
    }
}
