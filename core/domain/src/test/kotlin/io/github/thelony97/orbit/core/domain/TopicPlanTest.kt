package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.CheckItem
import io.github.thelony97.orbit.core.model.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TopicPlanTest {
    private val today = LocalDate.of(2026, 10, 14)
    private fun t(id: String, on: String?, done: Boolean = false) = CheckItem(id, id, done = done, remindOn = on)

    private val anna = Contact(
        id = "anna", firstName = "Anna",
        topics = listOf(t("heute", "2026-10-14"), t("morgen", "2026-10-15"), t("ohne", null), t("erledigt", "2026-10-14", done = true), t("kaputt", "14.10.")),
    )
    private val alt = Contact(id = "alt", archived = true, topics = listOf(t("arch", "2026-10-14")))
    private val visible = { c: Contact -> !c.archived && c.deletedAt == null }

    @Test fun onlyOpenTopicsOfTodayFromVisibleContacts() {
        val due = TopicPlan.due(listOf(anna, alt), today, lastRun = today.minusDays(1), visible)
        assertEquals(listOf("heute"), due.map { it.topic.id })
    }

    @Test fun missedMorningsAreCaughtUpForAWeek() {
        val c = Contact(id = "c", topics = listOf(t("vor3", "2026-10-11"), t("vor9", "2026-10-05"), t("heute", "2026-10-14")))
        assertEquals(listOf("vor3", "heute"), TopicPlan.due(listOf(c), today, lastRun = LocalDate.of(2026, 10, 1), visible).map { it.topic.id })
        // Already shown yesterday: not again.
        assertEquals(listOf("heute"), TopicPlan.due(listOf(c), today, lastRun = today.minusDays(1), visible).map { it.topic.id })
        // Never ran: today only.
        assertEquals(listOf("heute"), TopicPlan.due(listOf(c), today, lastRun = null, visible).map { it.topic.id })
    }

    /** Topics written before the field existed read with no date and never remind. */
    @Test fun oldTopicsHaveNoDay() {
        val old = """{"schema":2,"contacts":[{"id":"anna","firstName":"Anna","topics":[{"id":"t1","text":"Urlaub","createdAt":5}]}]}"""
        val topic = DocJson.decode(old).contacts.single().topics.single()
        assertNull(topic.remindOn)
        assertEquals("Urlaub", topic.text)
        assertEquals(emptyList<TopicPlan.Due>(), TopicPlan.due(DocJson.decode(old).contacts, today, null) { true })
        val back = DocJson.decode(DocJson.encode(DocJson.decode(old).let { d -> d.copy(contacts = d.contacts.map { c -> c.copy(topics = c.topics.map { it.copy(remindOn = "2026-10-14") }) }) }))
        assertEquals("2026-10-14", back.contacts.single().topics.single().remindOn)
    }
}
