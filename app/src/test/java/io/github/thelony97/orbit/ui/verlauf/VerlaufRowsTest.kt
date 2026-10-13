package io.github.thelony97.orbit.ui.verlauf

import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.ContactKind
import io.github.thelony97.orbit.data.Entry
import io.github.thelony97.orbit.data.OrbitData
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class VerlaufRowsTest {
    private fun e(id: String, at: Long, type: String = "call", note: String = "", member: String? = null) =
        Entry(id = id, contactId = "", typeId = type, at = at, note = note, memberId = member)

    private val data = OrbitData(contacts = listOf(
        Contact("a", firstName = "Anna", entries = listOf(e("1", 10, note = "Kaffee"), e("2", 30, "meet"))),
        Contact("x", firstName = "Alt", archived = true, entries = listOf(e("3", 40))),
        Contact("o", kind = ContactKind.ORG, company = "LiLES", entries = listOf(e("4", 20, note = "Zählerstand Huber", member = "m1"))),
    ))

    @Test fun newestFirstWithoutArchiveAndOrgsHidden() {
        assertEquals(listOf("2", "1"), verlaufRows(data, showOrgs = false, typeId = null, query = "").map { it.entry.id })
        assertEquals(listOf("2", "4", "1"), verlaufRows(data, showOrgs = true, typeId = null, query = "").map { it.entry.id })
    }

    @Test fun filterAndSearchNeverRevealMemberNotes() {
        assertEquals(listOf("2"), verlaufRows(data, false, "meet", "").map { it.entry.id })
        assertEquals(listOf("1"), verlaufRows(data, true, null, "kaffee").map { it.entry.id })
        assertEquals(emptyList<String>(), verlaufRows(data, true, null, "huber").map { it.entry.id })
        assertEquals(listOf("4"), verlaufRows(data, true, null, "liles").map { it.entry.id })
    }

    @Test fun dayHeaders() {
        val t = LocalDate.of(2026, 10, 13)
        assertEquals("Heute", dayHeader(t, t))
        assertEquals("Gestern", dayHeader(t.minusDays(1), t))
        assertEquals("Fr., 9. Okt.", dayHeader(LocalDate.of(2026, 10, 9), t))
        assertEquals("Mo., 12. Jän.", dayHeader(LocalDate.of(2026, 1, 12), t))
        assertEquals("Sa., 12. Okt. 2024", dayHeader(LocalDate.of(2024, 10, 12), t))
    }
}
