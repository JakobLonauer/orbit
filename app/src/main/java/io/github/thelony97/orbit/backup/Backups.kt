package io.github.thelony97.orbit.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.now
import io.github.thelony97.orbit.notify.Notifications
import io.github.thelony97.orbit.domain.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backups are plain ZIP files: orbit.json plus the photos folder.
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

    fun fileName(): String = "$PREFIX${today()}.zip"

    suspend fun writeZip(out: OutputStream) = withContext(Dispatchers.IO) {
        val data = OrbitRepository.snapshot()
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("orbit.json"))
            zip.write(pretty.encodeToString(OrbitData.serializer(), data).toByteArray())
            zip.closeEntry()
            data.contacts.mapNotNull { it.photo }.distinct().forEach { name ->
                val file = OrbitRepository.photoFile(name)
                if (file.exists()) {
                    zip.putNextEntry(ZipEntry("photos/$name"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Reads a backup and returns its data without applying it (photos are restored right away). */
    suspend fun readZip(input: InputStream): OrbitData = withContext(Dispatchers.IO) {
        var data: OrbitData? = null
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.name == "orbit.json" -> {
                        val text = zip.readBytes().decodeToString()
                        data = OrbitRepository.json.decodeFromString(OrbitData.serializer(), text)
                    }
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
        data ?: throw IllegalArgumentException("Das ist keine Orbit-Sicherung (orbit.json fehlt).")
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
        val treeString = OrbitRepository.snapshot().settings.backupTreeUri
            ?: throw IllegalStateException("Kein Backup-Ordner gewählt")
        val tree = Uri.parse(treeString)
        val resolver = ctx.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val name = fileName()
        val existing = listBackups(ctx, tree).firstOrNull { it.second == name }
        val target = existing?.first
            ?: DocumentsContract.createDocument(resolver, parent, "application/zip", name)
            ?: throw IllegalStateException("Datei konnte nicht angelegt werden")
        (resolver.openOutputStream(target, "wt") ?: throw IllegalStateException("Ordner nicht beschreibbar")).use {
            writeZip(it)
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
