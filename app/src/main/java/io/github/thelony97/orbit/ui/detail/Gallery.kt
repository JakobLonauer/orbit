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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.rounded.PersonAddAlt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
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
import io.github.thelony97.orbit.ui.components.placeholder
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
        // „Originalgröße behalten“ (PLAN 3.10), otherwise 2048 px on the long edge.
        val keep = OrbitRepository.data.value?.settings?.keepOriginalPhotos == true
        uris.mapNotNull { uri ->
            runCatching {
                val source = ImageDecoder.createSource(ctx.contentResolver, uri)
                val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val longest = maxOf(info.size.width, info.size.height)
                    if (!keep && longest > MAX_EDGE) {
                        val f = MAX_EDGE.toFloat() / longest
                        decoder.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
                    }
                }
                val name = "g-${newId()}.jpg"
                OrbitRepository.photoFile(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it) }
                // contactId is filled in by the store when the contact is saved.
                GalleryPhoto(id = newId(), contactId = "", file = name, addedAt = now(), takenAt = takenAt(ctx, uri))
            }.getOrNull()
        }
    }

    /** PLAN 3.10: the date the picture was taken (EXIF), for the Fotos page; null if unknown. */
    private fun takenAt(ctx: Context, uri: Uri): Long? = runCatching {
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            val exif = android.media.ExifInterface(input)
            val raw = exif.getAttribute(android.media.ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(android.media.ExifInterface.TAG_DATETIME)
            raw?.let {
                java.time.LocalDateTime.parse(it.trim(), java.time.format.DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
                    .atZone(io.github.thelony97.orbit.domain.zone()).toInstant().toEpochMilli()
            }
        }
    }.getOrNull()

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

    /** The already decoded small version, if any – the viewer shows it until the sharp one is ready. */
    fun cachedThumb(name: String): ImageBitmap? = thumbs.get(name)

    suspend fun full(name: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val file = OrbitRepository.photoFile(name)
        if (!file.exists()) null else decode(file, 1600)?.asImageBitmap()
    }
}

/** Photo import for one contact; remembered at screen level so "+ Foto" works with no section. */
@androidx.compose.runtime.Stable
class GalleryImport(private val running: androidx.compose.runtime.State<Int>, private val start: () -> Unit) {
    val importing: Boolean get() = running.value > 0
    /** Pictures still being copied (placeholders in their final place). */
    val pending: Int get() = running.value

    fun launch() = start()
}

@Composable
fun rememberGalleryImport(contactId: String): GalleryImport {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val running = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isNotEmpty()) {
            running.intValue = uris.size
            scope.launch {
                // Finish even if the user leaves the screen meanwhile, otherwise the copied files would be orphaned.
                withContext(NonCancellable) {
                    val added = GalleryStore.import(ctx.applicationContext, uris)
                    OrbitRepository.editContact(contactId) { it.copy(gallery = added.asReversed().map { a -> a.copy(contactId = contactId) } + it.gallery) }
                }
                running.intValue = 0
            }
        }
    }
    return remember(contactId, picker) {
        GalleryImport(running) {
            AppLock.allowExternal()
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
}

/** The photo folder; only composed when there are photos or an import is running (W5). */
@Composable
fun GallerySection(contact: Contact, import: GalleryImport, onAll: () -> Unit = {}) {
    var viewer by remember { mutableStateOf<Int?>(null) }
    val importing = import.importing
    val photos = contact.gallery
    val shown = io.github.thelony97.orbit.core.domain.PhotoMonths.newest(contact, 6)

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
                    .clickable { import.launch() }
                    .padding(5.dp),
            )
        },
    ) {
        if (importing) {
            // New pictures appear as tiles in their final place while they are copied (PLAN 1.2).
            val breath = io.github.thelony97.orbit.ui.components.rememberBreath()
            List(import.pending.coerceAtMost(9)) { it }.chunked(3).forEach { chunk ->
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    chunk.forEach { _ ->
                        Box(Modifier.weight(1f).aspectRatio(1f).placeholder(breath, RoundedCornerShape(12.dp)))
                    }
                    repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        // Die neuesten 6 + „Alle“ (PLAN 3.10).
        shown.chunked(3).forEachIndexed { row, chunk ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                chunk.forEachIndexed { col, p ->
                    val index = row * 3 + col
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        Thumb(p.file, Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).clickable { viewer = index })
                        if (index == 5 && photos.size > 6) {
                            Box(
                                Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Space.Void.copy(alpha = 0.6f)).clickable(onClick = onAll),
                                contentAlignment = Alignment.Center,
                            ) { Text("Alle", style = MaterialTheme.typography.titleMediumEmphasized, color = Space.Text) }
                        }
                    }
                }
                repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }

    viewer?.let { start ->
        PhotoViewer(shown.map { io.github.thelony97.orbit.core.domain.PhotoMonths.Item(contact.id, it) }, start, onDismiss = { viewer = null })
    }
}

