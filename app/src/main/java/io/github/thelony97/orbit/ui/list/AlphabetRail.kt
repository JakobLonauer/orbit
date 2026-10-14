package io.github.thelony97.orbit.ui.list

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.ui.components.glow
import io.github.thelony97.orbit.ui.theme.Space
import kotlin.math.roundToInt

/**
 * A–Z-Leiste (PLAN 1.3, Kontakte): drag or tap along the letters; a magnifier bubble follows
 * the finger, every new letter ticks and jumps the list. [onLetter] scrolls to that section.
 */
@Composable
fun AlphabetRail(
    letters: List<String>,
    onLetter: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** What the rail shows for a key (the Fotos page: „O“ for 2026-10). */
    railLabel: (String) -> String = { it },
    /** What the bubble says („Oktober 2026“); a longer text makes the bubble a pill. */
    bubbleLabel: (String) -> String = { it },
) {
    if (letters.size < 2) return
    val haptics = rememberHaptics()
    var height by remember { mutableFloatStateOf(1f) }
    var active by remember { mutableStateOf<String?>(null) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    val bubble by animateFloatAsState(if (active != null) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 900f), label = "bubble")
    val density = LocalDensity.current

    fun pick(y: Float) {
        fingerY = y.coerceIn(0f, height)
        val i = ((fingerY / height) * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val l = letters[i]
        if (l != active) {
            active = l
            haptics.perform(Haptic.Tick)
            onLetter(l)
        }
    }

    Box(modifier.fillMaxHeight().width(64.dp)) {
        // Magnifier bubble left of the rail, following the finger.
        if (bubble > 0.01f) {
            val half = with(density) { 28.dp.toPx() }
            val label = active?.let(bubbleLabel).orEmpty()
            val pill = label.length > 2
            Box(
                Modifier
                    .align(if (pill) Alignment.TopEnd else Alignment.TopStart)
                    .offset { IntOffset(if (pill) -with(density) { 30.dp.roundToPx() } else 0, (fingerY - half).roundToInt()) }
                    .graphicsLayer { scaleX = bubble; scaleY = bubble; alpha = bubble }
                    .then(if (pill) Modifier.wrapContentWidth(unbounded = true).height(56.dp) else Modifier.size(56.dp))
                    .glow(Space.Violet, 18.dp, if (pill) RoundedCornerShape(28.dp) else CircleShape, 0.5f)
                    .background(Space.PanelHigh, if (pill) RoundedCornerShape(28.dp) else CircleShape)
                    .then(if (pill) Modifier.padding(horizontal = 18.dp) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = if (pill) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.headlineSmallEmphasized, color = Space.Text, maxLines = 1)
            }
        }
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
                .width(20.dp)
                .onSizeChanged { height = it.height.toFloat().coerceAtLeast(1f) }
                .pointerInput(letters) {
                    detectTapGestures(
                        onPress = { o ->
                            pick(o.y)
                            tryAwaitRelease()
                            active = null
                        },
                    )
                }
                .pointerInput(letters) {
                    detectVerticalDragGestures(
                        onDragStart = { o -> pick(o.y) },
                        onDragEnd = { active = null },
                        onDragCancel = { active = null },
                        onVerticalDrag = { change, _ -> pick(change.position.y) },
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { l ->
                Text(
                    railLabel(l),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (l == active) Space.Violet else Space.TextFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
    }
}
