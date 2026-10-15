package io.github.thelony97.orbit.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Motion
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * Geist (PLAN 9.2, A0): a suggestion after a fixed rule, drawn where it would land – a dashed rim
 * that slowly walks, the content half see-through. A tap takes it: the rim closes, the content
 * fills (250 ms, Bestätigen) and then [onAccept] runs. Holding shows where it comes from
 * ([source]); a swipe across [dismissAxis] sends it away ([onDismiss]).
 */
@Composable
fun Ghost(
    source: String,
    onAccept: () -> Unit,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
    accent: Color = Space.Violet,
    corner: androidx.compose.ui.unit.Dp = 20.dp,
    dismissAxis: Orientation = Orientation.Horizontal,
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val fill = remember { Animatable(0f) }
    val swipe = remember { Animatable(0f) }
    var showSource by remember { mutableStateOf(false) }
    // The rim walks one slow lap; frozen in battery saver (PLAN 1.1: no permanent animations).
    val saver = remember { ctx.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true }
    val march = if (saver) null else rememberInfiniteTransition(label = "ghost").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4_000, easing = LinearEasing)), label = "march",
    )
    val drag = Modifier.pointerInput(onDismiss, dismissAxis) {
        if (onDismiss == null) return@pointerInput
        val limit = if (dismissAxis == Orientation.Horizontal) size.width.toFloat() else size.height.toFloat()
        val end: () -> Unit = {
            scope.launch {
                if (abs(swipe.value) > limit * 0.35f) {
                    haptics.perform(Haptic.Reject)
                    swipe.animateTo(sign(swipe.value) * limit * 1.2f, tween(Motion.FAST_MS, easing = Motion.Exit))
                    onDismiss()
                } else {
                    swipe.animateTo(0f, Motion.snappy())
                }
            }
        }
        if (dismissAxis == Orientation.Horizontal) {
            detectHorizontalDragGestures(onDragEnd = end, onDragCancel = end) { change, delta ->
                change.consume()
                scope.launch { swipe.snapTo(swipe.value + delta) }
            }
        } else {
            // In a horizontal row only an upward swipe sends a card away.
            detectVerticalDragGestures(onDragEnd = end, onDragCancel = end) { change, delta ->
                change.consume()
                scope.launch { swipe.snapTo((swipe.value + delta).coerceAtMost(0f)) }
            }
        }
    }
    Box(
        modifier
            .graphicsLayer {
                val limit = if (dismissAxis == Orientation.Horizontal) size.width else size.height
                if (dismissAxis == Orientation.Horizontal) translationX = swipe.value else translationY = swipe.value
                alpha = 1f - (abs(swipe.value) / limit.coerceAtLeast(1f)).coerceIn(0f, 0.9f)
            }
            .then(drag)
            .drawWithContent {
                val f = fill.value
                drawRoundRect(accent.copy(alpha = 0.05f + 0.12f * f), cornerRadius = CornerRadius(corner.toPx()))
                drawContent()
                val stroke = 1.5.dp.toPx()
                val dash = 7.dp.toPx()
                val gap = 5.dp.toPx() * (1f - f)
                val phase = -(march?.value ?: 0f) * (dash + 5.dp.toPx()) * 6f
                drawRoundRect(
                    color = accent.copy(alpha = 0.5f + 0.5f * f),
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(corner.toPx()),
                    style = Stroke(stroke, pathEffect = if (gap > 0.2f) PathEffect.dashPathEffect(floatArrayOf(dash, gap), phase) else null),
                )
            }
            .combinedClickable(
                onClick = {
                    if (fill.value == 0f && !fill.isRunning) {
                        scope.launch {
                            haptics.perform(Haptic.Confirm)
                            fill.animateTo(1f, tween(Motion.MEDIUM_MS, easing = Motion.Emphasized))
                            onAccept()
                        }
                    }
                },
                onLongClick = {
                    haptics.perform(Haptic.Tick)
                    showSource = true
                },
            ),
    ) {
        Box(Modifier.graphicsLayer { alpha = 0.66f + 0.34f * fill.value }) { content() }
        if (showSource && source.isNotBlank()) {
            LaunchedEffect(Unit) {
                delay(2_600)
                showSource = false
            }
            Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, -8), onDismissRequest = { showSource = false }) {
                Text(
                    source,
                    style = MaterialTheme.typography.labelMedium,
                    color = Space.Text,
                    modifier = Modifier
                        .glass(androidx.compose.foundation.shape.RoundedCornerShape(50), tint = Space.PanelHighest, border = accent.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
