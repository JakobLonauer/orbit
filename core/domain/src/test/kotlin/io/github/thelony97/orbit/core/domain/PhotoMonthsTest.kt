package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Attachment
import io.github.thelony97.orbit.core.model.Contact
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class PhotoMonthsTest {
    private val zone = ZoneId.of("Europe/Vienna")
    private fun at(y: Int, m: Int, d: Int) = java.time.LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000
    private fun a(id: String, file: String, added: Long, taken: Long? = null, mime: String = "image/jpeg") = Attachment(id, "", file, mime, added, takenAt = taken)

    @Test fun byTakenDateNewestFirstSharedOnce() {
        val anna = Contact(id = "anna", gallery = listOf(
            a("1", "a.jpg", added = at(2026, 10, 14), taken = at(2025, 7, 1)),
            a("2", "b.jpg", added = at(2026, 10, 2)),
            a("3", "doc.pdf", added = at(2026, 10, 3), mime = "application/pdf"),
        ))
        val tom = Contact(id = "tom", gallery = listOf(a("4", "b.jpg", added = at(2026, 10, 5)), a("5", "c.jpg", added = at(2026, 10, 9))))
        val alt = Contact(id = "alt", archived = true, gallery = listOf(a("6", "x.jpg", added = at(2026, 10, 10))))
        val months = PhotoMonths.build(listOf(anna, tom, alt), zone) { !it.archived }
        assertEquals(listOf("2026-10" to "Oktober 2026", "2025-07" to "Juli 2025"), months.map { it.key to it.title })
        assertEquals(listOf("c.jpg", "b.jpg"), months[0].items.map { it.photo.file })
        assertEquals(listOf("a.jpg"), months[1].items.map { it.photo.file })
        assertEquals(listOf("b.jpg", "a.jpg"), PhotoMonths.newest(anna).map { it.file })
    }
}
