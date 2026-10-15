package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.ContactKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ServiceStatusTest {
    private val doc = Contact("o", kind = ContactKind.ORG, company = "Dr. Huber", openingHours = "Mo-Fr 08:00-12:00", phoneHours = "Mo-Fr 07:30-11:00")
    // Thursday 15 Oct 2026.
    private fun at(h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, 15, h, m)

    @Test fun easterAndHolidays() {
        assertEquals(LocalDate.of(2026, 4, 5), Holidays.easter(2026))
        assertEquals(LocalDate.of(2025, 4, 20), Holidays.easter(2025))
        assertTrue(Holidays.isHoliday(LocalDate.of(2026, 10, 26)))
        assertTrue(Holidays.isHoliday(LocalDate.of(2026, 6, 4))) // Fronleichnam
        assertFalse(Holidays.isHoliday(LocalDate.of(2026, 10, 15)))
    }

    @Test fun phoneBeforeOpening() {
        assertEquals("Telefon ab 7:30", ServiceStatus.of(doc, at(7), null)!!.text)
        assertEquals("Telefon bis 11", ServiceStatus.of(doc, at(9), null)!!.text)
        assertEquals("Telefon ab Fr 7:30", ServiceStatus.of(doc, at(14), null)!!.text)
        assertEquals("offen bis 12", ServiceStatus.of(doc.copy(phoneHours = ""), at(9), null)!!.text)
        assertEquals("zu · ab Fr 8", ServiceStatus.of(doc.copy(phoneHours = ""), at(13), null)!!.text)
    }

    @Test fun holidayAndVacationWin() {
        val nationalDay = LocalDateTime.of(2026, 10, 26, 9, 0)
        assertEquals("Feiertag · zu", ServiceStatus.of(doc, nationalDay, null)!!.text)
        val away = doc.copy(vacationUntil = "2026-10-20")
        assertEquals("Urlaub bis 20.10. → Dr. Mayr", ServiceStatus.of(away, at(9), "Dr. Mayr")!!.text)
        assertEquals(ServiceStatus.Tone.AWAY, ServiceStatus.of(away, at(9), null)!!.tone)
        assertNull(ServiceStatus.of(doc.copy(openingHours = "", phoneHours = ""), at(9), null))
    }
}
