package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.CalendarDay
import io.github.thelony97.orbit.core.model.ContactKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VCardReaderTest {
    private var n = 0
    private fun id(): String = "id${n++}"

    @Test fun version3BusinessCard() {
        val text = """
            BEGIN:VCARD
            VERSION:3.0
            N:Huber;Anna;;Mag.a;
            FN:Mag.a Anna Huber
            NICKNAME:Anni
            ORG:JKU Linz;Institut
            TITLE:Assistentin
            TEL;TYPE=CELL,PREF:+43 660 1234567
            TEL;TYPE=WORK:0732 2468 12
            EMAIL;TYPE=WORK:anna.huber@jku.at
            ADR;TYPE=WORK:;;Altenberger Straße 69;Linz;;4040;Österreich
            URL:www.jku.at
            BDAY:1990-05-17
            NOTE:Kennt sich mit\nKUSSS aus
            END:VCARD
        """.trimIndent()
        val c = VCardReader.read(text, ::id).single()
        assertEquals(ContactKind.PERSON, c.kind)
        assertEquals("Anna", c.firstName)
        assertEquals("Huber", c.lastName)
        assertEquals("Mag.a", c.title)
        assertEquals("Anni", c.nickname)
        assertEquals("JKU Linz · Institut", c.company)
        assertEquals("Assistentin", c.position)
        assertEquals(listOf("Mobil", "Arbeit"), c.phones.map { it.label })
        assertEquals("anna.huber@jku.at", c.emails.single().value)
        assertEquals("Altenberger Straße 69, 4040 Linz, Österreich", c.address)
        assertEquals("https://www.jku.at", c.links.single().url)
        assertEquals(CalendarDay(17, 5, 1990), c.birthday)
        assertEquals("Kennt sich mit\nKUSSS aus", c.notes)
    }

    @Test fun oldAndroidExportWithQuotedPrintableAndFolding() {
        val text = "BEGIN:VCARD\r\nVERSION:2.1\r\nN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:M=C3=BCller;J=C3=\r\n=BCrgen;;;\r\nTEL;CELL:0664 111\r\nEMAIL;HOME:j@example.at\r\nEND:VCARD\r\n" +
            "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Ordination\r\n  Dr. Mayr\r\nORG:Ordination Dr. Mayr\r\nTEL;TYPE=WORK:0732 777\r\nEND:VCARD\r\n"
        val list = VCardReader.read(text, ::id)
        assertEquals(2, list.size)
        assertEquals("Jürgen", list[0].firstName)
        assertEquals("Müller", list[0].lastName)
        assertEquals("Mobil", list[0].phones.single().label)
        assertEquals("Privat", list[0].emails.single().label)
        assertEquals(ContactKind.ORG, list[1].kind)
        assertEquals("Ordination Dr. Mayr", list[1].company)
    }

    @Test fun onlyAFullName() {
        val c = VCardReader.read("BEGIN:VCARD\nVERSION:4.0\nFN:Tom Berger\nTEL:tel:+43 1 234\nEND:VCARD", ::id).single()
        assertEquals("Tom", c.firstName)
        assertEquals("Berger", c.lastName)
        assertEquals("+43 1 234", c.phones.single().value)
    }

    @Test fun birthdaysAndEmptyCards() {
        assertEquals(CalendarDay(3, 12, null), VCardReader.day("--12-03"))
        assertEquals(CalendarDay(3, 12, null), VCardReader.day("--1203"))
        assertEquals(CalendarDay(1, 2, 1985), VCardReader.day("19850201"))
        assertNull(VCardReader.day("gestern"))
        assertTrue(VCardReader.read("BEGIN:VCARD\nVERSION:3.0\nEND:VCARD", ::id).isEmpty())
    }
}
