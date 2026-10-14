package io.github.thelony97.orbit.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.thelony97.orbit.core.domain.BackupCrypto
import io.github.thelony97.orbit.core.domain.migration.MigrateV1
import io.github.thelony97.orbit.core.model.Attachment
import io.github.thelony97.orbit.core.model.HistoryEntry
import io.github.thelony97.orbit.data.Mutations
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.data.now
import io.github.thelony97.orbit.store.Store
import io.github.thelony97.orbit.notify.Notifications
import io.github.thelony97.orbit.domain.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backups are plain ZIP files: orbit.json plus the photos folder (profile photos and photo folders).
 * Mail passwords are never part of a backup.
 * Weekly backups go into a folder the user picked (e.g. a Google Drive folder).
 */
object Backups {

    private const val WORK = "orbit-weekly-backup"
    private const val KEEP = 6
    private const val PREFIX = "orbit-backup-"

    private val pretty = Json(from = OrbitRepository.json) { prettyPrint = true }

    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        val request = PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Date and time in the name: a new backup never truncates an existing one of the same day. */
    fun fileName(): String =
        "$PREFIX${today()}-${java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HHmmss"))}.zip"

    /** A backup is encrypted with the stored key once a password is set (PLAN 4). */
    class NeedsPassword : Exception("Diese Sicherung ist verschlüsselt.")

    suspend fun writeZip(ctx: Context, out: OutputStream) = withContext(Dispatchers.IO) {
        // Never back up the empty stand-in state after a failed load.
        check(OrbitRepository.loadError.value == null) { "Daten konnten nicht geladen werden – keine Sicherung." }
        val key = BackupKey.load(ctx)
        // Encryption is on but the key is unreadable: fail loudly instead of writing plain text.
        check(key != null || !BackupKey.isSet(ctx)) { "Backup-Schlüssel nicht lesbar – Passwort neu setzen." }
        if (key == null) {
            zipTo(out)
        } else {
            // Stream through a temp file: memory stays at one chunk even with many photos.
            val tmp = File.createTempFile("backup", ".zip", ctx.cacheDir)
            try {
                tmp.outputStream().use { zipTo(it) }
                tmp.inputStream().buffered().use { BackupCrypto.encrypt(it, out, key) }
            } finally {
                tmp.delete()
            }
        }
    }

    private suspend fun zipTo(out: OutputStream) {
        val data = OrbitRepository.snapshot()
        ZipOutputStream(out.buffered()).use { zip ->
            // Storage v2: core, Verlauf and Ablage as separate JSON files (ADR 0003).
            zip.putNextEntry(ZipEntry("orbit.json"))
            zip.write(pretty.encodeToString(OrbitData.serializer(), data).toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("history.json"))
            zip.write(OrbitRepository.json.encodeToString(HISTORY, Store.historyOf(data)).toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("attachments.json"))
            zip.write(OrbitRepository.json.encodeToString(ATTACHMENTS, Store.attachmentsOf(data)).toByteArray())
            zip.closeEntry()
            data.contacts.flatMap { Mutations.files(it) }.distinct().forEach { name ->
                val file = OrbitRepository.photoFile(name)
                if (file.exists()) {
                    zip.putNextEntry(ZipEntry("photos/$name"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /**
     * Reads a backup and returns its data without applying it (photos are restored right away).
     * Encrypted backups need [password]; without it [NeedsPassword] is thrown, a wrong one
     * throws [BackupCrypto.WrongPassword].
     */
    suspend fun readZip(ctx: Context, input: InputStream, password: CharArray? = null): OrbitData = withContext(Dispatchers.IO) {
        val buffered = input.buffered()
        buffered.mark(8)
        val head = buffered.readNBytes(4)
        buffered.reset()
        if (!BackupCrypto.isEncrypted(head)) return@withContext readPlain(buffered)
        val pw = password ?: throw NeedsPassword()
        // Decrypt completely first: a wrong password fails before any photo is written.
        val tmp = File.createTempFile("restore", ".zip", ctx.cacheDir)
        try {
            tmp.outputStream().use { BackupCrypto.decrypt(buffered, it, pw) }
            tmp.inputStream().buffered().use { readPlain(it) }
        } finally {
            tmp.delete()
        }
    }

    private fun readPlain(source: InputStream): OrbitData {
        var core: String? = null
        var history: String? = null
        var attachments: String? = null
        ZipInputStream(source).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.name == "orbit.json" -> core = zip.readBytes().decodeToString()
                    entry.name == "history.json" -> history = zip.readBytes().decodeToString()
                    entry.name == "attachments.json" -> attachments = zip.readBytes().decodeToString()
                    entry.name.startsWith("photos/") && !entry.isDirectory -> {
                        val name = File(entry.name).name
                        if (name.isNotBlank() && !name.contains("..")) {
                            OrbitRepository.photoFile(name).outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        val text = core ?: throw IllegalArgumentException("Das ist keine Orbit-Sicherung (orbit.json fehlt).")
        return fromBackup(text, history, attachments)
    }

    private val HISTORY = ListSerializer(HistoryEntry.serializer())
    private val ATTACHMENTS = ListSerializer(Attachment.serializer())

    /** Backups from 1.0.7 (schema 1, everything in orbit.json) and from v2 both restore. */
    internal fun fromBackup(core: String, history: String?, attachments: String?): OrbitData {
        if (MigrateV1.schemaOf(core) < 2) {
            val r = MigrateV1.fromJson(core, ::newId)
            return Store.assemble(io.github.thelony97.orbit.core.domain.migration.MigrateV2.upgrade(r.doc), r.history, r.attachments)
        }
        require(history != null) { "Sicherung unvollständig (history.json fehlt)." }
        // A schema-2 backup (before R4) gets its Zuordnungen like the stored file does.
        val doc = io.github.thelony97.orbit.core.domain.migration.MigrateV2.upgrade(OrbitRepository.json.decodeFromString(OrbitData.serializer(), core))
        val h = history.let { OrbitRepository.json.decodeFromString(HISTORY, it) }.orEmpty()
        val a = attachments?.let { OrbitRepository.json.decodeFromString(ATTACHMENTS, it) }.orEmpty()
        return Store.assemble(doc, h, a)
    }

    /** Applies restored data but keeps device-specific settings of this phone. */
    suspend fun restore(ctx: Context, restored: OrbitData) {
        val current = OrbitRepository.snapshot().settings
        val merged = OrbitRepository.migrate(restored).let { d ->
            d.copy(
                settings = d.settings.copy(
                    onboardingDone = true,
                    backupTreeUri = current.backupTreeUri,
                    lockEnabled = current.lockEnabled,
                    lastDigestDay = current.lastDigestDay,
                ),
            )
        }
        OrbitRepository.replaceAll(merged)
    }

    /** Writes a dated backup into the chosen folder and prunes old ones. */
    suspend fun backupToFolder(ctx: Context): Result<String> = runCatching {
        check(OrbitRepository.loadError.value == null) { "Daten konnten nicht geladen werden – keine Sicherung." }
        val treeString = OrbitRepository.snapshot().settings.backupTreeUri
            ?: throw IllegalStateException("Kein Backup-Ordner gewählt")
        val tree = Uri.parse(treeString)
        val resolver = ctx.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val name = fileName()
        val target = DocumentsContract.createDocument(resolver, parent, "application/zip", name)
            ?: throw IllegalStateException("Datei konnte nicht angelegt werden")
        try {
            (resolver.openOutputStream(target, "w") ?: throw IllegalStateException("Ordner nicht beschreibbar")).use {
                writeZip(ctx, it)
            }
        } catch (e: Throwable) {
            // A half-written file must not count as one of the kept backups.
            runCatching { DocumentsContract.deleteDocument(resolver, target) }
            throw e
        }
        listBackups(ctx, tree).sortedByDescending { it.second }.drop(KEEP).forEach { (uri, _) ->
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
        }
        OrbitRepository.update { d -> d.copy(settings = d.settings.copy(lastBackupAt = now())) }
        name
    }

    private fun listBackups(ctx: Context, tree: Uri): List<Pair<Uri, String>> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val out = ArrayList<Pair<Uri, String>>()
        ctx.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val name = c.getString(1) ?: continue
                if (name.startsWith(PREFIX) && name.endsWith(".zip")) {
                    out += DocumentsContract.buildDocumentUriUsingTree(tree, id) to name
                }
            }
        }
        return out
    }
}

class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        OrbitRepository.init(applicationContext)
        val settings = OrbitRepository.ensureLoaded().settings
        if (!settings.autoBackup || settings.backupTreeUri == null) return Result.success()
        val result = Backups.backupToFolder(applicationContext)
        OrbitRepository.flush()
        return if (result.isSuccess) {
            Result.success()
        } else {
            if (runAttemptCount >= 2) {
                Notifications.backupFailed(applicationContext, result.exceptionOrNull()?.message ?: "Unbekannter Fehler")
                Result.success()
            } else {
                Result.retry()
            }
        }
    }
}