/** A square thumbnail from the photo folder (also on the Fotos page). */
@Composable
fun PhotoThumb(name: String, modifier: Modifier) = Thumb(name, modifier)

@Composable
private fun Thumb(name: String, modifier: Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, name) { value = GalleryStore.thumb(name) }
    Box(modifier.background(Space.PanelHigh)) {
        bitmap?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/**
 * Foto im Vollbild (PLAN 3.10): swipe through [items], zoom; Profilbild, Teilen, ins Handy
 * speichern, „Auch bei …“ (the same file in another contact's folder) and Entfernen with Undo –
 * the file goes only when no contact uses it any more.
 */
@Composable
fun PhotoViewer(items: List<io.github.thelony97.orbit.core.domain.PhotoMonths.Item>, start: Int, onDismiss: () -> Unit) {
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val ctx = LocalContext.current
    val pager = rememberPagerState(initialPage = start.coerceIn(0, items.lastIndex)) { items.size }
    var shareTo by remember { mutableStateOf<io.github.thelony97.orbit.core.domain.PhotoMonths.Item?>(null) }
    val scope = rememberCoroutineScope()
    val data by OrbitRepository.data.collectAsState()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize(), key = { items.getOrNull(it)?.photo?.id ?: it }) { page ->
                items.getOrNull(page)?.let { ZoomableImage(it.photo.file) }
            }
            val current = items.getOrNull(pager.currentPage)
            val owner = current?.let { cur -> data?.contacts?.firstOrNull { it.id == cur.contactId } }
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OrbButton(Icons.Rounded.Close, "Schließen", onDismiss, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${pager.currentPage + 1} / ${items.size}", style = MaterialTheme.typography.titleSmallEmphasized, color = Space.Text)
                        current?.let { cur ->
                            val date = io.github.thelony97.orbit.core.domain.PhotoMonths.dateOf(cur.photo)
                            Text(
                                listOfNotNull(owner?.let { io.github.thelony97.orbit.domain.Logic.shortName(it) }, Fmt.date(date)).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall, color = Space.TextDim,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (owner != null && !owner.isOrg) {
                        OrbButton(Icons.Rounded.AccountCircle, "Als Profilbild", {
                            val p = current.photo
                            scope.launch {
                                val name = runCatching { OrbitRepository.photoFile(p.file).inputStream().use { Photos.saveFromStream(it) } }.getOrNull() ?: return@launch
                                val old = owner.photo
                                OrbitRepository.editContact(owner.id) { it.copy(photo = name) }
                                if (old != null) {
                                    Photos.delete(old)
                                    PhotoCache.evict(old)
                                }
                            }
                        }, size = 40.dp)
                    }
                    OrbButton(Icons.Rounded.Share, "Teilen", { current?.let { scope.launch { io.github.thelony97.orbit.actions.PhotoActions.share(ctx, it.photo.file) } } }, size = 40.dp)
                    OrbButton(Icons.Rounded.Download, "Ins Handy speichern", {
                        current?.let { cur ->
                            scope.launch {
                                val ok = io.github.thelony97.orbit.actions.PhotoActions.saveToPhone(ctx, cur.photo.file)
                                android.widget.Toast.makeText(ctx, if (ok) "In „Bilder/Orbit“ gespeichert" else "Speichern nicht möglich", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }, size = 40.dp)
                    OrbButton(Icons.Rounded.PersonAddAlt, "Auch bei …", { shareTo = current }, size = 40.dp)
                    Spacer(Modifier.weight(1f))
                    OrbButton(Icons.Rounded.Delete, "Entfernen", {
                        val cur = current ?: return@OrbButton
                        val p = cur.photo
                        // Rückgängig statt Nachfragen (PLAN 1.2).
                        OrbitRepository.editContact(cur.contactId) { it.copy(gallery = it.gallery.filterNot { g -> g.id == p.id }) }
                        io.github.thelony97.orbit.ui.components.Undo.offer("Foto entfernt") {
                            OrbitRepository.editContact(cur.contactId) { c -> if (c.gallery.any { it.id == p.id }) c else c.copy(gallery = listOf(p) + c.gallery) }
                        }
                        // The file only after the undo time, and only if no contact uses it.
                        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycleScope.launch {
                            kotlinx.coroutines.delay(15_000)
                            val d = OrbitRepository.snapshot()
                            if (io.github.thelony97.orbit.data.Mutations.unreferenced(d, listOf(p.file)).isNotEmpty()) GalleryStore.delete(p.file)
                        }
                        if (items.size <= 1) onDismiss()
                    }, size = 40.dp, tint = Space.Coral)
                }
            }
        }
        shareTo?.let { item ->
            ContactPickDialog(
                title = "Auch bei …",
                exclude = data?.contacts.orEmpty().filter { c -> c.gallery.any { it.file == item.photo.file } }.map { it.id }.toSet(),
                onDismiss = { shareTo = null },
            ) { toId ->
                OrbitRepository.edit { d -> io.github.thelony97.orbit.data.Mutations.shareAttachment(d, item.photo, toId) }
                android.widget.Toast.makeText(ctx, "Auch im Ordner von " + (data?.contacts?.firstOrNull { it.id == toId }?.let { io.github.thelony97.orbit.domain.Logic.shortName(it) } ?: ""), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** A person or organisation to pick (not archived, not in the trash). */
@Composable
fun ContactPickDialog(title: String, exclude: Set<String>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val data by OrbitRepository.data.collectAsState()
    val search = androidx.compose.foundation.text.input.rememberTextFieldState()
    val q = search.text.toString().trim().lowercase()
    val lastFirst = data?.settings?.sortByLastName ?: false
    val people = data?.contacts.orEmpty()
        .filter { it.deletedAt == null && !it.archived && it.id !in exclude && (q.isEmpty() || io.github.thelony97.orbit.domain.Logic.displayName(it, lastFirst).lowercase().contains(q)) }
        .sortedBy { io.github.thelony97.orbit.domain.Logic.sortKey(it, lastFirst) }
        .take(40)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Space.PanelHigh,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                io.github.thelony97.orbit.ui.components.SpaceSearchField(search, "Kontakt", Modifier.fillMaxWidth())
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(people.size, key = { people[it].id }) { i ->
                        val c = people[i]
                        Text(
                            io.github.thelony97.orbit.domain.Logic.displayName(c, lastFirst),
                            color = Space.Text,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(c.id); onDismiss() }.padding(vertical = 10.dp, horizontal = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun ZoomableImage(name: String) {
    val bitmap by produceState(GalleryStore.cachedThumb(name), name) { GalleryStore.full(name)?.let { value = it } }
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
        // Until the sharp picture is decoded, the small one stands in at the same size.
        val b = bitmap
        if (b != null) {
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
