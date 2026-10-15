package io.github.thelony97.orbit.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.domain.ReminderPlan
import io.github.thelony97.orbit.core.domain.SendLater
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.zone
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.theme.Space
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val HOURS = 24
private const val MINUTES_PER_HOUR = 60

/**
 * Mitternachts-Rad (PLAN 9 E4): a day around the thumb – 00:00 at the top. Turning picks the time
 * in 5-minute steps (a tick per hour); near the top the next occasion's midnight pulls the hand
 * in with a stronger snap.
 */
@Composable
internal fun MidnightWheel(c: Contact, data: OrbitData, onPick: (LocalDateTime) -> Unit, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    val now = remember { LocalDateTime.now(zone()) }
    val occasion = remember(c, data) { SendLater.nextOccasion(c, data.contacts, now.toLocalDate()) }
    var angle by remember { mutableFloatStateOf(SendLater.angleOf(((now.hour + 1) % HOURS) * MINUTES_PER_HOUR)) }
    val minute = SendLater.minuteOf(angle)
    val pick = SendLater.at(minute, now, occasion?.date)
    var lastHour by remember { mutableFloatStateOf(-1f) }
    var wasMagnet by remember { mutableFloatStateOf(0f) }

    fun turn(to: Float) {
        angle = to
        val m = SendLater.minuteOf(to)
        val p = SendLater.at(m, now, occasion?.date)
        val magnet = if (p.magnet) 1f else 0f
        if (magnet != wasMagnet) {
            wasMagnet = magnet
            if (p.magnet) haptics.perform(Haptic.Threshold)
        } else if ((m / MINUTES_PER_HOUR).toFloat() != lastHour) {
            haptics.perform(Haptic.Tick)
        }
        lastHour = (m / MINUTES_PER_HOUR).toFloat()
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(32.dp)).background(Space.PanelHigh).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                Canvas(
                    Modifier.size(260.dp)
                        .pointerInput(Unit) { detectTapGestures { o -> turn(angleAt(o, Offset(size.width / 2f, size.height / 2f))) } }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                turn(angleAt(change.position, Offset(size.width / 2f, size.height / 2f)))
                            }
                        },
                ) { drawWheel(if (pick.magnet) 360f else SendLater.angleOf(minute), pick.magnet) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        SendLater.label(pick.at, now),
                        style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                        color = if (pick.magnet) Space.Orange else Space.Text,
                    )
                    if (pick.magnet && occasion != null) {
                        Text(ReminderPlan.phrase(occasion) { id -> data.contacts.firstOrNull { it.id == id }?.let { Logic.shortName(it) }.orEmpty() },
                            style = MaterialTheme.typography.labelMedium, color = Space.Orange)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            GlowButton("Planen", { haptics.perform(Haptic.Confirm); onPick(pick.at) }, color = if (pick.magnet) Space.Orange else Space.Violet)
        }
    }
}

/** Degrees from the top, clockwise. */
private fun angleAt(p: Offset, center: Offset): Float {
    val deg = Math.toDegrees(atan2((p.y - center.y).toDouble(), (p.x - center.x).toDouble())).toFloat() + 90f
    return (deg + 360f) % 360f
}

private fun DrawScope.drawWheel(angle: Float, magnet: Boolean) {
    val r = size.minDimension / 2 - 18.dp.toPx()
    // Licht-Code: a time is no running thing – Violet to pick, Orange on the magnet (Anlass).
    val accent = if (magnet) Space.Orange else Space.Violet
    drawCircle(Space.TextFaint.copy(alpha = 0.18f), r, style = Stroke(10.dp.toPx()))
    for (h in 0 until HOURS) {
        val a = (h * 360f / HOURS - 90f) * PI.toFloat() / 180f
        val long = h % 6 == 0
        val inner = r - (if (long) 14.dp else 7.dp).toPx()
        drawLine(
            if (h == 0) Space.Orange.copy(alpha = 0.8f) else Space.TextFaint.copy(alpha = if (long) 0.7f else 0.35f),
            Offset(center.x + inner * cos(a), center.y + inner * sin(a)),
            Offset(center.x + (r - 2.dp.toPx()) * cos(a), center.y + (r - 2.dp.toPx()) * sin(a)),
            if (long) 2.5.dp.toPx() else 1.5.dp.toPx(), cap = StrokeCap.Round,
        )
    }
    drawArc(accent.copy(alpha = 0.28f), -90f, angle.coerceAtLeast(0.5f), false, Offset(center.x - r, center.y - r), androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
    val a = (angle - 90f) * PI.toFloat() / 180f
    drawCircle(accent, 14.dp.toPx(), Offset(center.x + r * cos(a), center.y + r * sin(a)))
    drawCircle(Space.Void, 5.dp.toPx(), Offset(center.x + r * cos(a), center.y + r * sin(a)))
}
