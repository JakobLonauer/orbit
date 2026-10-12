package io.github.thelony97.orbit.ui.detail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.GalleryPhoto
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.data.now
import io.github.thelony97.orbit.security.AppLock
import io.github.thelony97.orbit.ui.Photos
import io.github.thelony97.orbit.ui.components.ConfirmDialog
import io.github.thelony97.orbit.ui.components.OrbButton
import io.github.thelony97.orbit.ui.components.PhotoCache
import io.github.thelony97.orbit.ui.components.SectionCard
import io.github.thelony97.orbit.ui.theme.Space
import io.github.thelony97.orbit.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Photo folder per contact. Files live next to the profile photos and travel with the backup. */
object GalleryStore {
    private const val MAX_EDGE = 2048
    private val thumbs = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    suspend fun import(ctx: Context, uris: List<Uri>): List<GalleryPhoto> = withContext(Dispatchers.IO) {
        uris.mapNotNull { uri ->
            runCatching {
                val source = ImageDecoder.createSource(ctx.contentResolver, uri)
                val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val longest = maxOf(info.size.width, info.size.height)
                    if (longest > MAX_EDGE) {
                        val f = MAX_EDGE.toFloat() / longest
                        decoder.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
                    }
                }
                val name = "g-${newId()}.jpg"
                OrbitRepository.photoFile(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it) }
                GalleryPhoto(id = newId(), file = name, addedAt = now())
            }.getOrNull()
        }
    }

    fun delete(name: String) {
        OrbitRepository.photoFile(name).delete()
        thumbs.remove(name)
    }

    private fun decode(file: File, target: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    suspend fun thumb(name: String): ImageBitmap? = thumbs.get(name) ?: withContext(Dispatchers.IO) {
        val file = OrbitRepository.photoFile(name)
        if (!file.exists()) return@withContext null
        decode(file, 320)?.asImageBitmap()?.also { thumbs.put(name, it) }
    }

    suspend fun full(name: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val file = OrbitRepository.photoFile(name)
        if (!file.exists()) null else decode(file, 1600)?.asImageBitmap()
    }
}

@Composable
fun GallerySection(contact: Contact) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var viewer by remember { mutableStateOf<Int?>(null) }
    var importing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isNotEmpty()) {
            importing = true
            scope.launch {
                // Finish even if the user leaves the screen meanwhile, otherwise the copied files would be orphaned.
                withContext(NonCancellable) {
                    val added = GalleryStore.import(ctx.applicationContext, uris)
                    OrbitRepository.editContact(contact.id) { it.copy(gallery = added.asReversed() + it.gallery) }
                }
                importing = false
            }
        }
    }
    val photos = contact.gallery

    SectionCard(
        title = if (photos.isEmpty()) "Fotos" else "Fotos · ${photos.size}",
        icon = Icons.Rounded.PhotoLibrary,
        action = {
            Icon(
                Icons.Rounded.AddPhotoAlternate, "Fotos hinzufügen",
                tint = Space.Violet,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        AppLock.allowExternal()
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    .padding(5.dp),
            )
        },
    ) {
        if (photos.isEmpty() && !importing) {
            Text(
                "Eigener Ordner für Bilder mit ${if (contact.isOrg) "dieser Organisation" else contact.firstName.ifBlank { "dieser Person" }} – getrennt vom Profilbild.",
                style = MaterialTheme.typography.bodySmall,
                color = Space.TextFaint,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        if (importing) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                LoadingIndicator(Modifier.size(28.dp), color = Space.Violet)
                Spacer(Modifier.width(8.dp))
                Text("Fotos werden übernommen …", style = MaterialTheme.typography.bodySmall, color = Space.TextDim)
            }
        }
        val shown = photos.take(9)
        shown.chunked(3).forEachIndexed { row, chunk ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                chunk.forEachIndexed { col, p ->
                    val index = row * 3 + col
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        Thumb(p.file, Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).clickable { viewer = index })
                        if (index == 8 && photos.size > 9) {
                            Box(
                                Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Space.Void.copy(alpha = 0.6f)).clickable { viewer = index },
                                contentAlignment = Alignment.Center,
                            ) { Text("+${photos.size - 8}", style = MaterialTheme.typography.titleMediumEmphasized, color = Space.Text) }
                        }
                    }
                }
                repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }

    viewer?.let { start ->
        GalleryViewer(contact, start.coerceIn(0, (photos.size - 1).coerceAtLeast(0)), onDismiss = { viewer = null })
    }
}

