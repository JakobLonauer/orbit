package io.github.thelony97.orbit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Motion
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * „Rückgängig statt Nachfragen“ (PLAN 1.2): after deleting, archiving, logging … a bar with a
 * running-out ring offers to undo. One at a time; a new offer replaces the old one.
 */
object Undo {
    const val SHOW_MS = 5_000

    class Offer(val text: String, val undo: () -> Unit, val id: Long = System.nanoTime())

    private val _current = MutableStateFlow<Offer?>(null)
    val current: StateFlow<Offer?> = _current.asStateFlow()

    fun offer(text: String, undo: () -> Unit) {
        _current.value = Offer(text, undo)
    }

    fun dismiss(offer: Offer) {
        if (_current.value?.id == offer.id) _current.value = null
    }
}

@Composable
fun UndoBar(modifier: Modifier = Modifier) {
    val offer by Undo.current.collectAsState()
    val haptics = rememberHaptics()
    AnimatedVisibility(
        visible = offer != null,
        modifier = modifier,
        enter = slideInVertically(tween(Motion.MEDIUM_MS, easing = Motion.Emphasized)) { it },
        exit = slideOutVertically(tween(Motion.FAST_MS)) { it } + fadeOut(tween(Motion.FAST_MS)),
    ) {
        val o = offer ?: return@AnimatedVisibility
        val ring = remember(o.id) { Animatable(1f) }
        LaunchedEffect(o.id) {
            ring.animateTo(0f, tween(Undo.SHOW_MS, easing = LinearEasing))
            Undo.dismiss(o)
        }
        val pill = RoundedCornerShape(50)
        Row(
            Modifier
                .glow(Space.VioletDeep, 20.dp, pill, 0.2f)
                .clip(pill)
                .background(Space.PanelHigh, pill)
                .border(1.dp, Space.LineStrong, pill)
                .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(o.text, style = MaterialTheme.typography.bodyMedium, color = Space.Text)
            Spacer(Modifier.width(12.dp))
            Row(
                Modifier
                    .clip(pill)
                    .clickable {
                        haptics.perform(Haptic.Confirm)
                        o.undo()
                        Undo.dismiss(o)
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(18.dp)) {
                    Canvas(Modifier.size(18.dp)) {
                        val w = 2.dp.toPx()
                        drawArc(Space.Line, 0f, 360f, false, style = Stroke(w))
                        drawArc(Space.Violet, -90f, 360f * ring.value, false, style = Stroke(w, cap = StrokeCap.Round))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text("Rückgängig", style = MaterialTheme.typography.labelLarge, color = Space.Violet)
            }
        }
    }
}
