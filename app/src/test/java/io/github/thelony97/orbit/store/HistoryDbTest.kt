package io.github.thelony97.orbit.store

import io.github.thelony97.orbit.core.model.Attachment
import io.github.thelony97.orbit.core.model.HistoryEntry
import io.github.thelony97.orbit.core.model.SmsMessage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class HistoryDbTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val db = HistoryDb(ctx, "test-history.db")

    @After fun close() {
        db.close()
        ctx.deleteDatabase("test-history.db")
    }

    private fun e(id: String, contact: String, at: Long, note: String = "", ext: String? = null, member: String? = null) =
        HistoryEntry(id, contact, "call", at, note = note, externalId = ext, memberId = member)

    @Test fun insertIsIdempotentByIdAndExternalId() {
        assertEquals(2, db.insertAll(listOf(e("1", "a", 10, ext = "call:1"), e("2", "a", 20))))
        assertEquals(0, db.insertAll(listOf(e("1", "a", 10), e("other", "a", 30, ext = "call:1"))))
        assertEquals(2, db.count())
    }

    @Test fun forContactNewestFirstWithPaging() {
        db.insertAll(listOf(e("1", "a", 10), e("2", "a", 30), e("3", "b", 20), e("4", "a", 20)))
        assertEquals(listOf("2", "4", "1"), db.forContact("a").map { it.id })
        assertEquals(listOf("4", "1"), db.forContact("a", before = 30).map { it.id })
        assertEquals(listOf("2"), db.forContact("a", limit = 1).map { it.id })
    }

    @Test fun fullJsonSurvives() {
        val sms = HistoryEntry("s", "a", "message", 5, messages = listOf(SmsMessage(1, 5, true, "Hallo")), withIds = listOf("b"))
        db.insertAll(listOf(sms))
        assertEquals(sms, db.forContact("a").single())
    }

    @Test fun searchFindsNotesAndSmsWithoutDiacriticsAndHidesMembers() {
        db.insertAll(listOf(
            e("1", "a", 10, note = "Termin beim Hausarzt Müller"),
            HistoryEntry("2", "a", "message", 20, messages = listOf(SmsMessage(1, 20, false, "Treffen am Hauptplatz"))),
            e("3", "org", 30, note = "Frau Müller wegen Zählerstand", member = "m1"),
        ))
        val hits = db.search("mull")
        assertEquals(listOf("1"), hits.map { it.entry.id })
        assertTrue(hits.single().snippet, hits.single().snippet.contains("«"))
        assertEquals(listOf("3", "1"), db.search("Müller", includeMembers = true).map { it.entry.id })
        assertEquals(listOf("2"), db.search("haupt").map { it.entry.id })
        assertEquals(emptyList<Any>(), db.search("  ** \" ").map { it.entry.id })
        assertEquals(listOf("1"), db.search("hausarzt termin").map { it.entry.id })
    }

    @Test fun updateAndDeleteKeepFtsInSync() {
        db.insertAll(listOf(e("1", "a", 10, note = "alt")))
        db.update(e("1", "a", 10, note = "neu geschrieben"))
        assertTrue(db.search("alt").isEmpty())
        assertEquals(1, db.search("geschrieben").size)
        db.delete("1")
        assertTrue(db.search("geschrieben").isEmpty())
        assertEquals(0, db.count())
    }

    @Test fun attachmentsWithReferenceCount() {
        val a = Attachment("x", "a", "f1.jpg", addedAt = 1)
        assertEquals(2, db.insertAttachments(listOf(a, a.copy(id = "y", contactId = "b"))))
        assertEquals(0, db.insertAttachments(listOf(a)))
        assertEquals(2, db.references("f1.jpg"))
        db.deleteAttachment("x")
        assertEquals(1, db.references("f1.jpg"))
        assertNull(db.attachments("a").firstOrNull())
        assertEquals("y", db.attachments("b").single().id)
    }
}
