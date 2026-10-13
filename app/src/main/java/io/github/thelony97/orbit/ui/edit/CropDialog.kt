package io.github.thelony97.orbit.ui.edit

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.domain.CropMath
import io.github.thelony97.orbit.ui.components.GhostButton
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.theme.Space

/**
 * Profilbild zuschneiden (PLAN 3.3): pinch to zoom, drag to move, the circle shows what the
 * avatar will be. The square behind the circle is what gets stored.
 */
@Composable
fun CropDialog(bitmap: Bitmap, onDismiss: () -> Unit, onCrop: (CropMath.Square) -> Unit) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val haptics = rememberHaptics()
    var frame by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Space.Void).navigationBarsPadding()) {
            androidx.compose.foundation.layout.BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .pointerInput(bitmap) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            val f = frame.toFloat()
                            if (f <= 0f) return@detectTransformGestures
                            zoom = (zoom * gestureZoom).coerceIn(1f, 6f)
                            val (x, y) = CropMath.clampPan(bitmap.width, bitmap.height, f, zoom, panX + pan.x, panY + pan.y)
                            panX = x
                            panY = y
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                // The frame: the shorter side minus a margin, always square (also in landscape).
                val side = (minOf(maxWidth, maxHeight) - 56.dp).coerceAtLeast(120.dp)
                Box(
                    Modifier.requiredSize(side).onSizeChanged { frame = it.width },
                    contentAlignment = Alignment.Center,
                ) {
                    if (frame > 0) {
                        val s = CropMath.baseScale(bitmap.width, bitmap.height, frame.toFloat())
                        val w = with(density) { (bitmap.width * s).toDp() }
                        val h = with(density) { (bitmap.height * s).toDp() }
                        Image(
                            image, null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .requiredSize(w, h)
                                .graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    translationX = panX
                                    translationY = panY
                                },
                        )
                    }
                }
                // Dim everything outside the circle; a thin ring marks the edge.
                Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                    val r = frame / 2f
                    drawRect(Space.Void.copy(alpha = 0.72f))
                    drawCircle(Color.Black, radius = r, center = center, blendMode = BlendMode.Clear)
                    drawCircle(Color.White.copy(alpha = 0.7f), radius = r, center = center, style = Stroke(1.dp.toPx()))
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                GhostButton("Abbrechen", onDismiss, icon = Icons.Rounded.Close)
                GlowButton("Übernehmen", {
                    haptics.perform(Haptic.Confirm)
                    onCrop(CropMath.square(bitmap.width, bitmap.height, frame.toFloat(), zoom, panX, panY))
                }, icon = Icons.Rounded.Check)
            }
        }
    }
}
