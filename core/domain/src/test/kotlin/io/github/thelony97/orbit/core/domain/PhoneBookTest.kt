package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.CalendarDay
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.ContactKind
import io.github.thelony97.orbit.core.model.CustomField
import io.github.thelony97.orbit.core.model.Group
import io.github.thelony97.orbit.core.model.LabeledValue
import io.github.thelony97.orbit.core.model.Occasion
import io.github.thelony97.orbit.core.model.OccasionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneBookTest {

    private val anna = Contact(
        "anna", firstName = "Anna", lastName = "Huber", title = "Mag.a", nickname = "Anni",
        phones = listOf(LabeledValue("Mobil", "+43 660 1234567"), LabeledValue("Büro", "0732 2468 12"), LabeledValue("Oma", "07229 1234")),
        defaultPhone = 1,
        emails = listOf(LabeledValue("Privat", "anna@example.at"), LabeledValue("Uni", "anna@jku.at")),
        favorite = true, groupIds = listOf("fam"),
        dates = listOf(Occasion("b", OccasionKind.BIRTHDAY, date = CalendarDay(17, 5, 1990))),
        fields = listOf(CustomField("f", "Kundennummer", "K-12345")),
    )
    private val groups = listOf(Group("fam", "Familie", 0xFF00FF00, ringtone = "content://ring/family"))

    @Test fun whoGoesIn() {
        assertTrue(PhoneBook.exported(anna, withOrgs = false))
        assertFalse(PhoneBook.exported(anna.copy(archived = true), true))
        assertFalse(PhoneBook.exported(anna.copy(deletedAt = 5), true))
        assertFalse(PhoneBook.exported(anna.copy(isMe = true), true))
        assertFalse(PhoneBook.exported(Contact("x"), true)) // no name at all
        val org = Contact("o", kind = ContactKind.ORG, company = "Ordination Dr. Mayr")
        assertTrue(PhoneBook.exported(org, true))
        assertFalse(PhoneBook.exported(org, false))
    }

    @Test fun cardOfAPerson() {
        val c = PhoneBook.card(anna, groups)
        assertEquals("Anna", c.given)
        assertEquals("Huber", c.family)
        assertEquals("Mag.a", c.prefix)
        assertEquals("Anni", c.nickname)
        assertEquals(listOf(PhoneBook.PhoneType.MOBILE, PhoneBook.PhoneType.WORK, PhoneBook.PhoneType.CUSTOM), c.numbers.map { it.type })
        assertEquals("Oma", c.numbers[2].label)
        assertEquals(listOf(false, true, false), c.numbers.map { it.primary })
        assertEquals(listOf(PhoneBook.MailType.HOME, PhoneBook.MailType.CUSTOM), c.mails.map { it.type })
        assertEquals("1990-05-17", c.birthday)
        assertTrue(c.starred)
        assertEquals("content://ring/family", c.ringtone) // from the group
        assertEquals(listOf(PhoneBook.Field("Kundennummer", "K-12345")), c.fields)
    }

    @Test fun ownRingtoneBeatsTheGroupAndYearlessBirthday() {
        val c = PhoneBook.card(anna.copy(ringtone = "content://ring/anna", dates = listOf(Occasion("b", OccasionKind.BIRTHDAY, date = CalendarDay(3, 12)))), groups)
        assertEquals("content://ring/anna", c.ringtone)
        assertEquals("--12-03", c.birthday)
    }

    @Test fun cardOfAnOrganisation() {
        val org = Contact("o", kind = ContactKind.ORG, company = "Ordination Dr. Mayr", phones = listOf(LabeledValue("Zentrale", "0732 123"), LabeledValue("Sonstige", "0732 124")))
        val c = PhoneBook.card(org, emptyList())
        assertEquals("Ordination Dr. Mayr", c.display)
        assertEquals("", c.given)
        assertEquals(listOf(PhoneBook.PhoneType.COMPANY_MAIN, PhoneBook.PhoneType.MAIN), c.numbers.map { it.type })
        assertNull(c.birthday)
    }

    @Test fun hashFollowsTheContent() {
        val a = PhoneBook.card(anna, groups)
        assertEquals(a.hash, PhoneBook.card(anna, groups).hash)
        assertNotEquals(a.hash, PhoneBook.card(anna.copy(lastName = "Berger"), groups).hash)
        assertNotEquals(a.hash, PhoneBook.card(anna.copy(photo = "p.jpg"), groups).hash)
        assertEquals(20, a.hash.length)
    }

    @Test fun macrosOnlyForOrganisationsAndHashStaysWithout() {
        val org = Contact(
            "o", kind = ContactKind.ORG, company = "Raiffeisen",
            macros = listOf(io.github.thelony97.orbit.core.model.Macro("m1", "Kundenservice", listOf(io.github.thelony97.orbit.core.model.MacroStep(io.github.thelony97.orbit.core.model.MacroStepKind.KEY, "1")))),
        )
        val withMacro = PhoneBook.card(org, emptyList())
        assertEquals(listOf(PhoneBook.MacroRow("m1", "Kundenservice")), withMacro.macros)
        assertNotEquals(withMacro.hash, PhoneBook.card(org.copy(macros = emptyList()), emptyList()).hash)
        // A card without macros hashes the text it had before macros existed (no rewrite of every row).
        val plain = PhoneBook.card(anna, groups)
        val before = java.security.MessageDigest.getInstance("SHA-256").digest(plain.toString().replace(", macros=[])", ")").toByteArray())
            .take(10).joinToString("") { "%02x".format(it) }
        assertEquals(before, plain.hash)
    }

    @Test fun planInsertsRewritesAndDeletes() {
        val a = PhoneBook.card(anna, groups)
        val b = PhoneBook.card(anna.copy(id = "b", firstName = "Bea"), groups)
        val c = PhoneBook.card(anna.copy(id = "c", firstName = "Cleo"), groups)
        val existing = listOf(
            PhoneBook.Existing(1, "anna", a.hash), // unchanged
            PhoneBook.Existing(2, "b", "old"), // changed
            PhoneBook.Existing(3, "gone", "x"), // no longer in Orbit
            PhoneBook.Existing(4, null, null), // deleted in a contacts app
            PhoneBook.Existing(5, "anna", a.hash), // duplicate
        )
        val plan = PhoneBook.plan(listOf(a, b, c), existing)
        assertEquals(listOf("c"), plan.insert.map { it.sourceId })
        assertEquals(listOf(2L to b), plan.rewrite)
        assertEquals(setOf(3L, 4L, 5L), plan.delete.toSet())
        assertTrue(PhoneBook.plan(listOf(a), listOf(PhoneBook.Existing(1, "anna", a.hash))).isEmpty)
    }
}
