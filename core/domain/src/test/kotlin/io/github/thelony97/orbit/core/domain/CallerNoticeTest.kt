package io.github.thelony97.orbit.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CallerNoticeTest {
    private val zone = ZoneId.of("Europe/Vienna")
    private val now = LocalDate.of(2026, 10, 13).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
    private val threeDaysAgo = LocalDate.of(2026, 10, 10).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun fullCard() {
        val n = CallerNotice.of(
            CallerCard("a", "Peter Lonauer", role = "Patient:in · Arzt Müller", note = "Fragt nach dem Auto",
                customerNo = "P-77", lastAt = threeDaysAgo, lastKind = "Anruf", topics = listOf("Urlaub", "Garten")),
            now, zone,
        )
        assertEquals("Peter Lonauer", n.title)
        assertEquals("Patient:in · Arzt Müller · zuletzt Anruf vor 3 Tagen", n.line)
        assertEquals(listOf("Fragt nach dem Auto", "Ansprechen: Urlaub · Garten", "Kundennummer P-77"), n.details)
    }

    @Test fun lockScreenShowsOnlyNameAndRole() {
        val n = CallerNotice.of(CallerCard("a", "Anna", role = "Lehrerin", note = "geheim", customerNo = "1", topics = listOf("x")), now, zone)
        assertEquals("Anna", n.publicTitle)
        assertEquals("Lehrerin", n.publicLine)
        assertFalse(n.publicTitle.contains("geheim") || n.publicLine.contains("geheim"))
    }

    @Test fun archivedAndBare() {
        val n = CallerNotice.of(CallerCard("k", "Kevin", archived = true), now, zone)
        assertEquals("Kevin · archiviert", n.title)
        assertEquals("", n.line)
        assertEquals(emptyList<String>(), n.details)
        val today = CallerNotice.of(CallerCard("a", "Anna", lastAt = now - 3_600_000, lastKind = "Treffen"), now, zone)
        assertEquals("zuletzt Treffen heute", today.line)
    }
}
