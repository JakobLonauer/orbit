package io.github.thelony97.orbit.ui.occasions

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Motion
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.domain.YearRing
import io.github.thelony97.orbit.ui.theme.Space
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** One occasion on the ring: its next date and the contact's colour. */
data class RingDot(val date: LocalDate, val color: Color)

private val LETTERS = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")

/**
 * Jahresrad (PLAN 1.3): the year as an orbit, occasions as dots (angle = day of the year), today
 * as a planet. On opening the ring draws itself and the dots appear one after another (W9
 * exception); tapping a month jumps there in the list. All motion is read while drawing.
 */
@Composable
fun YearRingView(
    dots: List<RingDot>,
    today: LocalDate,
    title: String,
    subtitle: String,
    onMonth: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val saver = remember { ctx.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true }
    val draw = remember { Animatable(if (saver) 1f else 0f) }
    LaunchedEffect(Unit) { if (!saver) draw.animateTo(1f, tween(900, easing = Motion.Emphasized)) }
    val haptics = rememberHaptics()
    val measurer = rememberTextMeasurer()
    val letterStyle = MaterialTheme.typography.labelSmall.copy(color = Space.TextFaint)
    // Measured once, not per frame.
    val letters = remember(measurer, letterStyle) { LETTERS.map { measurer.measure(it, letterStyle) } }
    val todayAngle = remember(today) { YearRing.angleOf(today) }
    Box(modifier.fillMaxWidth().height(232.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(today.year) {
                    detectTapGestures { pos ->
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val r = min(size.width, size.height) / 2f - 28.dp.toPx()
                        YearRing.monthAt(pos.x - c.x, pos.y - c.y, r - 36.dp.toPx(), r + 28.dp.toPx(), today.year)?.let {
                            haptics.perform(Haptic.Tick)
                            onMonth(it)
                        }
                    }
                },
        ) {
            val p = draw.value
            val c = center
            val r = min(size.width, size.height) / 2f - 28.dp.toPx()
            fun at(deg: Float, radius: Float = r): Offset {
                val a = Math.toRadians((deg - 90f).toDouble())
                return Offset(c.x + radius * cos(a).toFloat(), c.y + radius * sin(a).toFloat())
            }
            val sweep = 360f * p
            // The orbit draws itself from January on.
            drawArc(
                Space.LineStrong, startAngle = -90f, sweepAngle = sweep, useCenter = false,
                topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = Stroke(1.5.dp.toPx()),
            )
            // Month ticks and letters outside the ring.
            for (m in 1..12) {
                val deg = YearRing.monthStart(m, today.year)
                if (deg > sweep) continue
                drawLine(Space.Line, at(deg, r - 5.dp.toPx()), at(deg, r + 5.dp.toPx()), strokeWidth = 1.dp.toPx())
                val mid = deg + 15f
                val layout = letters[m - 1]
                val pos = at(mid, r + 16.dp.toPx())
                drawText(layout, topLeft = Offset(pos.x - layout.size.width / 2f, pos.y - layout.size.height / 2f))
            }
            // Occasions: each dot fades in once the drawing passes its day.
            for (d in dots) {
                val deg = YearRing.angleOf(d.date)
                val alpha = ((sweep - deg) / 24f).coerceIn(0f, 1f)
                if (alpha <= 0f) continue
                drawCircle(d.color.copy(alpha = alpha), radius = 4.dp.toPx(), center = at(deg))
            }
            // Today: the planet, in the sun's light once the ring reached it.
            if (sweep >= todayAngle) {
                val pt = at(todayAngle)
                drawCircle(Brush.radialGradient(listOf(Space.Orange.copy(alpha = 0.55f), Space.Orange.copy(alpha = 0f)), pt, 16.dp.toPx()), radius = 16.dp.toPx(), center = pt)
                drawCircle(Space.Orange, radius = 6.dp.toPx(), center = pt)
                drawCircle(Space.Text.copy(alpha = 0.9f), radius = 2.dp.toPx(), center = pt - Offset(1.5.dp.toPx(), 1.5.dp.toPx()))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLargeEmphasized, color = Space.Text)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Space.TextDim)
        }
    }
}
