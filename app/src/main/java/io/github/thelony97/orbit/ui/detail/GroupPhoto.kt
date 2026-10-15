package io.github.thelony97.orbit.ui.detail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.model.FacePin
import io.github.thelony97.orbit.data.GalleryPhoto
import io.github.thelony97.orbit.data.Mutations
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.ui.Photos
import io.github.thelony97.orbit.ui.components.OrbButton
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** The face around a tap as a square (a little over a quarter of the photo's short side). */
internal fun faceCrop(src: Bitmap, x: Float, y: Float): Bitmap {
    val side = (minOf(src.width, src.height) * FACE).roundToInt().coerceAtLeast(1)
    val left = (x * src.width - side / 2f).roundToInt().coerceIn(0, (src.width - side).coerceAtLeast(0))
    val top = (y * src.height - side / 2f).roundToInt().coerceIn(0, (src.height - side).coerceAtLeast(0))
    return Bitmap.createBitmap(src, left, top, side.coerceAtMost(src.width), side.coerceAtMost(src.height))
}

private const val FACE = 0.28f

/** Pins one person on [photo]: the pin, the photo in their folder too, and – without a picture – their face as profile photo. */
private suspend fun pin(ownerId: String, photo: GalleryPhoto, contactId: String, x: Float, y: Float) {
    val d = OrbitRepository.snapshot()
    val target = d.contacts.firstOrNull { it.id == contactId } ?: return
    val face = if (target.photo == null) {
        withContext(Dispatchers.IO) { BitmapFactory.decodeFile(OrbitRepository.photoFile(photo.file).path) }?.let { Photos.storeBitmap(faceCrop(it, x, y)) }
    } else {
        null
    }
    OrbitRepository.update { doc ->
        var next = doc.copy(contacts = doc.contacts.map { c ->
            when (c.id) {
                ownerId -> c.copy(gallery = c.gallery.map { g -> if (g.id == photo.id) g.copy(pins = g.pins.filterNot { it.contactId == contactId } + FacePin(contactId, x, y)) else g })
                contactId -> if (face != null && c.photo == null) c.copy(photo = face) else c
                else -> c
            }
        })
        if (contactId != ownerId && next.contacts.none { it.id == contactId && it.gallery.any { g -> g.file == photo.file } }) {
            next = Mutations.shareAttachment(next, photo, contactId)
        }
        next
    }
}

/**
 * Gruppenfoto mit Namens-Pins (PLAN 9 E10, no face recognition): tap a face, pick who it is – the
 * name sits there, the photo lands in their folder, and someone without a picture gets this face.
 */
@Composable
internal fun GroupPhotoPins(ownerId: String, photo: GalleryPhoto, onClose: () -> Unit) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val data by OrbitRepository.data.collectAsState()
    val current = data?.contacts?.firstOrNull { it.id == ownerId }?.gallery?.firstOrNull { it.id == photo.id } ?: photo
    val bmp by produceState<Bitmap?>(null, photo.file) { value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(OrbitRepository.photoFile(photo.file).path) } }
    var picking by remember { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Space.Void).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OrbButton(Icons.Rounded.Close, "Fertig", onClose, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Text("Gesichter antippen", style = MaterialTheme.typography.titleSmallEmphasized, color = Space.TextDim)
            }
            val image = bmp ?: return@Column
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val boxW = with(density) { maxWidth.toPx() }
                val boxH = with(density) { maxHeight.toPx() }
                val scale = minOf(boxW / image.width, boxH / image.height)
                val w = image.width * scale
                val h = image.height * scale
                val dx = (boxW - w) / 2
                val dy = (boxH - h) / 2
                Image(
                    image.asImageBitmap(), null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().pointerInput(scale) {
                        detectTapGestures { at ->
                            val x = (at.x - dx) / w
                            val y = (at.y - dy) / h
                            if (x in 0f..1f && y in 0f..1f) {
                                haptics.perform(Haptic.Tick)
                                picking = Offset(x, y)
                            }
                        }
                    },
                )
                current.pins.forEach { p ->
                    val who = data?.contacts?.firstOrNull { it.id == p.contactId } ?: return@forEach
                    Box(
                        Modifier.align(Alignment.TopStart).offset { IntOffset((dx + p.x * w).roundToInt() - PIN_HALF, (dy + p.y * h).roundToInt()) },
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Space.Violet))
                            Text(
                                Logic.shortName(who), style = MaterialTheme.typography.labelMedium, color = Space.Text,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Space.PanelHighest.copy(alpha = 0.85f)).padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
        picking?.let { at ->
            ContactPickDialog(title = "Wer ist das?", exclude = current.pins.map { it.contactId }.toSet(), onDismiss = { picking = null }) { id ->
                picking = null
                haptics.perform(Haptic.Confirm)
                scope.launch { pin(ownerId, photo, id, at.x, at.y) }
            }
        }
    }
}

private const val PIN_HALF = 40
