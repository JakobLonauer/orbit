package io.github.thelony97.orbit.data

import io.github.thelony97.orbit.core.domain.BirthdayTab
import io.github.thelony97.orbit.core.model.BuiltinTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** „Gratuliert“ per tap (PLAN 3.5): one entry per occasion and year, a second tap removes only that one. */
class CongratsTest {
    private val call = Entry(id = "c1", contactId = "anna", typeId = TypeIds.CALL, at = 5L)
    private val lastYear = Entry(id = "g25", contactId = "anna", typeId = BuiltinTypes.CONGRATS, at = 1L, occasionId = "bd", occasionYear = 2025)
    private val anna = Contact(id = "anna", firstName = "Anna", entries = listOf(call, lastYear))
    private val doc = OrbitData(contacts = listOf(anna, Contact(id = "tom", firstName = "Tom")))

    @Test fun tickAddsOneEntryForThisYear() {
        val d = Mutations.toggleCongrats(doc, "anna", "bd", 2026, at = 10L)
        val a = d.contacts.first { it.id == "anna" }
        assertTrue(BirthdayTab.congratulated(a, "bd", 2026))
        assertTrue(BirthdayTab.congratulated(a, "bd", 2025))
        assertEquals(3, a.entries.size)
        assertEquals(doc.contacts.first { it.id == "tom" }, d.contacts.first { it.id == "tom" })
    }

    @Test fun secondTapTakesOnlyThisYearsTickAway() {
        val once = Mutations.toggleCongrats(doc, "anna", "bd", 2026)
        val twice = Mutations.toggleCongrats(once, "anna", "bd", 2026)
        val a = twice.contacts.first { it.id == "anna" }
        assertFalse(BirthdayTab.congratulated(a, "bd", 2026))
        // Last year's tick and the call stay.
        assertEquals(listOf(call, lastYear), a.entries)
    }
}
