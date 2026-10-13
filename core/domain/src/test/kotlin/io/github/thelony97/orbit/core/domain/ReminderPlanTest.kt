package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.CalendarDay
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.Occasion
import io.github.thelony97.orbit.core.model.OccasionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReminderPlanTest {
    private val today = LocalDate.of(2026, 10, 13)
    private val anna = Contact("anna", firstName = "Anna", dates = listOf(
        Occasion("b", OccasionKind.BIRTHDAY, date = CalendarDay(13, 10, 1996), reminders = Occasion.BIRTHDAY_REMINDERS),
        Occasion("w", OccasionKind.WEDDING, date = CalendarDay(20, 10, 2021), reminders = listOf(0, 7), sharedWith = "tom"),
        Occasion("n", OccasionKind.NAME_DAY, date = CalendarDay(14, 10), reminders = emptyList()),
    ))
    private val tom = Contact("tom", firstName = "Tom")
    private val max = Contact("max", firstName = "Max", dates = listOf(
        Occasion("mb", OccasionKind.BIRTHDAY, date = CalendarDay(20, 10), reminders = Occasion.BIRTHDAY_REMINDERS),
    ))
    private val names = mapOf("anna" to "Anna", "tom" to "Tom", "max" to "Max")

    @Test fun dueTodayAndLeadTime() {
        val due = ReminderPlan.dueOn(listOf(anna, tom, max), today) { true }
        assertEquals(listOf("b", "w", "mb"), due.map { it.upcoming.occasion.id })
        assertFalse(due[0].withGiftIdeas)
        assertFalse(due[1].withGiftIdeas)
        assertTrue(due[2].withGiftIdeas)
        assertEquals(
            "Heute: Annas 30. Geburtstag · In 7 Tagen: 5. Hochzeitstag Anna & Tom · In 7 Tagen: Max' Geburtstag",
            ReminderPlan.digestLine(due) { names.getValue(it) },
        )
    }

    @Test fun genitive() {
        assertEquals("Annas", ReminderPlan.genitive("Anna"))
        assertEquals("Hans'", ReminderPlan.genitive("Hans"))
        assertEquals("Fritz'", ReminderPlan.genitive("Fritz"))
    }
}
