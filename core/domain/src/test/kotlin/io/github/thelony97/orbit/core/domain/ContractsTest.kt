package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.ContactKind
import io.github.thelony97.orbit.core.model.Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ContractsTest {

    private val handy = Contract("h", "Handy", "0815", start = "2025-03-01", minMonths = 24, renewMonths = 12, noticeMonths = 3)

    @Test fun firstTermDeadline() {
        val t = Contracts.term(handy, LocalDate.of(2026, 10, 15))!!
        assertEquals(LocalDate.of(2025, 3, 1), t.start)
        assertEquals(LocalDate.of(2027, 3, 1), t.end)
        assertEquals(LocalDate.of(2026, 12, 1), t.deadline)
        assertEquals(47L, t.days)
        assertFalse(t.inWindow)
    }

    @Test fun deadlineMissedMovesToNextTerm() {
        val t = Contracts.term(handy, LocalDate.of(2026, 12, 2))!!
        assertEquals(LocalDate.of(2027, 3, 1), t.start)
        assertEquals(LocalDate.of(2028, 3, 1), t.end)
        assertEquals(LocalDate.of(2027, 12, 1), t.deadline)
    }

    @Test fun deadlineDayItselfStillCounts() {
        val t = Contracts.term(handy, LocalDate.of(2026, 12, 1))!!
        assertEquals(0L, t.days)
        assertTrue(t.inWindow)
    }

    @Test fun indefiniteMonthly() {
        val strom = Contract("s", "Strom", start = "2024-01-15", renewMonths = 0, noticeDays = 14)
        val t = Contracts.term(strom, LocalDate.of(2026, 10, 15))!!
        assertEquals(LocalDate.of(2026, 11, 15), t.end)
        assertEquals(LocalDate.of(2026, 11, 1), t.deadline)
    }

    @Test fun cancelledKeepsItsTerm() {
        val c = handy.copy(cancelledOn = "2026-11-20")
        val t = Contracts.term(c, LocalDate.of(2026, 12, 20))!!
        assertTrue(t.cancelled)
        assertNull(t.deadline)
        assertEquals(LocalDate.of(2027, 3, 1), t.end)
        assertFalse(t.over)
        assertTrue(Contracts.term(c, LocalDate.of(2027, 3, 1))!!.over)
    }

    @Test fun noStartNoTerm() = assertNull(Contracts.term(Contract("x"), LocalDate.of(2026, 1, 1)))

    @Test fun progressAndMarks() {
        val t = Contracts.term(handy, LocalDate.of(2026, 3, 1))!!
        assertEquals(0.5f, t.progress(LocalDate.of(2026, 3, 1)), 0.01f)
        assertTrue(t.windowAt()!! < t.deadlineAt()!!)
    }

    @Test fun dueWithinLead() {
        val org = Contact("o", kind = ContactKind.ORG, company = "A1", contracts = listOf(handy))
        assertEquals(1, Contracts.due(listOf(org), LocalDate.of(2026, 11, 20)).size)
        assertTrue(Contracts.due(listOf(org), LocalDate.of(2026, 10, 1)).isEmpty())
    }

    @Test fun mail() {
        val (subject, body) = Contracts.cancelMail(handy, LocalDate.of(2027, 3, 1), "Jakob Lonauer")
        assertEquals("Kündigung Handy Nr. 0815", subject)
        assertTrue("zum 1.3.2027" in body)
        assertTrue(body.endsWith("Jakob Lonauer"))
    }

    @Test fun lines() {
        assertEquals("heute kündigen", Contracts.deadlineLine(0, LocalDate.of(2026, 12, 1)))
        assertEquals("kündigen bis 1.12.", Contracts.deadlineLine(9, LocalDate.of(2026, 12, 1)))
    }
}
