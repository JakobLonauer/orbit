package io.github.thelony97.orbit.ui.detail

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfDocument
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.ui.components.GhostButton
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot

/** A4 at 72 dpi (PDF points). */
private const val PAGE_W = 595
private const val PAGE_H = 842
private const val INSET = 0.08f

/** Straightens the quadrilateral [corners] (image pixels: top-left, top-right, bottom-right, bottom-left) onto a page. */
internal fun straighten(src: Bitmap, corners: List<Offset>): Bitmap {
    val w = ((hypot(corners[1].x - corners[0].x, corners[1].y - corners[0].y) + hypot(corners[2].x - corners[3].x, corners[2].y - corners[3].y)) / 2).toInt().coerceAtLeast(1)
    val h = ((hypot(corners[3].x - corners[0].x, corners[3].y - corners[0].y) + hypot(corners[2].x - corners[1].x, corners[2].y - corners[1].y)) / 2).toInt().coerceAtLeast(1)
    val m = Matrix()
    val from = floatArrayOf(corners[0].x, corners[0].y, corners[1].x, corners[1].y, corners[2].x, corners[2].y, corners[3].x, corners[3].y)
    val to = floatArrayOf(0f, 0f, w.toFloat(), 0f, w.toFloat(), h.toFloat(), 0f, h.toFloat())
    m.setPolyToPoly(from, 0, to, 0, 4)
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(out).apply {
        drawColor(android.graphics.Color.WHITE)
        drawBitmap(src, m, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
    }
    return out
}

/** One straightened page as a PDF in the contact's folder; returns the attachment. */
private suspend fun savePdf(contactId: String, page: Bitmap): io.github.thelony97.orbit.data.GalleryPhoto = withContext(Dispatchers.IO) {
    val file = "d-${newId()}.pdf"
    val doc = PdfDocument()
    val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
    val p = doc.startPage(info)
    val scale = minOf(PAGE_W.toFloat() / page.width, PAGE_H.toFloat() / page.height)
    val m = Matrix().apply {
        postScale(scale, scale)
        postTranslate((PAGE_W - page.width * scale) / 2, (PAGE_H - page.height * scale) / 2)
    }
    p.canvas.drawBitmap(page, m, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
    doc.finishPage(p)
    OrbitRepository.photoFile(file).outputStream().use { doc.writeTo(it) }
    doc.close()
    val day = java.time.LocalDate.now()
    io.github.thelony97.orbit.data.GalleryPhoto(
        id = newId(), contactId = contactId, file = file, mime = "application/pdf", addedAt = System.currentTimeMillis(),
        name = "Scan ${day.dayOfMonth}.${day.monthValue}.${day.year}.pdf",
    )
}

/**
 * Dokument-Scan (PLAN 9 E11, no text recognition): the camera, then four corners to pull onto the
 * paper's edges; Speichern straightens it into a PDF in the contact's folder.
 */
@Composable
internal fun DocScan(contactId: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var shot by remember { mutableStateOf<Bitmap?>(null) }
    val target = remember {
        val dir = java.io.File(ctx.cacheDir, "camera").apply { mkdirs() }
        androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".share", java.io.File(dir, "scan.jpg"))
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (!ok) onDone() else scope.launch {
            shot = withContext(Dispatchers.IO) {
                runCatching {
                    android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(ctx.contentResolver, target)) { d, info, _ ->
                        d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                        val longest = maxOf(info.size.width, info.size.height)
                        if (longest > 2400) d.setTargetSampleSize(longest / 2400 + 1)
                    }
                }.getOrNull()
            }
            if (shot == null) onDone()
        }
    }
    LaunchedEffect(Unit) {
        io.github.thelony97.orbit.security.AppLock.allowExternal()
        camera.launch(target)
    }
    shot?.let { bmp ->
        CornerEditor(bmp, onCancel = onDone, onSave = { corners ->
            scope.launch {
                val page = withContext(Dispatchers.Default) { straighten(bmp, corners) }
                val att = savePdf(contactId, page)
                OrbitRepository.update { d -> d.copy(contacts = d.contacts.map { if (it.id == contactId) it.copy(gallery = listOf(att) + it.gallery) else it }) }
                onDone()
            }
        })
    }
}

@Composable
private fun CornerEditor(bmp: Bitmap, onCancel: () -> Unit, onSave: (List<Offset>) -> Unit) {
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    // Corners in image pixels, a little inside the edges to start.
    val corners = remember {
        mutableStateListOf(
            Offset(bmp.width * INSET, bmp.height * INSET), Offset(bmp.width * (1 - INSET), bmp.height * INSET),
            Offset(bmp.width * (1 - INSET), bmp.height * (1 - INSET)), Offset(bmp.width * INSET, bmp.height * (1 - INSET)),
        )
    }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Space.Void).statusBarsPadding().navigationBarsPadding()) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                val boxW = with(density) { maxWidth.toPx() }
                val boxH = with(density) { maxHeight.toPx() }
                val scale = minOf(boxW / bmp.width, boxH / bmp.height)
                val dx = (boxW - bmp.width * scale) / 2
                val dy = (boxH - bmp.height * scale) / 2
                fun toScreen(p: Offset) = Offset(dx + p.x * scale, dy + p.y * scale)
                Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                var active by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
                Canvas(
                    Modifier.fillMaxSize().pointerInput(scale) {
                        detectDragGestures(
                            onDragStart = { at ->
                                active = corners.indices.minByOrNull { (toScreen(corners[it]) - at).getDistance() } ?: -1
                                haptics.perform(Haptic.Tick)
                            },
                            onDragEnd = { active = -1 },
                        ) { change, drag ->
                            change.consume()
                            val i = active
                            if (i >= 0) {
                                val p = corners[i] + drag / scale
                                corners[i] = Offset(p.x.coerceIn(0f, bmp.width.toFloat()), p.y.coerceIn(0f, bmp.height.toFloat()))
                            }
                        }
                    },
                ) {
                    val pts = corners.map { toScreen(it) }
                    val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                    drawPath(path, Space.Teal, style = Stroke(2.dp.toPx()))
                    pts.forEachIndexed { i, p -> drawCircle(if (i == active) Space.Orange else Space.Teal, (if (i == active) 16 else 12).dp.toPx(), p, alpha = 0.85f) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                GhostButton("Abbrechen", onCancel)
                Spacer(Modifier.weight(1f))
                GlowButton("Speichern", { haptics.perform(Haptic.Confirm); onSave(corners.toList()) }, color = Space.Teal)
            }
        }
    }
}
