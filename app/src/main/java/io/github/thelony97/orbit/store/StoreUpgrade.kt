package io.github.thelony97.orbit.store

import io.github.thelony97.orbit.core.domain.DocJson
import io.github.thelony97.orbit.core.domain.migration.MigrateV1
import io.github.thelony97.orbit.core.domain.migration.MigrateV2
import io.github.thelony97.orbit.core.model.SCHEMA_V3
import java.io.File
import java.io.FileOutputStream

/**
 * One-time switch from the 1.0.7 file to storage v2 (PLAN 2: data must never get lost).
 *
 * 1. Keep a copy of the old file (`orbit.v1.bak.json`, never overwritten).
 * 2. Move history and photos into SQLite (idempotent: INSERT OR IGNORE).
 * 3. Only then replace `orbit.json` atomically with the v2 document.
 * A crash anywhere leaves schema 1 on disk, so the next start simply runs it again.
 *
 * Schema 2 -> 3 (R4, Zuordnungen): keep `orbit.v2.bak.json` (never overwritten), then replace
 * `orbit.json` with the converted document. Only the JSON core changes; SQLite is untouched.
 */
object StoreUpgrade {
    const val BACKUP = "orbit.v1.bak.json"
    const val BACKUP_V2 = "orbit.v2.bak.json"

    /** [migrated] = the 1.0.7 file was converted; [toV3] = a schema-2 file was converted. */
    data class Report(val migrated: Boolean, val entries: Int = 0, val attachments: Int = 0, val toV3: Boolean = false)

    fun run(file: File, db: HistoryDb, newId: () -> String): Report {
        if (!file.exists()) return Report(false)
        val text = file.readText()
        val schema = MigrateV1.schemaOf(text)
        if (schema >= SCHEMA_V3) return Report(false)
        if (schema == 2) {
            val backup = File(file.parentFile, BACKUP_V2)
            if (!backup.exists()) atomicWrite(backup, text)
            atomicWrite(file, DocJson.encode(MigrateV2.upgrade(DocJson.decode(text))))
            return Report(false, toV3 = true)
        }

        val backup = File(file.parentFile, BACKUP)
        if (!backup.exists()) atomicWrite(backup, text)

        val result = MigrateV1.fromJson(text, newId)
        db.insertAll(result.history)
        db.insertAttachments(result.attachments)
        // Nothing may be lost: every row must be in the database before the old file is replaced.
        check(db.count() >= result.history.size) { "Verlauf unvollständig übernommen" }
        db.checkpoint()
        // Straight on to schema 3: the v1 copy above already holds the original.
        atomicWrite(file, DocJson.encode(MigrateV2.upgrade(result.doc)))
        return Report(true, result.history.size, result.attachments.size)
    }

    internal fun atomicWrite(target: File, content: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(content.toByteArray())
            out.fd.sync()
        }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }
}
