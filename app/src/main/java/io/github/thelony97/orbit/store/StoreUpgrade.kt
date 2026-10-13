package io.github.thelony97.orbit.store

import io.github.thelony97.orbit.core.domain.DocJson
import io.github.thelony97.orbit.core.domain.migration.MigrateV1
import java.io.File
import java.io.FileOutputStream

/**
 * One-time switch from the 1.0.7 file to storage v2 (PLAN 2: data must never get lost).
 *
 * 1. Keep a copy of the old file (`orbit.v1.bak.json`, never overwritten).
 * 2. Move history and photos into SQLite (idempotent: INSERT OR IGNORE).
 * 3. Only then replace `orbit.json` atomically with the v2 document.
 * A crash anywhere leaves schema 1 on disk, so the next start simply runs it again.
 */
object StoreUpgrade {
    const val BACKUP = "orbit.v1.bak.json"

    data class Report(val migrated: Boolean, val entries: Int = 0, val attachments: Int = 0)

    fun run(file: File, db: HistoryDb, newId: () -> String): Report {
        if (!file.exists()) return Report(false)
        val text = file.readText()
        if (MigrateV1.schemaOf(text) >= 2) return Report(false)

        val backup = File(file.parentFile, BACKUP)
        if (!backup.exists()) atomicWrite(backup, text)

        val result = MigrateV1.fromJson(text, newId)
        db.insertAll(result.history)
        db.insertAttachments(result.attachments)
        atomicWrite(file, DocJson.encode(result.doc))
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