@Composable
private fun Thumb(name: String, modifier: Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, name) { value = GalleryStore.thumb(name) }
    Box(modifier.background(Space.PanelHigh)) {
        bitmap?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
private fun GalleryViewer(contact: Contact, start: Int, onDismiss: () -> Unit) {
    val photos = contact.gallery
    if (photos.isEmpty()) {
        onDismiss()
        return
    }
    val pager = rememberPagerState(initialPage = start) { photos.size }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize(), key = { photos.getOrNull(it)?.id ?: it }) { page ->
                photos.getOrNull(page)?.let { ZoomableImage(it.file) }
            }
            val current = photos.getOrNull(pager.currentPage)
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OrbButton(Icons.Rounded.Close, "Schließen", onDismiss, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${pager.currentPage + 1} / ${photos.size}", style = MaterialTheme.typography.titleSmallEmphasized, color = Space.Text)
                    current?.let { Text("hinzugefügt ${Fmt.date(it.addedAt)}", style = MaterialTheme.typography.labelSmall, color = Space.TextDim) }
                }
                OrbButton(
                    Icons.Rounded.AccountCircle, "Als Profilbild",
                    {
                        val p = current ?: return@OrbButton
                        scope.launch {
                            val name = runCatching {
                                OrbitRepository.photoFile(p.file).inputStream().use { Photos.saveFromStream(it) }
                            }.getOrNull() ?: return@launch
                            val old = contact.photo
                            OrbitRepository.editContact(contact.id) { it.copy(photo = name) }
                            if (old != null) {
                                Photos.delete(old)
                                PhotoCache.evict(old)
                            }
                        }
                    },
                    size = 40.dp,
                )
                Spacer(Modifier.width(8.dp))
                OrbButton(Icons.Rounded.Delete, "Löschen", { confirmDelete = true }, size = 40.dp, tint = Space.Coral)
            }
        }
        if (confirmDelete) {
            ConfirmDialog(
                title = "Foto löschen?",
                text = "Das Foto wird aus dem Ordner von ${io.github.thelony97.orbit.domain.Logic.shortName(contact)} entfernt. Das Original in deiner Galerie bleibt.",
                confirm = "Löschen",
                onConfirm = {
                    val p = photos.getOrNull(pager.currentPage) ?: return@ConfirmDialog
                    OrbitRepository.editContact(contact.id) { it.copy(gallery = it.gallery.filterNot { g -> g.id == p.id }) }
                    GalleryStore.delete(p.file)
                    if (photos.size <= 1) onDismiss()
                },
                onDismiss = { confirmDelete = false },
            )
        }
    }
}

@Composable
private fun ZoomableImage(name: String) {
    val bitmap by produceState<ImageBitmap?>(null, name) { value = GalleryStore.full(name) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                })
            }
            .then(
                if (scale > 1f) {
                    Modifier.pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                } else {
                    Modifier.pointerInput(Unit) {
                        // Pinch to start zooming; single-finger swipes still go to the pager.
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.size >= 2) {
                                    val a = event.changes[0]
                                    val b = event.changes[1]
                                    val before = (a.previousPosition - b.previousPosition).getDistance()
                                    val after = (a.position - b.position).getDistance()
                                    if (before > 0f) scale = (scale * after / before).coerceIn(1f, 5f)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b == null) {
            LoadingIndicator(Modifier.size(48.dp), color = Space.Violet)
        } else {
            Image(
                b, null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        }
    }
}
