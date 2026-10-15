package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.CalendarDay
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.Occasion
import io.github.thelony97.orbit.core.model.OccasionKind
import io.github.thelony97.orbit.core.model.OrbitDoc
import io.github.thelony97.orbit.core.model.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalendarSyncTest {
    private val today = LocalDate.of(2026, 10, 15)
    private val anna = Contact(
        "anna", firstName = "Anna",
        dates = listOf(
            Occasion("b", OccasionKind.BIRTHDAY, date = CalendarDay(3, 2, 1990)),
            Occasion("x", OccasionKind.CUSTOM, label = "Umzug", date = CalendarDay(1, 12, 2026), repeat = Repeat.ONCE),
            Occasion("y", OccasionKind.CUSTOM, label = "Ohne Jahr", date = CalendarDay(1, 12), repeat = Repeat.ONCE),
        ),
    )
    private val old = Contact("old", firstName = "Alt", archived = true, dates = listOf(Occasion("b2", OccasionKind.BIRTHDAY, date = CalendarDay(1, 1))))
    private val doc = OrbitDoc(contacts = listOf(anna, old))
    private fun name(id: String) = if (id == "anna") "Anna" else id

    @Test fun eventsOfVisibleContactsOnly() {
        val items = CalendarSync.items(doc, today, ::name)
        assertEquals(listOf("anna/b", "anna/x"), items.map { it.key })
        // Yearly: this year's day (already past) with a yearly rule; one-off: its own day.
        assertEquals(LocalDate.of(2026, 2, 3), items[0].day)
        assertEquals("FREQ=YEARLY", items[0].rrule)
        assertEquals(LocalDate.of(2026, 12, 1), items[1].day)
        assertEquals(null, items[1].rrule)
    }

    @Test fun planFollowsTheItems() {
        val items = CalendarSync.items(doc, today, ::name)
        val existing = listOf(
            CalendarSync.Existing(1, "anna/b", items[0].hash),
            CalendarSync.Existing(2, "anna/x", "old"),
            CalendarSync.Existing(3, "gone/z", "h"),
            CalendarSync.Existing(4, null, null),
        )
        val plan = CalendarSync.plan(items, existing)
        assertTrue(plan.insert.isEmpty())
        assertEquals(listOf(2L), plan.update.map { it.first })
        assertEquals(setOf(3L, 4L), plan.delete.toSet())
    }
}
