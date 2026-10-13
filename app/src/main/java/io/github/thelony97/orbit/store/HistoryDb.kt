package io.github.thelony97.orbit.store

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import io.github.thelony97.orbit.core.domain.DocJson
import io.github.thelony97.orbit.core.domain.Fold
import io.github.thelony97.orbit.core.model.Attachment
import io.github.thelony97.orbit.core.model.HistoryEntry

/**
 * Verlauf + Ablage in SQLite (ADR 0003). Each row keeps its full JSON next to the indexed
 * columns, so new optional fields need no SQL migration. Schema changes bump [VERSION]
 * and get a step in [onUpgrade] plus a test.
 *
 * Full text: FTS4 with unicode61 (diacritic-insensitive). If a device's SQLite lacks it,
 * the table falls back to the simple tokenizer and stores folded text (see `meta.fts_folded`).
 */
class HistoryDb(context: Context, name: String? = NAME) :
    SQLiteOpenHelper(context, name, null, VERSION) {

    data class Hit(val entry: HistoryEntry, val snippet: String)

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL(
            "CREATE TABLE entries(id TEXT PRIMARY KEY, contact_id TEXT NOT NULL, type_id TEXT NOT NULL, " +
                "at INTEGER NOT NULL, external_id TEXT, member_id TEXT, json TEXT NOT NULL)",
        )
        // "sms:<day>" is only unique per contact; calls/SMS of different people may share it.
        db.execSQL("CREATE UNIQUE INDEX entries_contact_ext ON entries(contact_id, external_id)")
        db.execSQL("CREATE INDEX entries_contact_at ON entries(contact_id, at DESC)")
        db.execSQL("CREATE INDEX entries_at ON entries(at DESC)")
        val folded = try {
            db.execSQL("CREATE VIRTUAL TABLE entries_fts USING fts4(body, tokenize=unicode61 \"remove_diacritics=2\")")
            false
        } catch (e: SQLiteException) {
            db.execSQL("CREATE VIRTUAL TABLE entries_fts USING fts4(body)")
            true
        }
        db.execSQL("INSERT INTO meta(key, value) VALUES ('fts_folded', ?)", arrayOf<Any>(if (folded) "1" else "0"))
        db.execSQL(
            "CREATE TABLE attachments(id TEXT PRIMARY KEY, contact_id TEXT NOT NULL, file TEXT NOT NULL, " +
                "added_at INTEGER NOT NULL, json TEXT NOT NULL)",
        )
        db.execSQL("CREATE INDEX attachments_contact ON attachments(contact_id, added_at DESC)")
        db.execSQL("CREATE INDEX attachments_file ON attachments(file)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 is the first version; future steps go here: if (oldVersion < 2) { … }
    }

    private val folded: Boolean by lazy {
        readableDatabase.rawQuery("SELECT value FROM meta WHERE key = 'fts_folded'", null).use {
            it.moveToFirst() && it.getString(0) == "1"
        }
    }

    private fun ftsBody(e: HistoryEntry): String {
        val text = buildString {
            append(e.note)
            e.messages.forEach { append('\n').append(it.text) }
        }
        return if (folded) Fold.fold(text) else text
    }

    /** Inserts rows that are not there yet (same id, or same contact + externalId = skipped). Returns how many were new. */
    fun insertAll(entries: List<HistoryEntry>): Int {
        if (entries.isEmpty()) return 0
        val db = writableDatabase
        folded // resolve the tokenizer flag outside the transaction
        var added = 0
        db.beginTransaction()
        try {
            for (e in entries) {
                val rowId = db.insertWithOnConflict("entries", null, values(e), SQLiteDatabase.CONFLICT_IGNORE)
                if (rowId == -1L) continue
                db.execSQL("INSERT INTO entries_fts(docid, body) VALUES (?, ?)", arrayOf<Any>(rowId, ftsBody(e)))
                added++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return added
    }

    fun update(e: HistoryEntry) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.update("entries", values(e), "id = ?", arrayOf(e.id))
            db.execSQL(
                "UPDATE entries_fts SET body = ? WHERE docid = (SELECT rowid FROM entries WHERE id = ?)",
                arrayOf<Any>(ftsBody(e), e.id),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun delete(id: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM entries_fts WHERE docid = (SELECT rowid FROM entries WHERE id = ?)", arrayOf<Any>(id))
            db.delete("entries", "id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun forContact(contactId: String, limit: Int = 200, before: Long = Long.MAX_VALUE): List<HistoryEntry> =
        readableDatabase.rawQuery(
            "SELECT json FROM entries WHERE contact_id = ? AND at < ? ORDER BY at DESC LIMIT ?",
            arrayOf(contactId, before.toString(), limit.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(DocJson.decodeEntry(c.getString(0))) } }

    /** Every row, newest first – for building indexes and for backups. */
    fun all(): List<HistoryEntry> =
        readableDatabase.rawQuery("SELECT json FROM entries ORDER BY at DESC", null)
            .use { c -> buildList { while (c.moveToNext()) add(DocJson.decodeEntry(c.getString(0))) } }

    fun count(): Int = readableDatabase.rawQuery("SELECT count(*) FROM entries", null)
        .use { it.moveToFirst(); it.getInt(0) }

    /**
     * Full-text search, newest first. Conversations with hidden members are excluded unless
     * [includeMembers] (only the organisation's own search sets it).
     */
    fun search(query: String, limit: Int = 50, includeMembers: Boolean = false): List<Hit> {
        val match = matchExpression(query) ?: return emptyList()
        val sql = "SELECT e.json, snippet(entries_fts, '«', '»', '…', -1, 10) FROM entries_fts " +
            "JOIN entries e ON e.rowid = entries_fts.docid WHERE entries_fts MATCH ? " +
            (if (includeMembers) "" else "AND e.member_id IS NULL ") +
            "ORDER BY e.at DESC LIMIT ?"
        return readableDatabase.rawQuery(sql, arrayOf(match, limit.toString())).use { c ->
            buildList { while (c.moveToNext()) add(Hit(DocJson.decodeEntry(c.getString(0)), c.getString(1))) }
        }
    }

    /** User text -> safe FTS expression: every word as prefix term, all must match. */
    internal fun matchExpression(query: String): String? {
        val source = if (folded) Fold.fold(query) else query
        val terms = source.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        return if (terms.isEmpty()) null else terms.joinToString(" ") { "$it*" }
    }

    fun insertAttachments(items: List<Attachment>): Int {
        if (items.isEmpty()) return 0
        val db = writableDatabase
        var added = 0
        db.beginTransaction()
        try {
            for (a in items) {
                val v = ContentValues().apply {
                    put("id", a.id); put("contact_id", a.contactId); put("file", a.file)
                    put("added_at", a.addedAt); put("json", DocJson.encode(a))
                }
                if (db.insertWithOnConflict("attachments", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1L) added++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return added
    }

    fun attachments(contactId: String): List<Attachment> =
        readableDatabase.rawQuery(
            "SELECT json FROM attachments WHERE contact_id = ? ORDER BY added_at DESC", arrayOf(contactId),
        ).use { c -> buildList { while (c.moveToNext()) add(DocJson.decodeAttachment(c.getString(0))) } }

    /** Reference count of a stored file; the file may only be deleted at 0. */
    fun references(file: String): Int =
        readableDatabase.rawQuery("SELECT count(*) FROM attachments WHERE file = ?", arrayOf(file))
            .use { it.moveToFirst(); it.getInt(0) }

    fun deleteAttachment(id: String) {
        writableDatabase.delete("attachments", "id = ?", arrayOf(id))
    }

    fun allAttachments(): List<Attachment> =
        readableDatabase.rawQuery("SELECT json FROM attachments ORDER BY added_at DESC", null)
            .use { c -> buildList { while (c.moveToNext()) add(DocJson.decodeAttachment(c.getString(0))) } }

    /** One change set, applied atomically (the repository's save step). */
    class Changes(
        val upserts: List<HistoryEntry> = emptyList(),
        val deletes: List<String> = emptyList(),
        val attachmentUpserts: List<Attachment> = emptyList(),
        val attachmentDeletes: List<String> = emptyList(),
        val contactsGone: List<String> = emptyList(),
    ) {
        val isEmpty: Boolean
            get() = upserts.isEmpty() && deletes.isEmpty() && attachmentUpserts.isEmpty() &&
                attachmentDeletes.isEmpty() && contactsGone.isEmpty()
    }

    fun apply(ch: Changes) {
        if (ch.isEmpty) return
        val db = writableDatabase
        folded // resolve the tokenizer flag outside the transaction
        db.beginTransaction()
        try {
            for (id in ch.contactsGone) {
                db.execSQL("DELETE FROM entries_fts WHERE docid IN (SELECT rowid FROM entries WHERE contact_id = ?)", arrayOf<Any>(id))
                db.delete("entries", "contact_id = ?", arrayOf(id))
                db.delete("attachments", "contact_id = ?", arrayOf(id))
            }
            for (id in ch.deletes) {
                db.execSQL("DELETE FROM entries_fts WHERE docid = (SELECT rowid FROM entries WHERE id = ?)", arrayOf<Any>(id))
                db.delete("entries", "id = ?", arrayOf(id))
            }
            for (e in ch.upserts) {
                val updated = db.updateWithOnConflict("entries", values(e), "id = ?", arrayOf(e.id), SQLiteDatabase.CONFLICT_IGNORE)
                if (updated > 0) {
                    db.execSQL(
                        "UPDATE entries_fts SET body = ? WHERE docid = (SELECT rowid FROM entries WHERE id = ?)",
                        arrayOf<Any>(ftsBody(e), e.id),
                    )
                } else {
                    val rowId = db.insertWithOnConflict("entries", null, values(e), SQLiteDatabase.CONFLICT_IGNORE)
                    if (rowId != -1L) {
                        db.execSQL("INSERT INTO entries_fts(docid, body) VALUES (?, ?)", arrayOf<Any>(rowId, ftsBody(e)))
                    }
                }
            }
            for (id in ch.attachmentDeletes) db.delete("attachments", "id = ?", arrayOf(id))
            for (a in ch.attachmentUpserts) {
                val v = ContentValues().apply {
                    put("id", a.id); put("contact_id", a.contactId); put("file", a.file)
                    put("added_at", a.addedAt); put("json", DocJson.encode(a))
                }
                db.insertWithOnConflict("attachments", null, v, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun values(e: HistoryEntry) = ContentValues().apply {
        put("id", e.id)
        put("contact_id", e.contactId)
        put("type_id", e.typeId)
        put("at", e.at)
        if (e.externalId != null) put("external_id", e.externalId) else putNull("external_id")
        if (e.memberId != null) put("member_id", e.memberId) else putNull("member_id")
        put("json", DocJson.encode(e))
    }

    companion object {
        const val NAME = "orbit.db"
        const val VERSION = 1
    }
}
