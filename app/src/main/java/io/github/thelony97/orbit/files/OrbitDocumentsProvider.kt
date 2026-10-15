package io.github.thelony97.orbit.files

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import io.github.thelony97.orbit.R
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.domain.Logic
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException

/**
 * Ordner pro Person in jedem Datei-Dialog (PLAN 9 A6): „Orbit“ in the system file picker – people
 * and organisations with files are folders, their photos and PDFs inside. Read only; never the
 * archive, the trash or hidden members (they have no folder of their own).
 */
class OrbitDocumentsProvider : DocumentsProvider() {

    private val rootColumns = arrayOf(
        Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS,
        Root.COLUMN_ICON, Root.COLUMN_MIME_TYPES,
    )
    private val docColumns = arrayOf(
        Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
        Document.COLUMN_FLAGS, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED,
    )

    override fun onCreate(): Boolean = true

    private fun contacts(): List<Contact> {
        val ctx = context ?: return emptyList()
        // Providers can be asked before the app finished starting: make sure the data is there.
        OrbitRepository.init(ctx.applicationContext)
        val data = runCatching { runBlocking { OrbitRepository.snapshot() } }.getOrNull() ?: return emptyList()
        if (OrbitRepository.loadError.value != null) return emptyList()
        return data.contacts
            .filter { it.deletedAt == null && !it.archived && it.gallery.isNotEmpty() }
            .sortedWith(compareBy<Contact> { !it.isMe }.thenBy { Logic.sortKey(it, data.settings.sortByLastName) })
    }

    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: rootColumns).apply {
            newRow()
                .add(Root.COLUMN_ROOT_ID, ROOT)
                .add(Root.COLUMN_DOCUMENT_ID, ROOT)
                .add(Root.COLUMN_TITLE, "Orbit")
                .add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY or Root.FLAG_SUPPORTS_IS_CHILD)
                .add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
                .add(Root.COLUMN_MIME_TYPES, "image/*\napplication/pdf")
        }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val out = MatrixCursor(projection ?: docColumns)
        when {
            documentId == ROOT -> folderRow(out, ROOT, "Orbit")
            documentId.startsWith("c:") -> contacts().firstOrNull { it.id == documentId.removePrefix("c:") }
                ?.let { folderRow(out, documentId, Logic.displayName(it, false)) }
            documentId.startsWith("f:") -> fileOf(documentId)?.let { (c, a) -> fileRow(out, c, a) }
        }
        return out
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        val out = MatrixCursor(projection ?: docColumns)
        when {
            parentDocumentId == ROOT -> contacts().forEach { c -> folderRow(out, "c:" + c.id, Logic.displayName(c, false)) }
            parentDocumentId.startsWith("c:") -> contacts().firstOrNull { it.id == parentDocumentId.removePrefix("c:") }
                ?.let { c -> c.gallery.forEach { a -> fileRow(out, c, a) } }
        }
        return out
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean = when {
        parentDocumentId == ROOT -> documentId != ROOT
        parentDocumentId.startsWith("c:") -> documentId.startsWith("f:" + parentDocumentId.removePrefix("c:") + ":")
        else -> false
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val (_, a) = fileOf(documentId) ?: throw FileNotFoundException(documentId)
        val file = OrbitRepository.photoFile(a.file)
        if (!file.exists()) throw FileNotFoundException(documentId)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun fileOf(documentId: String): Pair<Contact, io.github.thelony97.orbit.data.GalleryPhoto>? {
        val parts = documentId.removePrefix("f:").split(':', limit = 2)
        if (parts.size != 2) return null
        val c = contacts().firstOrNull { it.id == parts[0] } ?: return null
        val a = c.gallery.firstOrNull { it.id == parts[1] } ?: return null
        return c to a
    }

    private fun folderRow(out: MatrixCursor, id: String, name: String) {
        out.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, id)
            .add(Document.COLUMN_DISPLAY_NAME, name)
            .add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
            .add(Document.COLUMN_FLAGS, 0)
            .add(Document.COLUMN_SIZE, null)
            .add(Document.COLUMN_LAST_MODIFIED, null)
    }

    private fun fileRow(out: MatrixCursor, c: Contact, a: io.github.thelony97.orbit.data.GalleryPhoto) {
        val file = OrbitRepository.photoFile(a.file)
        val ext = if (a.mime == "application/pdf") ".pdf" else ".jpg"
        val name = a.name.ifBlank { Logic.displayName(c, false) + " " + a.id.take(6) + ext }
        out.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, "f:" + c.id + ":" + a.id)
            .add(Document.COLUMN_DISPLAY_NAME, if (Regex("\\.[A-Za-z0-9]{2,4}$").containsMatchIn(name)) name else name + ext)
            .add(Document.COLUMN_MIME_TYPE, a.mime)
            .add(Document.COLUMN_FLAGS, 0)
            .add(Document.COLUMN_SIZE, file.length())
            .add(Document.COLUMN_LAST_MODIFIED, (a.takenAt ?: a.addedAt).takeIf { it > 0 } ?: file.lastModified())
    }

    private companion object {
        const val ROOT = "root"
    }
}
