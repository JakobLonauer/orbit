package io.github.thelony97.orbit.store

import io.github.thelony97.orbit.core.domain.DocJson
import io.github.thelony97.orbit.core.domain.migration.MigrateV1
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class StoreUpgradeTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val dir = File(ctx.filesDir, "upgrade-test").apply { deleteRecursively(); mkdirs() }
    private val file = File(dir, "orbit.json")
    private val db = HistoryDb(ctx, "upgrade-test.db")
    private var n = 0
    private val ids = { "gen${n++}" }

    private val v1 = """
        {"schema":1,"contacts":[
          {"id":"me","isMe":true,"firstName":"Ich"},
          {"id":"a","firstName":"Anna","birthday":{"day":1,"month":3},"rhythmDays":30,
           "entries":[{"id":"e1","typeId":"call","at":5,"source":"CALL_LOG","externalId":"call:7"}],
           "gallery":[{"id":"g1","file":"g1.jpg","addedAt":3}]}
        ],"settings":{"onboardingDone":true}}
    """.trimIndent()

    @After fun close() {
        db.close()
        ctx.deleteDatabase("upgrade-test.db")
        dir.deleteRecursively()
    }

    @Test fun migratesOnceAndKeepsBackup() {
        file.writeText(v1)
        val r = StoreUpgrade.run(file, db, ids)
        assertTrue(r.migrated)
        assertEquals(1, r.entries)
        assertEquals(1, r.attachments)
        assertEquals(v1, File(dir, StoreUpgrade.BACKUP).readText())
        assertEquals(3, MigrateV1.schemaOf(file.readText()))
        // Straight from 1.0.7 to schema 3: the v1 copy is the only one needed.
        assertFalse(File(dir, StoreUpgrade.BACKUP_V2).exists())
        val doc = DocJson.decode(file.readText())
        assertEquals("Anna", doc.contacts[1].firstName)
        assertEquals(1, doc.contacts[1].dates.size)
        assertEquals("e1", db.forContact("a").single().id)
        assertEquals("g1.jpg", db.attachments("a").single().file)
        assertFalse(File(dir, "orbit.json.tmp").exists())

        // Second start: nothing happens, backup untouched.
        assertFalse(StoreUpgrade.run(file, db, ids).migrated)
        assertEquals(v1, File(dir, StoreUpgrade.BACKUP).readText())
    }

    /** Schema 2 on disk (1.0.101, R2/R3 candidates): copy first, then the Zuordnungen. */
    @Test fun schema2GetsZuordnungenAndKeepsACopy() {
        val v2 = """
            {"schema":2,"contacts":[{"id":"me","isMe":true,"firstName":"Ich"},{"id":"a","firstName":"Anna"},{"id":"o","kind":"ORG","company":"Arzt"}],
             "relations":[{"id":"r","fromId":"a","toId":"o","kind":"patient"}],"settings":{"onboardingDone":true}}
        """.trimIndent()
        file.writeText(v2)
        val r = StoreUpgrade.run(file, db, ids)
        assertTrue(r.toV3)
        assertFalse(r.migrated)
        assertEquals(v2, File(dir, StoreUpgrade.BACKUP_V2).readText())
        val doc = DocJson.decode(file.readText())
        assertEquals(3, doc.schema)
        assertTrue(doc.relations.isEmpty())
        assertEquals("doctor", doc.orgLinks.single().role)
        assertEquals("Anna", doc.contacts[1].firstName)
        // Next start: nothing, the copy stays as it was.
        assertFalse(StoreUpgrade.run(file, db, ids).toV3)
        assertEquals(v2, File(dir, StoreUpgrade.BACKUP_V2).readText())
    }

    @Test fun rerunAfterCrashDoesNotDuplicate() {
        file.writeText(v1)
        // Simulate a crash after the DB step: rows are in, file is still v1.
        db.insertAll(MigrateV1.fromJson(v1, ids).history)
        val r = StoreUpgrade.run(file, db, ids)
        assertTrue(r.migrated)
        assertEquals(1, db.count())
    }

    @Test fun existingBackupIsNeverOverwritten() {
        File(dir, StoreUpgrade.BACKUP).writeText("älter")
        file.writeText(v1)
        StoreUpgrade.run(file, db, ids)
        assertEquals("älter", File(dir, StoreUpgrade.BACKUP).readText())
    }

    @Test fun smsOfTwoPeopleOnTheSameDayBothSurvive() {
        file.writeText("""
            {"schema":1,"contacts":[
              {"id":"a","entries":[{"id":"s1","typeId":"message","at":1,"source":"SMS","externalId":"sms:2026-01-01"}]},
              {"id":"b","entries":[{"id":"s2","typeId":"message","at":2,"source":"SMS","externalId":"sms:2026-01-01"}]}
            ]}
        """.trimIndent())
        StoreUpgrade.run(file, db, ids)
        assertEquals(2, db.count())
        assertEquals("s1", db.forContact("a").single().id)
        assertEquals("s2", db.forContact("b").single().id)
    }

    @Test fun missingFileIsNoOp() {
        assertFalse(StoreUpgrade.run(file, db, ids).migrated)
    }
}
