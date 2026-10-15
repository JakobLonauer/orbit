package io.github.thelony97.orbit.ui.list

import android.app.Activity
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.ui.components.AppWork
import io.github.thelony97.orbit.ui.components.Undo
import io.github.thelony97.orbit.ui.detail.GalleryStore

private fun accepts(mime: String) = mime.startsWith("image/") || mime == "application/pdf"

/**
 * Ziehen & Ablegen (PLAN 9 E20): a photo or PDF dragged from another app (split screen) onto a
 * contact's row lands in their folder; the row lifts while something hovers over it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.acceptFiles(contactId: String): Modifier {
    val ctx = LocalContext.current
    var hover by remember { mutableStateOf(false) }
    val target = remember(contactId) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { hover = true }
            override fun onExited(event: DragAndDropEvent) { hover = false }
            override fun onEnded(event: DragAndDropEvent) { hover = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                hover = false
                val drag = event.toAndroidDragEvent()
                (ctx as? Activity)?.requestDragAndDropPermissions(drag)
                val clip = drag.clipData ?: return false
                val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
                if (uris.isEmpty()) return false
                AppWork.launch { drop(ctx, contactId, uris) }
                return true
            }
        }
    }
    return this
        .graphicsLayer { val s = if (hover) 1.03f else 1f; scaleX = s; scaleY = s }
        .dragAndDropTarget(shouldStartDragAndDrop = { e -> e.mimeTypes().any(::accepts) }, target = target)
}

private suspend fun drop(ctx: android.content.Context, contactId: String, uris: List<Uri>) {
    val (docs, pics) = uris.partition { ctx.contentResolver.getType(it) == "application/pdf" }
    val added = (GalleryStore.import(ctx.applicationContext, pics) + GalleryStore.importDocs(ctx.applicationContext, docs)).map { it.copy(contactId = contactId) }
    if (added.isEmpty()) return
    OrbitRepository.update { d -> d.copy(contacts = d.contacts.map { if (it.id == contactId) it.copy(gallery = added.asReversed() + it.gallery) else it }) }
    Undo.offer(if (added.size == 1) "Abgelegt" else "${added.size} abgelegt") {
        val ids = added.map { it.id }.toSet()
        OrbitRepository.edit { d -> d.copy(contacts = d.contacts.map { if (it.id == contactId) it.copy(gallery = it.gallery.filterNot { g -> g.id in ids }) else it }) }
    }
}
