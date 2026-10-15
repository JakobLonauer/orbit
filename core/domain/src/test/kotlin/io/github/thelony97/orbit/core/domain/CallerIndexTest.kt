package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.CalendarDay
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.ContactKind
import io.github.thelony97.orbit.core.model.CustomField
import io.github.thelony97.orbit.core.model.Greeting
import io.github.thelony97.orbit.core.model.LabeledValue
import io.github.thelony97.orbit.core.model.Member
import io.github.thelony97.orbit.core.model.Occasion
import io.github.thelony97.orbit.core.model.OccasionKind
import io.github.thelony97.orbit.core.model.OrbitDoc
import io.github.thelony97.orbit.core.model.OrgLink
import io.github.thelony97.orbit.core.model.Relation
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallerIndexTest {
    private val at: (String) -> String? = { raw ->
        val d = raw.filter { it.isDigit() }
        when {
            d.length < 3 -> null
            raw.trim().startsWith("+") -> "+$d"
            d.startsWith("0") -> "+43" + d.drop(1)
            else -> d
        }
    }
    private fun ph(v: String, l: String = "") = LabeledValue(l, v)

    private val papa = Contact("papa", firstName = "Peter", lastName = "Lonauer", photo = "p.jpg",
        phones = listOf(ph("0660 1")), pinnedNote = "Fragt immer nach dem Auto")
    private val arzt = Contact("arzt", kind = ContactKind.ORG, firstName = "Arzt Müller",
        phones = listOf(ph("0732 111", "Termine")),
        fields = listOf(CustomField("f", "Patientennummer", "P-77")))
    private val liles = Contact("liles", kind = ContactKind.ORG, firstName = "LiLES",
        members = listOf(Member("m1", "Frau Huber", role = "Sachbearbeiterin", phones = listOf(ph("0732 2468 12")))),
        numberPrefixes = listOf("0732 2468"))
    private val anna = Contact("anna", firstName = "Anna", phones = listOf(ph("0664 2")), position = "Lehrerin")
    private val doc = OrbitDoc(
        contacts = listOf(papa, arzt, liles, anna, Contact("me", isMe = true, phones = listOf(ph("0650 3")))),
        orgLinks = listOf(OrgLink("l", "papa", "arzt", "gp")),
    )
    private val idx = CallerIndex.build(doc, at)

    @Test fun personWithAssignmentAndNote() {
        val c = idx.lookup("+43 660 1", at)!!
        assertEquals("Peter Lonauer", c.name)
        assertEquals("Patient:in · Arzt Müller", c.role)
        assertEquals("Fragt immer nach dem Auto", c.note)
        assertEquals("p.jpg", c.photo)
    }

    @Test fun orgNumberLabelAndCustomerNumber() {
        val c = idx.lookup("0732111", at)!!
        assertEquals("Arzt Müller", c.name)
        assertEquals("Termine", c.role)
        assertEquals("P-77", c.customerNo)
        assertTrue(c.org)
    }

    @Test fun memberAndRange() {
        val m = idx.lookup("0732 246812", at)!!
        assertEquals("Frau Huber", m.name)
        assertEquals("Sachbearbeiterin · LiLES", m.role)
        assertEquals("m1", m.memberId)
        val r = idx.lookup("0732 2468 99", at)!!
        assertEquals("LiLES", r.name)
        assertNull(r.memberId)
    }

    @Test fun durchwahlOnlyForRanges() {
        // PLAN 9 B2: „Zentrale · Durchwahl 99“ – a member's or an exact number is no Durchwahl.
        assertEquals("99", idx.extension("0732 2468 99", at))
        assertNull(idx.extension("0732 246812", at))
        assertNull(idx.extension("0664 2", at))
    }

    @Test fun familyAndAnrede() {
        // PLAN 9 B7: „Sie · Frau Mag.a Huber“, partner and children with their birthdays.
        val huber = Contact("h", firstName = "Anna", lastName = "Huber", title = "Mag.a", salutation = "Frau", greeting = Greeting(formal = true), phones = listOf(ph("0664 70")))
        val partner = Contact("p", firstName = "Lukas", lastName = "Huber", nickname = "Luki", phones = listOf(ph("0664 71")))
        val kid = Contact("k", firstName = "Mia", dates = listOf(Occasion("o", OccasionKind.BIRTHDAY, date = CalendarDay(3, 4, 2019))))
        val d = OrbitDoc(contacts = listOf(huber, partner, kid), relations = listOf(Relation("r1", "p", "h", "partner"), Relation("r2", "h", "k", "parent")))
        val i = CallerIndex.build(d, at)
        val card = i.lookup("0664 70", at)!!
        assertEquals("Sie · Frau Mag.a Huber", card.anrede)
        assertEquals(listOf("Luki" to "partner", "Mia" to "child"), card.family.map { it.name to it.kind })
        assertEquals(CalendarDay(3, 4, 2019), card.family.last().birthday)
        assertEquals("Du · Luki", i.lookup("0664 71", at)!!.anrede)
        assertEquals("", idx.lookup("0664 2", at)!!.anrede)
    }

    @Test fun fallbackRoleAndMeExcluded() {
        assertEquals("Lehrerin", idx.lookup("0664 2", at)!!.role)
        assertNull(idx.lookup("0650 3", at))
    }

    @Test fun lastContactTopicsAndOrgNameFromCompany() {
        val call = io.github.thelony97.orbit.core.model.HistoryEntry("e1", "anna", "call", at = 5_000, note = "x")
        val talk = io.github.thelony97.orbit.core.model.HistoryEntry("e2", "anna", "meet", at = 9_000)
        val org = Contact("praxis", kind = ContactKind.ORG, company = "Praxis Dr. Huber", phones = listOf(ph("0732 555")))
        val d = doc.copy(
            contacts = doc.contacts.map {
                if (it.id == "anna") it.copy(
                    entries = listOf(call, talk),
                    topics = listOf(
                        io.github.thelony97.orbit.core.model.CheckItem("t1", "Wohnung"),
                        io.github.thelony97.orbit.core.model.CheckItem("t2", "Erledigt", done = true),
                    ),
                ) else it
            } + org,
            entryTypes = listOf(
                io.github.thelony97.orbit.core.model.EntryType("call", "Anruf", "call"),
                io.github.thelony97.orbit.core.model.EntryType("meet", "Treffen", "meet"),
            ),
        )
        val i = CallerIndex.build(d, at)
        val a = i.lookup("0664 2", at)!!
        assertEquals(9_000L, a.lastAt)
        assertEquals("Treffen", a.lastKind)
        assertEquals(listOf("Wohnung"), a.topics)
        assertEquals("Praxis Dr. Huber", i.lookup("0732 555", at)!!.name)
        assertTrue(i.callerInfo)
        val off = CallerIndex.build(d.copy(settings = d.settings.copy(callerInfo = false)), at)
        assertEquals(false, off.callerInfo)
    }

    @Test fun birthdayGoesOnThePersonCardOnly() {
        val bday = io.github.thelony97.orbit.core.model.CalendarDay(15, 10, 1996)
        val withDate = anna.copy(dates = listOf(io.github.thelony97.orbit.core.model.Occasion("o", io.github.thelony97.orbit.core.model.OccasionKind.BIRTHDAY, date = bday)))
        val i = CallerIndex.build(doc.copy(contacts = listOf(withDate, arzt)), at)
        assertEquals(bday, i.lookup("0664 2", at)!!.birthday)
        assertNull(i.lookup("0732 111", at)!!.birthday)
    }

    @Test fun groupColourGoesOnTheCard() {
        val fam = io.github.thelony97.orbit.core.model.Group("fam", "Familie", 0xFFFF6B6BL)
        val i = CallerIndex.build(doc.copy(groups = listOf(fam), contacts = listOf(anna.copy(groupIds = listOf("fam")), papa)), at)
        assertEquals(0xFFFF6B6BL, i.lookup("0664 2", at)!!.color)
        assertNull(i.lookup("0660 1", at)!!.color)
    }

    @Test fun oldIndexFileStillReads() {
        // Written by 1.0.101 without the new fields.
        val json = Json { ignoreUnknownKeys = true }
        val old = """{"exact":{"+436642":{"contactId":"anna","name":"Anna"}}}"""
        val i = json.decodeFromString(CallerIndex.serializer(), old)
        assertTrue(i.callerInfo)
        assertEquals("Anna", i.exact["+436642"]!!.name)
        assertEquals(emptyList<String>(), i.exact["+436642"]!!.topics)
    }

    @Test fun survivesJson() {
        val json = Json { ignoreUnknownKeys = true }
        val text = json.encodeToString(CallerIndex.serializer(), idx)
        assertEquals(idx, json.decodeFromString(CallerIndex.serializer(), text))
    }
}
