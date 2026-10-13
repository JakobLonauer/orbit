package io.github.thelony97.orbit.core.domain.migration

import io.github.thelony97.orbit.core.model.BuiltinTypes
import io.github.thelony97.orbit.core.model.CalendarDay
import io.github.thelony97.orbit.core.model.OccasionKind
import io.github.thelony97.orbit.core.model.OrbitDoc
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrateV1Test {

    private val text = javaClass.getResource("/v1-sample.json")!!.readText()
    private fun run(): MigrateV1.Result {
        var n = 0
        return MigrateV1.fromJson(text) { "id${n++}" }
    }

    private val r = run()
    private fun c(id: String) = r.doc.contacts.single { it.id == id }

    @Test fun schemaDetection() {
        assertEquals(1, MigrateV1.schemaOf(text))
        assertEquals(1, MigrateV1.schemaOf("""{"contacts":[]}"""))
        assertEquals(2, MigrateV1.schemaOf("""{"schema":2}"""))
        assertEquals(1, MigrateV1.schemaOf("not json"))
    }

    @Test fun keepsEveryContactAndBasics() {
        assertEquals(listOf("me", "papa", "arzt", "old"), r.doc.contacts.map { it.id })
        val p = c("papa")
        assertEquals("Peter", p.firstName)
        assertEquals("+43 660 1234567", p.phones.single().value)
        assertEquals("Hauptplatz 1, 4020 Linz", p.address)
        assertEquals(listOf("g1"), p.groupIds)
        assertTrue(p.favorite)
        assertEquals("Auto fragen", p.topics.single().text)
        assertTrue(c("me").isMe)
    }

    @Test fun birthdayAndAnniversariesBecomeOccasions() {
        val d = c("papa").dates
        assertEquals(3, d.size)
        val bd = d.first { it.kind == OccasionKind.BIRTHDAY }
        assertEquals(CalendarDay(29, 2, 1964), bd.date)
        assertEquals(listOf(0, 7), bd.reminders)
        assertEquals(OccasionKind.WEDDING, d.first { it.id == "a1" }.kind)
        val custom = d.first { it.id == "a2" }
        assertEquals(OccasionKind.CUSTOM, custom.kind)
        assertEquals("Erster Skiurlaub", custom.label)
        assertNull(custom.date.year)
        assertEquals(listOf(0), custom.reminders)
    }

    @Test fun websiteBecomesFirstLink() {
        val l = c("arzt").links.single()
        assertEquals("https://www.dr-mueller.at", l.url)
        assertTrue(c("papa").links.isEmpty())
    }

    @Test fun entriesAndGalleryMoveOut() {
        assertEquals(listOf("e1", "e2"), r.history.map { it.id })
        assertTrue(r.history.all { it.contactId == "papa" })
        assertEquals("call:991", r.history[0].externalId)
        assertEquals("Bin unterwegs", r.history[1].messages.single().text)
        val a = r.attachments.single()
        assertEquals("g_p1.jpg", a.file)
        assertEquals("papa", a.contactId)
        assertEquals("Urlaub", a.caption)
    }

    @Test fun orgRelationsBecomeAssignments() {
        assertEquals(listOf("r2"), r.doc.relations.map { it.id })
        val links = r.doc.orgLinks.associateBy { it.id }
        assertEquals(2, links.size)
        assertEquals("papa", links["r1"]!!.personId)
        assertEquals("arzt", links["r1"]!!.orgId)
        assertEquals("doctor", links["r1"]!!.role)
        assertEquals("me", links["r3"]!!.personId)
        assertEquals("custom", links["r3"]!!.role)
        assertEquals("Freund/in", links["r3"]!!.label)
    }

    @Test fun dropsDueConceptAndAddsBuiltins() {
        assertEquals("name", r.doc.settings.listSort)
        assertEquals(30, r.doc.settings.minCallSeconds)
        assertTrue(r.doc.settings.onboardingDone)
        assertFalse(r.doc.settings.showOrgs)
        val ids = r.doc.entryTypes.map { it.id }
        assertTrue(ids.containsAll(listOf("call", "x", BuiltinTypes.CONGRATS, BuiltinTypes.GIFT)))
        assertEquals(1, ids.count { it == "call" })
    }

    @Test fun archiveKeepsTimestamp() {
        assertTrue(c("old").archived)
        assertEquals(77L, c("old").archivedAt)
    }

    @Test fun v2RoundTripWritesSchemaAndNoRemovedFields() {
        val out = Json { encodeDefaults = false; explicitNulls = false }
            .encodeToString(OrbitDoc.serializer(), r.doc)
        assertEquals(2, MigrateV1.schemaOf(out))
        listOf("rhythmDays", "paused", "snoozedUntil", "remindedDueDay", "resetsTimer", "gallery", "entries", "website")
            .forEach { assertFalse(it, out.contains("\"$it\"")) }
        val back = Json { ignoreUnknownKeys = true }.decodeFromString(OrbitDoc.serializer(), out)
        assertEquals(r.doc, back)
    }

    @Test fun deterministicIds() {
        assertEquals(r, run())
    }
}
