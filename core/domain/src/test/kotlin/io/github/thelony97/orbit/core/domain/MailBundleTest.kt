package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.HistoryEntry
import io.github.thelony97.orbit.core.model.MailRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class MailBundleTest {
    private val zone = ZoneId.of("Europe/Vienna")
    private fun at(day: Int, h: Int) = java.time.LocalDate.of(2026, 10, day).atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
    private var n = 0
    private val ids = { "e${n++}" }
    private fun h(uid: Long, at: Long, from: String, to: List<String> = listOf("ich@jku.at"), subject: String = "S$uid", mid: String? = "<$uid@x>") =
        MailBundle.Header("acc", "INBOX", 7, uid, mid, at, from, to, subject)
    private val own = setOf("ich@jku.at", "jakob@gmail.com")
    private val match = { a: String ->
        when (a) {
            "anna@example.at" -> MailBundle.Owner("anna", null)
            "mayer@jku.at" -> MailBundle.Owner("jku", "m1")
            "tom@example.at" -> MailBundle.Owner("tom", null)
            else -> null
        }
    }

    @Test fun incomingToSenderOutgoingToEachKnownRecipient() {
        assertEquals(listOf("anna"), MailBundle.owners(h(1, at(14, 9), "Anna@Example.at"), own, match).map { it.first.contactId })
        val out = MailBundle.owners(h(2, at(14, 10), "jakob@gmail.com", listOf("anna@example.at", "tom@example.at", "fremd@x.at", "ich@jku.at")), own, match)
        assertEquals(listOf("anna", "tom"), out.map { it.first.contactId })
        assertTrue(out.all { !it.second.incoming })
        assertEquals("m1", MailBundle.owners(h(3, at(14, 11), "mayer@jku.at"), own, match).single().first.memberId)
    }

    @Test fun oneEntryPerDayAndDirectionNewestFirst() {
        val anna = Contact(id = "anna")
        val refs = listOf(h(1, at(14, 9), "anna@example.at"), h(2, at(14, 15), "anna@example.at"), h(3, at(15, 8), "anna@example.at"),
            h(4, at(14, 12), "jakob@gmail.com", listOf("anna@example.at")))
            .flatMap { MailBundle.owners(it, own, match) }.map { (o, r) -> o.memberId to r }
        val entries = MailBundle.merge(anna, refs, zone, ids)
        assertEquals(listOf("mail:2026-10-14:in", "mail:2026-10-14:out", "mail:2026-10-15:in"), entries.mapNotNull { it.externalId }.sorted())
        val in14 = entries.first { it.externalId == "mail:2026-10-14:in" }
        assertEquals(listOf(2L, 1L), in14.mails.map { it.uid })
        assertEquals(at(14, 15), in14.at)
        assertEquals("2 E-Mails empfangen", MailBundle.title(in14))
        assertEquals("E-Mail gesendet", MailBundle.title(entries.first { it.externalId == "mail:2026-10-14:out" }))
        assertEquals("", in14.note)
    }

    @Test fun knownMailsAreSkippedAndExistingDaysGrow() {
        val first = MailRef("acc", "INBOX", 7, 1, at(14, 9), true, "Hallo", "anna@example.at", "<1@x>")
        val anna = Contact(id = "anna", entries = listOf(HistoryEntry("old", "anna", "email", at(14, 9), externalId = "mail:2026-10-14:in", mails = listOf(first), incoming = true)))
        // The same mail again (another folder, Gmail label) plus a new one the same day.
        val again = first.copy(folder = "[Gmail]/Alle Nachrichten", uid = 99)
        val next = first.copy(uid = 2, at = at(14, 18), subject = "Nachtrag", messageId = "<2@x>")
        val changed = MailBundle.merge(anna, listOf(null to again, null to next), zone, ids)
        assertEquals(listOf("old"), changed.map { it.id })
        assertEquals(listOf("Nachtrag", "Hallo"), changed.single().mails.map { it.subject })
        assertEquals(emptyList<HistoryEntry>(), MailBundle.merge(anna.copy(entries = changed), listOf(null to next), zone, ids))
    }

    /** Entries written before mails existed read with no mails. */
    @Test fun oldEntryJsonHasNoMails() {
        val e = DocJson.json.decodeFromString(HistoryEntry.serializer(), """{"id":"1","contactId":"a","typeId":"call","at":5,"durationSec":30}""")
        assertEquals(emptyList<MailRef>(), e.mails)
    }
}

class MailBundleRelocateTest {
    @Test fun renumberedFolderMovesTheReference() {
        val zone = ZoneId.of("Europe/Vienna")
        val old = MailRef("acc", "INBOX", 7, 10, 5, true, "Hallo", "anna@x.at", "<1@x>")
        val anna = Contact(id = "anna", entries = listOf(HistoryEntry("e", "anna", "email", 5, externalId = "mail:1970-01-01:in", mails = listOf(old), incoming = true)))
        val changed = MailBundle.merge(anna, listOf(null to old.copy(uidValidity = 8, uid = 3)), zone) { "x" }
        assertEquals(listOf(8L to 3L), changed.single().mails.map { it.uidValidity to it.uid })
        // Same mail in another folder (Gmail label): left as it is.
        assertEquals(emptyList<HistoryEntry>(), MailBundle.merge(anna, listOf(null to old.copy(folder = "Archiv", uid = 99)), zone) { "x" })
    }
}
