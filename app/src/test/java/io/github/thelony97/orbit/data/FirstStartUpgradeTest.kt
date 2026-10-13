package io.github.thelony97.orbit.data

import io.github.thelony97.orbit.core.domain.migration.MigrateV1
import io.github.thelony97.orbit.store.StoreUpgrade
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * The real first start after installing over 1.0.7, through the repository the app uses.
 * The fixture has the shape 1.0.7 writes (encodeDefaults = true, schema 1).
 */
@RunWith(RobolectricTestRunner::class)
class FirstStartUpgradeTest {
    private val ctx = RuntimeEnvironment.getApplication()

    private val v1 = """
        {"schema":1,"contacts":[
          {"id":"me","isMe":true,"firstName":"Jakob","createdAt":1,"updatedAt":1},
          {"id":"papa","firstName":"Peter","lastName":"Lonauer","phones":[{"label":"Mobil","value":"+43 664 7654321"}],
           "address":"Hauptplatz 1, 4020 Linz","birthday":{"day":29,"month":2,"year":1964},"rhythmDays":14,"paused":true,
           "topics":[{"id":"t1","text":"Auto"}],"gifts":[{"id":"g1","text":"Buch"}],"notes":"Hausarzt Dr. Müller",
           "entries":[
             {"id":"e1","typeId":"call","at":1700000000000,"source":"CALL_LOG","durationSec":320,"incoming":true,"externalId":"call:1700000000000"},
             {"id":"e2","typeId":"message","at":1700000100000,"source":"SMS","externalId":"sms:2023-11-14",
              "messages":[{"id":7,"at":1700000100000,"incoming":false,"text":"Bin unterwegs"}]}],
           "gallery":[{"id":"p1","file":"g-p1.jpg","addedAt":9,"caption":"Urlaub"}],"createdAt":2,"updatedAt":3},
          {"id":"oma","firstName":"Oma","entries":[{"id":"e3","typeId":"message","at":1700000200000,"source":"SMS","externalId":"sms:2023-11-14"}]}
        ],
        "groups":[{"id":"g","name":"Familie","color":4293227379,"rhythmDays":14,"pinned":true}],
        "entryTypes":[{"id":"call","name":"Anruf","icon":"call","resetsTimer":true,"builtin":true}],
        "relations":[{"id":"r","fromId":"me","toId":"papa","kind":"parent"}],
        "settings":{"onboardingDone":true,"listSort":"due","minCallSeconds":30,"backupTreeUri":"content://x"}}
    """.trimIndent()

    @Test fun firstStartKeepsEverythingAndSavesDifferences() = runBlocking {
        val dir = ctx.filesDir
        File(dir, "orbit.json").writeText(v1)
        File(dir, StoreUpgrade.BACKUP).delete()
        ctx.deleteDatabase("orbit.db")
        OrbitRepository.resetForTest(ctx)

        val d = OrbitRepository.ensureLoaded()
        assertNull(OrbitRepository.loadError.value)
        val papa = d.contacts.single { it.id == "papa" }
        assertEquals(listOf("e2", "e1"), papa.entries.map { it.id })
        assertEquals("Bin unterwegs", papa.entries[0].messages.single().text)
        assertEquals("g-p1.jpg", papa.gallery.single().file)
        assertEquals("Hauptplatz 1, 4020 Linz", papa.address)
        assertEquals(29, papa.birthday?.day)
        assertEquals("Hausarzt Dr. Müller", papa.notes)
        assertEquals("Auto", papa.topics.single().text)
        assertEquals("Buch", papa.gifts.single().text)
        // Same SMS day at two people: both kept.
        assertEquals("e3", d.contacts.single { it.id == "oma" }.entries.single().id)
        assertEquals("content://x", d.settings.backupTreeUri)
        assertEquals("name", d.settings.listSort)
        assertEquals(1, d.relations.size)
        assertEquals(v1, File(dir, StoreUpgrade.BACKUP).readText())
        assertEquals(2, MigrateV1.schemaOf(File(dir, "orbit.json").readText()))

        // Add an entry and delete one, then save.
        OrbitRepository.update { s -> Mutations.addEntry(s, "papa", Entry(id = "e4", contactId = "", typeId = "meet", at = 1800000000000, note = "Kaffee")) }
        OrbitRepository.update { s -> Mutations.deleteEntry(s, "papa", "e1") }
        OrbitRepository.flush()
        assertEquals(listOf("e4", "e2"), OrbitRepository.history.forContact("papa").map { it.id })
        assertFalse(File(dir, "orbit.json").readText().contains("Kaffee"))
        assertTrue(File(dir, "caller-index.json").exists())

        // A fresh process sees exactly the same.
        OrbitRepository.resetForTest(ctx)
        val again = OrbitRepository.ensureLoaded()
        assertEquals(listOf("e4", "e2"), again.contacts.single { it.id == "papa" }.entries.map { it.id })
    }
}
