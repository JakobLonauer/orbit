package io.github.thelony97.orbit.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.thelony97.orbit.core.designsystem.Corners
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Motion
import io.github.thelony97.orbit.core.designsystem.Spacing
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** Under the anchor, centred and kept inside the window; above it when there is no room below. */
internal class BesideAnchor(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom + gap
        val y = if (below + popupContentSize.height <= windowSize.height) below
        else (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

/**
 * Geist (PLAN 9.2, A0): a suggestion after a fixed rule, drawn where it would land – a dashed rim
 * that slowly walks, the content half see-through. A tap takes it: the rim closes, the content
 * fills (250 ms, Bestätigen) and then [onAccept] runs. Holding shows where it comes from
 * ([source]) and, with [onDismiss], „Verwerfen“; outside scrolling containers a swipe across
 * [dismissAxis] sends it away, too (null: no swipe – inside a list it would fight the scrolling).
 */
@Composable
fun Ghost(
    source: String,
    onAccept: () -> Unit,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
    accent: Color = Space.Violet,
    corner: Dp = 20.dp,
    dismissAxis: Orientation? = Orientation.Horizontal,
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val fill = remember { Animatable(0f) }
    val swipe = remember { Animatable(0f) }
    val leave = remember { Animatable(0f) }
    var showSource by remember { mutableStateOf(false) }
    // The rim walks one slow lap; frozen in battery saver (PLAN 1.1: no permanent animations).
    val saver = remember { ctx.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true }
    val march = if (saver) null else rememberInfiniteTransition(label = "ghost").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4_000, easing = LinearEasing)), label = "march",
    )

    fun dismissNow() {
        val done = onDismiss ?: return
        showSource = false
        scope.launch {
            haptics.perform(Haptic.Reject)
            leave.animateTo(1f, tween(Motion.FAST_MS, easing = Motion.Exit))
            done()
            // Normally the card is gone by now (this coroutine with it). Still composed – the same
            // card came back with new content – it shows again instead of staying invisible.
            delay(Motion.MAX_MS * 2L)
            leave.animateTo(0f, tween(Motion.MEDIUM_MS))
        }
    }

    val drag = if (dismissAxis != Orientation.Horizontal || onDismiss == null) Modifier else Modifier.pointerInput(onDismiss) {
        val limit = size.width.toFloat()
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
        detectHorizontalDragGestures(onDragEnd = end, onDragCancel = end) { change, delta ->
            change.consume()
            scope.launch { swipe.snapTo(swipe.value + delta) }
        }
    }
    Box(
        modifier
            .graphicsLayer {
                translationX = swipe.value
                val gone = leave.value
                scaleX = 1f - 0.08f * gone
                scaleY = 1f - 0.08f * gone
                alpha = (1f - (abs(swipe.value) / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.9f)) * (1f - gone)
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
                            // Still here (a permission was refused, say): back to a ghost, tappable again.
                            delay(600)
                            fill.animateTo(0f, tween(Motion.MEDIUM_MS))
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
        if (showSource && (source.isNotBlank() || onDismiss != null)) {
            LaunchedEffect(Unit) {
                delay(if (onDismiss != null) 4_000 else 2_600)
                showSource = false
            }
            val gap = with(LocalDensity.current) { Spacing.S.roundToPx() }
            Popup(
                popupPositionProvider = remember(gap) { BesideAnchor(gap) },
                onDismissRequest = { showSource = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .widthIn(max = 280.dp)
                        .glass(Corners.Row, tint = Space.PanelHighest, border = accent.copy(alpha = 0.5f))
                        .padding(horizontal = Spacing.M, vertical = Spacing.S),
                ) {
                    if (source.isNotBlank()) Text(source, style = MaterialTheme.typography.labelMedium, color = Space.TextDim)
                    if (onDismiss != null) {
                        if (source.isNotBlank()) Spacer(Modifier.height(Spacing.XS))
                        Text(
                            "Verwerfen",
                            style = MaterialTheme.typography.labelLarge,
                            color = Space.Text,
                            modifier = Modifier
                                .clickable { dismissNow() }
                                .padding(vertical = Spacing.XS),
                        )
                    }
                }
            }
        }
    }
}
