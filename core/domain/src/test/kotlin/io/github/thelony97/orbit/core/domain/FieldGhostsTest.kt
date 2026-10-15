package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.HistoryEntry
import io.github.thelony97.orbit.core.model.LabeledValue
import io.github.thelony97.orbit.core.model.SmsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldGhostsTest {
    private val norm: (String) -> String? = { raw ->
        val d = raw.filter { it.isDigit() || it == '+' }
        when {
            d.startsWith("+") -> d
            d.startsWith("00") -> "+" + d.drop(2)
            d.startsWith("0") -> "+43" + d.drop(1)
            else -> null
        }
    }

    private val anna = Contact(
        "a", firstName = "Anna", phones = listOf(LabeledValue("", "0664 1234567")),
        entries = listOf(
            HistoryEntry(
                "e", "a", "message", 100,
                messages = listOf(
                    SmsMessage(1, 100, true, "Neue Nummer: 0676 987 65 43, alte geht noch. Mail anna.neu@gmx.at"),
                    SmsMessage(2, 90, true, "Ruf mich an: +43 664 1234567"),
                    SmsMessage(3, 80, false, "Meine: 0699 11111111"),
                    SmsMessage(4, 70, true, "Wir wohnen jetzt Hauptstraße 12, 4020 Linz"),
                ),
            ),
        ),
    )

    @Test fun findsNewOnly() {
        val g = FieldGhosts.find(anna, norm, emptySet())
        assertEquals(listOf("0676 987 65 43", "anna.neu@gmx.at", "Hauptstraße 12, 4020 Linz"), g.map { it.value })
        assertTrue(g.first().source.contains("Neue Nummer"))
    }

    @Test fun dismissedStaysAway() {
        val first = FieldGhosts.find(anna, norm, emptySet()).first()
        assertEquals(2, FieldGhosts.find(anna, norm, setOf(first.key("a"))).size)
    }
}
