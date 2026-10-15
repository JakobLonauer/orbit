package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.CalendarDay
import io.github.thelony97.orbit.core.model.CheckItem
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.HistoryEntry
import io.github.thelony97.orbit.core.model.Meeting
import io.github.thelony97.orbit.core.model.Occasion
import io.github.thelony97.orbit.core.model.OccasionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TodayPlanTest {
    private val zone = ZoneId.of("Europe/Vienna")
    private val today = LocalDate.of(2026, 10, 15)
    private fun at(d: LocalDate, h: Int) = d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
    private val now = at(today, 10)

    private val me = Contact("me", isMe = true, firstName = "Jakob", dates = listOf(Occasion("b0", OccasionKind.BIRTHDAY, date = CalendarDay(15, 10, 1997))))
    private val anna = Contact(
        "anna", firstName = "Anna",
        dates = listOf(Occasion("b1", OccasionKind.BIRTHDAY, date = CalendarDay(15, 10, 1996))),
        topics = listOf(
            CheckItem("t1", "Wohnung", remindOn = "2026-10-14"),
            CheckItem("t2", "Konzert", remindOn = "2026-10-20"),
            CheckItem("t3", "Alt", remindOn = "2026-09-01"),
            CheckItem("t4", "Erledigt", done = true, remindOn = "2026-10-15"),
        ),
        entries = listOf(
            HistoryEntry("e1", "anna", "call", at(today.minusYears(1), 9)),
            HistoryEntry("e2", "anna", "meet", at(today.minusYears(3), 18), note = "Konzert in Wien"),
        ),
    )
    private val tom = Contact("tom", firstName = "Tom", archived = true, dates = listOf(Occasion("b2", OccasionKind.BIRTHDAY, date = CalendarDay(15, 10, 1990))))
    private val meetings = listOf(
        Meeting("m1", "anna", at(today, 14), at(today, 15), "Café"),
        Meeting("m2", "anna", at(today, 8), at(today, 9)),
        Meeting("m3", "anna", at(today.plusDays(1), 14), at(today.plusDays(1), 15)),
    )

    private fun build(done: Set<String> = emptySet()) =
        TodayPlan.build(listOf(me, anna, tom), meetings, now, zone, done) { !it.archived && it.deletedAt == null }

    @Test fun whatStandsToday() {
        val items = build()
        assertEquals(listOf("meet:m1", "occ:anna:b1:2026-10-15", "topic:t1", "ago:e2:2026-10-15"), items.map { it.key })
        val ago = items.last() as TodayPlan.YearsAgo
        assertEquals(3, ago.years)
        assertEquals(30, (items[1] as TodayPlan.Occasion).turning)
    }

    @Test fun doneCardsStayAway() {
        assertFalse(build(setOf("topic:t1", "meet:m1")).any { it.key == "topic:t1" || it.key == "meet:m1" })
    }

    @Test fun staleKeys() {
        assertTrue(TodayPlan.stale("occ:anna:b1:2026-10-10", today))
        assertFalse(TodayPlan.stale("occ:anna:b1:2026-10-14", today))
        assertFalse(TodayPlan.stale("phonebook-offer", today))
    }
}
