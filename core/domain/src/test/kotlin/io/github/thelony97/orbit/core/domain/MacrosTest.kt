package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.domain.Macros.Key
import io.github.thelony97.orbit.core.domain.Macros.Play
import io.github.thelony97.orbit.core.model.CustomField
import io.github.thelony97.orbit.core.model.MacroStep
import io.github.thelony97.orbit.core.model.MacroStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacrosTest {
    private val fields = listOf(CustomField("f1", "Kundennummer", "4821 77"))
    private val name: (String) -> String? = { id -> fields.firstOrNull { it.id == id }?.label }
    private val value: (String) -> String? = { id -> fields.firstOrNull { it.id == id }?.value }

    private fun keys(vararg k: Pair<Char, Long>) = k.map { Key(it.first, it.second) }

    private val typed = keys(
        '1' to 4_200, '3' to 9_100,
        '4' to 15_000, '8' to 15_200, '2' to 15_400, '1' to 15_600, '7' to 15_800, '7' to 16_000, '#' to 16_300,
    )

    @Test fun recordingBecomesStepsWithPausesAndAField() {
        val steps = Macros.record(typed, fields)
        assertEquals(
            listOf(
                MacroStep(MacroStepKind.PAUSE, ms = 4_000), MacroStep(MacroStepKind.KEY, "1"),
                MacroStep(MacroStepKind.PAUSE, ms = 5_000), MacroStep(MacroStepKind.KEY, "3"),
                MacroStep(MacroStepKind.PAUSE, ms = 6_000), MacroStep(MacroStepKind.FIELD, "f1"), MacroStep(MacroStepKind.KEY, "#"),
            ),
            steps,
        )
        assertEquals("4 s · 1 · 5 s · 3 · 6 s · Kundennummer · #", Macros.label(steps, name))
    }

    @Test fun dialStringForThePhonesOwnDialer() {
        val steps = Macros.record(typed, fields) + MacroStep(MacroStepKind.WAIT) + MacroStep(MacroStepKind.KEY, "0")
        assertEquals(",,1,,,3,,,482177#;0", Macros.dialString(steps, value))
    }

    @Test fun planTypesFieldDigitsAsTones() {
        val plan = Macros.plan(Macros.record(typed, fields), value)
        assertEquals(Play.Wait(4_000), plan.first())
        assertEquals(6, plan.count { it is Play.Tone && it.fromField })
        assertEquals(Play.Tone('#', fromField = false), plan.last())
    }

    @Test fun caseNumbersTypedInTheCall() {
        // PLAN 9 B7: „Vorgang 123456“ – a long digit run that is not an own field.
        val keys = keys('1' to 2_000, '1' to 6_000, '2' to 6_100, '3' to 6_200, '4' to 6_300, '5' to 6_400, '6' to 6_500, '#' to 6_700) + typed.map { it.copy(at = it.at + 10_000) }
        assertEquals(listOf("123456"), Macros.cases(keys, fields))
        assertEquals(emptyList<String>(), Macros.cases(typed, fields))
    }

    @Test fun noFieldForShortOrOtherDigits() {
        val steps = Macros.record(keys('1' to 3_000, '2' to 3_200), fields)
        assertEquals(listOf("PAUSE", "KEY", "KEY"), steps.map { it.kind.name })
        assertTrue(Macros.worthKeeping(keys('1' to 1, '2' to 2)))
        assertFalse(Macros.worthKeeping(keys('1' to 1)))
    }
}
