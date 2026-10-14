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

    @Test fun giftIsOneEntryPerYearAndCanBeChangedOrRemoved() {
        val d1 = Mutations.setGift(doc, "anna", "bd", 2026, " Buch ", listOf("tom"), at = 7L)
        val g = BirthdayTab.gift(d1.contacts.first { it.id == "anna" }, "bd", 2026)!!
        assertEquals("Buch", g.note)
        assertEquals(listOf("tom"), g.withIds)
        val d2 = Mutations.setGift(d1, "anna", "bd", 2026, "Gutschein", emptyList(), at = 8L)
        val a2 = d2.contacts.first { it.id == "anna" }
        assertEquals("Gutschein", BirthdayTab.gift(a2, "bd", 2026)!!.note)
        assertEquals(1, a2.entries.count { it.typeId == BuiltinTypes.GIFT })
        val d3 = Mutations.setGift(d2, "anna", "bd", 2026, "  ", emptyList(), at = 9L)
        assertEquals(listOf(call, lastYear), d3.contacts.first { it.id == "anna" }.entries)
    }

    @Test fun coGiversMostRecentFirstWithoutArchived() {
        val bea = Contact(id = "bea", firstName = "Bea")
        val alt = Contact(id = "alt", firstName = "Alt", archived = true)
        val gifts = listOf(
            Entry(id = "x1", contactId = "anna", typeId = BuiltinTypes.GIFT, at = 1L, withIds = listOf("tom")),
            Entry(id = "x2", contactId = "anna", typeId = BuiltinTypes.GIFT, at = 2L, withIds = listOf("bea", "alt")),
        )
        val contacts = listOf(anna.copy(entries = gifts), Contact(id = "tom", firstName = "Tom"), bea, alt)
        assertEquals(listOf("bea", "tom"), BirthdayTab.coGivers(contacts))
    }

    @Test fun giftHistoryNewestYearFirst() {
        val gifts = listOf(
            Entry(id = "g1", contactId = "anna", typeId = BuiltinTypes.GIFT, at = 1L, note = "Gutschein", occasionYear = 2024),
            Entry(id = "g2", contactId = "anna", typeId = BuiltinTypes.GIFT, at = 2L, note = "Buch", occasionYear = 2025, withIds = listOf("tom", "weg")),
        )
        val names = mapOf("tom" to "Tom")
        assertEquals("2025: Buch (mit Tom) · 2024: Gutschein", BirthdayTab.giftHistory(anna.copy(entries = gifts)) { names[it] })
        assertEquals("", BirthdayTab.giftHistory(anna) { names[it] })
    }

    @Test fun markNeverTakesTheTickAway() {
        val once = Mutations.markCongrats(doc, "anna", "bd", 2026)
        val twice = Mutations.markCongrats(once, "anna", "bd", 2026)
        assertTrue(BirthdayTab.congratulated(twice.contacts.first { it.id == "anna" }, "bd", 2026))
        assertEquals(3, twice.contacts.first { it.id == "anna" }.entries.size)
        // Newest first, like every Verlauf.
        assertEquals(BuiltinTypes.CONGRATS, twice.contacts.first { it.id == "anna" }.entries.first().typeId)
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
