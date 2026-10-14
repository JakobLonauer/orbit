package io.github.thelony97.orbit.ui.occasions

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.designsystem.Motion
import io.github.thelony97.orbit.ui.theme.Space
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private fun powerSaving(ctx: android.content.Context) =
    ctx.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true

/**
 * Sternenregen (PLAN 1.3): a birthday today – once per day a short shower of falling stars over
 * the Anlässe tab (W9 exception, about 1.4 s). Drawn only; never takes a touch; off in power saving.
 */
@Composable
fun StarRain(play: Boolean, onPlayed: () -> Unit) {
    val ctx = LocalContext.current
    if (!play || powerSaving(ctx)) return
    val t = remember { Animatable(0f) }
    val stars = remember {
        val r = Random(7)
        List(36) { Triple(r.nextFloat(), r.nextFloat() * 0.5f, 0.6f + r.nextFloat() * 0.8f) }
    }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(1400, easing = LinearEasing))
        onPlayed()
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = t.value
        if (p >= 1f) return@Canvas
        for ((x, delay, speed) in stars) {
            val local = ((p - delay) / (1f - delay)).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) continue
            val y = -20f + local * speed * size.height
            val px = x * size.width - local * 60.dp.toPx()
            val alpha = (1f - local) * 0.9f
            val tail = Offset(px + 24.dp.toPx(), y - 24.dp.toPx())
            drawLine(
                Brush.linearGradient(listOf(Space.Orange.copy(alpha = 0f), Space.Orange.copy(alpha = alpha)), tail, Offset(px, y)),
                tail, Offset(px, y), strokeWidth = 2.dp.toPx(),
            )
            drawCircle(Color.White.copy(alpha = alpha), radius = 2.dp.toPx(), center = Offset(px, y))
        }
    }
}

/**
 * Sternenfunke (PLAN 1.3): ticking „Gratuliert“ sends eight sparks out of the check (400 ms).
 * [key] changes on every tick; drawn outside the element without taking space.
 */
fun Modifier.starSpark(progress: () -> Float): Modifier = drawWithContent {
    drawContent()
    val p = progress()
    if (p <= 0f || p >= 1f) return@drawWithContent
    val c = center
    for (i in 0 until 8) {
        val a = i * Math.PI / 4
        val r = size.minDimension / 2f + p * 18.dp.toPx()
        val pt = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
        drawCircle(Space.Teal.copy(alpha = 1f - p), radius = (1f - p) * 2.5.dp.toPx() + 0.5f, center = pt)
    }
}

@Composable
fun rememberSpark(trigger: Boolean): () -> Float {
    val ctx = LocalContext.current
    val a = remember { Animatable(1f) }
    val last = remember { booleanArrayOf(trigger) }
    LaunchedEffect(trigger) {
        // Only the step to „Gratuliert“ sparks, not taking it back or the first composition.
        if (trigger && !last[0] && !powerSaving(ctx)) {
            a.snapTo(0f)
            a.animateTo(1f, tween(Motion.MAX_MS))
        }
        last[0] = trigger
    }
    return { a.value }
}
