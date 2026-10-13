package io.github.thelony97.orbit

import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Test-build probe for ADR 0003: FTS4 with snippet() and diacritic-insensitive search. */
@RunWith(RobolectricTestRunner::class)
class Fts4Test {

    private fun db(): SQLiteDatabase = SQLiteDatabase.create(null)

    @Test
    fun fts4MatchAndSnippet() {
        val db = db()
        db.execSQL("CREATE VIRTUAL TABLE t USING fts4(body)")
        db.execSQL("INSERT INTO t(body) VALUES ('Termin beim Hausarzt Müller am Dienstag')")
        db.execSQL("INSERT INTO t(body) VALUES ('Geburtstag von Papa')")
        db.rawQuery("SELECT snippet(t, '[', ']', '…') FROM t WHERE t MATCH ?", arrayOf("hausarzt")).use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertTrue(c.getString(0), c.getString(0).contains("[Hausarzt]"))
        }
        db.rawQuery("SELECT count(*) FROM t WHERE t MATCH ?", arrayOf("geburt*")).use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        db.close()
    }

    @Test
    fun unicode61RemovesDiacritics() {
        val db = db()
        db.execSQL("CREATE VIRTUAL TABLE u USING fts4(body, tokenize=unicode61 \"remove_diacritics=2\")")
        db.execSQL("INSERT INTO u(body) VALUES ('Müller Jänner Straße')")
        db.rawQuery("SELECT count(*) FROM u WHERE u MATCH ?", arrayOf("muller")).use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        db.rawQuery("SELECT count(*) FROM u WHERE u MATCH ?", arrayOf("janner")).use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        db.close()
    }

    @Test
    fun userVersionMigrationHook() {
        val db = db()
        assertEquals(0, db.version)
        db.version = 3
        assertEquals(3, db.version)
        db.close()
    }
}
