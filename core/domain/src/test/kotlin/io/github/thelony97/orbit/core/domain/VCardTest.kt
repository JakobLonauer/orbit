package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.CalendarDay
import io.github.thelony97.orbit.core.model.CheckItem
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.ContactKind
import io.github.thelony97.orbit.core.model.CustomField
import io.github.thelony97.orbit.core.model.LabeledValue
import io.github.thelony97.orbit.core.model.Link
import io.github.thelony97.orbit.core.model.Occasion
import io.github.thelony97.orbit.core.model.OccasionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VCardTest {
    private val anna = Contact(
        "a", firstName = "Anna", lastName = "Berger", company = "Müller, Huber & Co", position = "Lehrerin",
        phones = listOf(LabeledValue("Privat", "0732 1"), LabeledValue("Mobil", "+43 660 1234567")),
        defaultPhone = 1,
        emails = listOf(LabeledValue("Privat", "anna@example.at")),
        addresses = listOf(LabeledValue("", "Hauptplatz 1\n4020 Linz")),
        links = listOf(Link("l", "Webseite", "https://anna.example.at")),
        notes = "geheim", pinnedNote = "auch geheim",
        topics = listOf(CheckItem("t", "Wohnung")),
        fields = listOf(CustomField("f", "Kundennummer", "KD-1")),
        dates = listOf(Occasion("b", OccasionKind.BIRTHDAY, date = CalendarDay(3, 1, 1996))),
    )

    @Test fun businessCardFieldsAndEscaping() {
        val v = VCard.of(anna)
        assertTrue(v.startsWith("BEGIN:VCARD\r\nVERSION:3.0\r\n"))
        assertTrue(v.endsWith("END:VCARD\r\n"))
        assertTrue("N:Berger;Anna;;;" in v)
        assertTrue("FN:Anna Berger" in v)
        assertTrue("ORG:Müller\\, Huber & Co" in v)
        assertTrue("TITLE:Lehrerin" in v)
        assertTrue("TEL;TYPE=HOME:0732 1" in v)
        assertTrue("TEL;TYPE=CELL,PREF:+43 660 1234567" in v)
        assertTrue("EMAIL;TYPE=HOME:anna@example.at" in v)
        assertTrue("ADR;TYPE=HOME:;;Hauptplatz 1\\, 4020 Linz;;;;" in v)
        assertTrue("URL:https://anna.example.at" in v)
        assertTrue("BDAY:1996-01-03" in v)
    }

    @Test fun privateThingsNeverLeave() {
        val v = VCard.of(anna)
        listOf("geheim", "Wohnung", "KD-1", "Kundennummer").forEach { assertFalse(it, it in v) }
    }

    @Test fun birthdayWithoutYearAndOrganisation() {
        val c = anna.copy(dates = listOf(Occasion("b", OccasionKind.BIRTHDAY, date = CalendarDay(29, 2))))
        assertTrue("BDAY:--02-29" in VCard.of(c))
        val org = Contact("o", kind = ContactKind.ORG, company = "Arzt Müller; Praxis", phones = listOf(LabeledValue("Zentrale", "0732 111")))
        val v = VCard.of(org)
        assertTrue("FN:Arzt Müller\\; Praxis" in v)
        assertTrue("ORG:Arzt Müller\\; Praxis" in v)
        assertTrue("TEL;TYPE=WORK,PREF:0732 111" in v)
    }

    @Test fun safeFileName() {
        assertEquals("Anna_Berger.vcf", VCard.fileName(anna))
        assertEquals("Kontakt.vcf", VCard.fileName(Contact("x")))
        assertEquals("Arzt_Müller_Praxis.vcf", VCard.fileName(Contact("o", kind = ContactKind.ORG, company = "Arzt Müller / Praxis")))
    }
}
