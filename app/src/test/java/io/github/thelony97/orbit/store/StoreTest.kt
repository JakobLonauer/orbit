package io.github.thelony97.orbit.store

import io.github.thelony97.orbit.core.domain.DocJson
import io.github.thelony97.orbit.core.model.Attachment
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.HistoryEntry
import io.github.thelony97.orbit.core.model.OrbitDoc
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class StoreTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val dir = File(ctx.filesDir, "store-test").apply { deleteRecursively(); mkdirs() }
    private val db = HistoryDb(ctx, "store-test.db")
    private var n = 0
    private val store = Store(File(dir, "orbit.json"), db) { "id${n++}" }

    @After fun close() {
        db.close()
        ctx.deleteDatabase("store-test.db")
        dir.deleteRecursively()
    }

    private fun e(id: String, c: String, at: Long, note: String = "") = HistoryEntry(id, c, "call", at, note = note)

    private val v1 = """
        {"schema":1,"contacts":[
          {"id":"me","isMe":true,"firstName":"Ich"},
          {"id":"a","firstName":"Anna","address":"Hauptplatz 1","website":"anna.at",
           "entries":[{"id":"e1","typeId":"call","at":5},{"id":"e2","typeId":"meet","at":9,"note":"Kaffee"}],
           "gallery":[{"id":"g1","file":"g1.jpg","addedAt":3}]}
        ]}
    """.trimIndent()

    @Test fun upgradesAndHydratesNewestFirst() {
        store.file.writeText(v1)
        val doc = store.load()!!
        val a = doc.contacts.single { it.id == "a" }
        assertEquals(listOf("e2", "e1"), a.entries.map { it.id })
        assertEquals("g1.jpg", a.gallery.single().file)
        assertEquals("Hauptplatz 1", a.address)
        assertEquals("https://anna.at", a.website)
        // The JSON core holds no history.
        assertFalse(store.file.readText().contains("\"entries\""))
        assertTrue(File(dir, StoreUpgrade.BACKUP).exists())
    }

    @Test fun savesOnlyDifferencesAndRoundTrips() {
        store.file.writeText(v1)
        val loaded = store.load()!!
        val a = loaded.contacts.single { it.id == "a" }
        val changed = loaded.copy(contacts = loaded.contacts.map { c ->
            if (c.id != "a") c else c.copy(
                entries = listOf(e("e3", "", 20, "neu")) + c.entries
                    .filterNot { it.id == "e1" }
                    .map { if (it.id == "e2") it.copy(note = "Kaffee am Hauptplatz") else it },
                gallery = emptyList(),
            )
        })
        val ch = Store.changes(loaded, changed)
        assertEquals(setOf("e3", "e2"), ch.upserts.map { it.id }.toSet())
        assertEquals("a", ch.upserts.single { it.id == "e3" }.contactId)
        assertEquals(listOf("e1"), ch.deletes)
        assertEquals(listOf("g1"), ch.attachmentDeletes)
        store.save(loaded, changed)

        val again = store.load()!!
        val a2 = again.contacts.single { it.id == "a" }
        assertEquals(listOf("e3", "e2"), a2.entries.map { it.id })
        assertEquals("Kaffee am Hauptplatz", a2.entries[1].note)
        assertTrue(a2.gallery.isEmpty())
        assertEquals(1, db.search("hauptplatz").size)
        assertEquals(a.firstName, a2.firstName)
    }

    @Test fun unchangedListsProduceNoWork() {
        val doc = OrbitDoc(contacts = listOf(Contact("a", entries = listOf(e("1", "a", 1)))))
        assertTrue(Store.changes(doc, doc.copy(settings = doc.settings.copy(minCallSeconds = 5))).isEmpty)
    }

    @Test fun removedContactTakesItsRowsAlong() {
        val before = OrbitDoc(contacts = listOf(
            Contact("a", entries = listOf(e("1", "a", 1)), gallery = listOf(Attachment("x", "a", "x.jpg"))),
            Contact("b"),
        ))
        store.save(null, before)
        assertEquals(1, db.count())
        val after = before.copy(contacts = before.contacts.filterNot { it.id == "a" })
        assertEquals(listOf("a"), Store.changes(before, after).contactsGone)
        store.save(before, after)
        assertEquals(0, db.count())
        assertEquals(0, db.references("x.jpg"))
    }

    @Test fun movedEntryChangesOwner() {
        val before = OrbitDoc(contacts = listOf(Contact("a", entries = listOf(e("1", "a", 1))), Contact("b")))
        store.save(null, before)
        val moved = before.contacts.first().entries.first()
        val after = before.copy(contacts = listOf(Contact("a"), Contact("b", entries = listOf(moved))))
        store.save(before, after)
        assertEquals("b", db.forContact("b").single().contactId)
        assertTrue(db.forContact("a").isEmpty())
    }

    @Test fun freshInstallHasNothing() {
        assertNull(store.load())
        val doc = OrbitDoc(contacts = listOf(Contact("me", isMe = true)))
        store.save(null, doc)
        assertEquals(doc, DocJson.decode(store.file.readText()))
    }
}
