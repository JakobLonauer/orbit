package io.github.thelony97.orbit.ui.settings

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.designsystem.Motion
import io.github.thelony97.orbit.core.domain.StorageUsage
import io.github.thelony97.orbit.core.domain.StorageUsage.Part
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Reads the sizes of Orbit's own files (IO, never in the composition). */
internal fun storageFiles(ctx: Context): List<StorageUsage.FileInfo> {
    val own = ctx.filesDir.listFiles().orEmpty().filter { it.isFile }
        .map { StorageUsage.FileInfo(it.name, it.length()) }
    val photos = File(ctx.filesDir, "photos").listFiles().orEmpty().filter { it.isFile }
        .map { StorageUsage.FileInfo(it.name, it.length(), inPhotos = true) }
    val db = ctx.getDatabasePath("orbit.db").parentFile?.listFiles().orEmpty()
        .filter { it.isFile && it.name.startsWith("orbit.db") }
        .map { StorageUsage.FileInfo(it.name, it.length()) }
    return own + photos + db
}

internal fun partColor(p: Part): Color = when (p) {
    Part.PHOTOS -> Space.Violet
    Part.DOCUMENTS -> Space.Blue
    Part.HISTORY -> Space.Teal
    Part.CONTACTS -> Space.Orange
    Part.SAFETY -> Space.TextFaint
}

/**
 * Speicher-Übersicht (PLAN 3.13 Sicherheit & Backup, 3.10 Speicheranzeige): one bar that grows
 * in from the left, split by part, and a line per part with size and count. [preset] skips the
 * disk read (screenshot tests).
 */
@Composable
fun StorageCard(preset: List<StorageUsage.Slice>? = null) {
    val ctx = LocalContext.current
    val slices by produceState(preset, preset) {
        if (preset == null) value = withContext(Dispatchers.IO) { StorageUsage.slices(storageFiles(ctx)) }
    }
    val total = slices?.sumOf { it.bytes } ?: 0L
    // W5: nothing measured, nothing to show.
    if (slices != null && total == 0L) return
    SettingsGroup("Speicher", status = slices?.let { StorageUsage.format(total) }) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            val grow = remember { Animatable(0f) }
            LaunchedEffect(slices != null) {
                if (slices != null) grow.animateTo(1f, androidx.compose.animation.core.tween(Motion.EMPHASIZED_MS, easing = Motion.Emphasized))
            }
            val list = slices.orEmpty()
            // The bar keeps its final height while the sizes load (placeholder in end form, W6).
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Space.PanelHighest)
                    .drawBehind {
                        if (total <= 0L) return@drawBehind
                        val gap = 2.dp.toPx()
                        val width = size.width * grow.value
                        var x = 0f
                        list.forEach { s ->
                            val w = (s.bytes.toFloat() / total * width).coerceAtLeast(if (s.bytes > 0) 3.dp.toPx() else 0f)
                            val drawn = (w - gap).coerceAtLeast(1f)
                            if (x < width) {
                                drawRoundRect(
                                    partColor(s.part),
                                    topLeft = Offset(x, 0f),
                                    size = Size(drawn.coerceAtMost(width - x), size.height),
                                    cornerRadius = CornerRadius(size.height / 2),
                                )
                            }
                            x += w
                        }
                    },
            )
            if (list.isNotEmpty()) Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                list.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(partColor(s.part)))
                        Spacer(Modifier.width(10.dp))
                        Text(s.part.label, style = MaterialTheme.typography.bodyMedium, color = Space.Text, modifier = Modifier.weight(1f))
                        val count = when (s.part) {
                            Part.PHOTOS -> if (s.files == 1) "1 Bild" else "${s.files} Bilder"
                            Part.DOCUMENTS -> if (s.files == 1) "1 PDF" else "${s.files} PDFs"
                            Part.SAFETY -> if (s.files == 1) "1 Kopie" else "${s.files} Kopien"
                            else -> null
                        }
                        if (count != null) {
                            Text(count, style = MaterialTheme.typography.labelMedium, color = Space.TextFaint)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            StorageUsage.format(s.bytes),
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            color = Space.TextDim,
                        )
                    }
                }
            }
        }
    }
}
