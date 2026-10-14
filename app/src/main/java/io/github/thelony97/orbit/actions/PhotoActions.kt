package io.github.thelony97.orbit.actions

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.security.AppLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Pictures and documents out of Orbit (PLAN 3.10): share, save back to the phone, open. */
object PhotoActions {

    private fun shareCopy(ctx: Context, file: String, as_: String): File {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - 10 * 60_000L
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        val target = File(dir, io.github.thelony97.orbit.core.domain.FileNames.safe(as_, "Orbit-$file"))
        // Never outside the share folder, whatever the name says.
        check(target.canonicalFile.parentFile == dir.canonicalFile) { "Dateiname" }
        return target.also { OrbitRepository.photoFile(file).copyTo(it, overwrite = true) }
    }

    suspend fun share(ctx: Context, file: String, mime: String = "image/jpeg", name: String = "") {
        val copy = withContext(Dispatchers.IO) {
            runCatching { shareCopy(ctx, file, name.ifBlank { "Orbit-" + file.substringBeforeLast('.') + if (mime == "application/pdf") ".pdf" else ".jpg" }) }.getOrNull()
        } ?: return Toast.makeText(ctx, "Teilen nicht möglich", Toast.LENGTH_SHORT).show()
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".share", copy)
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        AppLock.allowExternal()
        runCatching { ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
    }

    /** Opens a document (PDF) in the phone's viewer. */
    suspend fun open(ctx: Context, file: String, mime: String, name: String) {
        val copy = withContext(Dispatchers.IO) { runCatching { shareCopy(ctx, file, name.ifBlank { file }) }.getOrNull() }
            ?: return Toast.makeText(ctx, "Öffnen nicht möglich", Toast.LENGTH_SHORT).show()
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".share", copy)
        AppLock.allowExternal()
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
            .onFailure { Toast.makeText(ctx, "Keine App zum Öffnen", Toast.LENGTH_SHORT).show() }
    }

    /** „Ins Handy speichern“: into Pictures/Orbit (Downloads for documents); no permission needed. */
    suspend fun saveToPhone(ctx: Context, file: String, mime: String = "image/jpeg", name: String = ""): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val pdf = mime == "application/pdf"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name.ifBlank { "Orbit-" + file.substringBeforeLast('.') })
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (pdf) "Download/Orbit" else "Pictures/Orbit")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = if (pdf) MediaStore.Downloads.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uri = ctx.contentResolver.insert(collection, values) ?: return@runCatching false
            ctx.contentResolver.openOutputStream(uri)?.use { out -> OrbitRepository.photoFile(file).inputStream().use { it.copyTo(out) } }
            ctx.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            true
        }.getOrDefault(false)
    }
}
