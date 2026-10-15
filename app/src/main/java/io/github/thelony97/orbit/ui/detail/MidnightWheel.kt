package io.github.thelony97.orbit.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.domain.ReminderPlan
import io.github.thelony97.orbit.core.domain.SendLater
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.zone
import io.github.thelony97.orbit.ui.theme.Space
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val HOURS = 24
private const val MINUTES_PER_HOUR = 60

/** Released this close to the wheel's centre = nothing is planned. */
private const val CANCEL_DP = 44f
private const val RADIUS_DP = 130f

/** Where the wheel sits: around the thumb, pulled inside the window so the whole day is visible. */
internal fun wheelCenter(thumb: Offset, window: androidx.compose.ui.geometry.Size, density: Float): Offset {
    val m = (RADIUS_DP + 24f) * density
    return Offset(thumb.x.coerceIn(m, (window.width - m).coerceAtLeast(m)), thumb.y.coerceIn(m, (window.height - m).coerceAtLeast(m)))
}

/** A finger that has not moved this far from where the wheel opened has not picked anything yet. */
private const val MOVE_DP = 24f

/** The pick for the finger at [finger] around [center]; null in the centre or before the finger moved (= cancel). */
internal fun wheelPick(c: Contact, data: OrbitData, now: LocalDateTime, center: Offset, finger: Offset, density: Float, start: Offset = finger + Offset(1e6f, 0f)): SendLater.Pick? {
    if ((finger - center).getDistance() < CANCEL_DP * density) return null
    if ((finger - start).getDistance() < MOVE_DP * density) return null
    val occasion = SendLater.nextOccasion(c, data.contacts, now.toLocalDate())
    return SendLater.at(SendLater.minuteOf(angleAt(finger, center)), now, occasion?.date)
}

/**
 * Mitternachts-Rad (PLAN 9 E4): Senden halten → a day around the thumb, 00:00 at the top. The
 * finger never lets go: turning picks the time in 5-minute steps (a tick per hour), near the top
 * the next occasion's midnight pulls the hand in (Einrasten); letting go plans, letting go in the
 * centre cancels. Pure display – the gesture lives on the send orb.
 */
@Composable
internal fun MidnightWheelOverlay(c: Contact, data: OrbitData, center: Offset, finger: Offset, modifier: Modifier = Modifier, start: Offset = finger + Offset(1e6f, 0f)) {
    val haptics = rememberHaptics()
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val now = remember { LocalDateTime.now(zone()) }
    val occasion = remember(c, data) { SendLater.nextOccasion(c, data.contacts, now.toLocalDate()) }
    val pick = wheelPick(c, data, now, center, finger, density, start)
    val minute = SendLater.minuteOf(angleAt(finger, center))
    // Haptics: Tick per hour, Einrasten on the magnet, nothing in the centre.
    val hour = if (pick == null) -1 else if (pick.magnet) -2 else minute / MINUTES_PER_HOUR
    androidx.compose.runtime.LaunchedEffect(hour) {
        when (hour) {
            -2 -> haptics.perform(Haptic.Threshold)
            -1 -> Unit
            else -> haptics.perform(Haptic.Tick)
        }
    }
    val open = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) { open.animateTo(1f, io.github.thelony97.orbit.core.designsystem.Motion.snappy()) }
    val r = RADIUS_DP.dp
    Box(modifier.fillMaxSize().background(Space.Void.copy(alpha = 0.55f * open.value))) {
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset((center.x - r.toPx()).toInt(), (center.y - r.toPx()).toInt()) }
                .size(r * 2)
                .graphicsLayer { val k = 0.7f + 0.3f * open.value; scaleX = k; scaleY = k; alpha = open.value.coerceIn(0f, 1f) },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Space.PanelHigh.copy(alpha = 0.92f), size.minDimension / 2f)
                drawWheel(if (pick?.magnet == true) 360f else SendLater.angleOf(minute), pick?.magnet == true, pick != null)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (pick == null) "–" else SendLater.label(pick.at, now),
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    color = if (pick?.magnet == true) Space.Orange else Space.Text,
                )
                if (pick?.magnet == true && occasion != null) {
                    Text(ReminderPlan.phrase(occasion) { id -> data.contacts.firstOrNull { it.id == id }?.let { Logic.shortName(it) }.orEmpty() },
                        style = MaterialTheme.typography.labelMedium, color = Space.Orange)
                }
            }
        }
    }
}

/** Degrees from the top, clockwise. */
private fun angleAt(p: Offset, center: Offset): Float {
    val deg = Math.toDegrees(atan2((p.y - center.y).toDouble(), (p.x - center.x).toDouble())).toFloat() + 90f
    return (deg + 360f) % 360f
}

private fun DrawScope.drawWheel(angle: Float, magnet: Boolean, active: Boolean) {
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
    if (!active) return
    drawArc(
        accent.copy(alpha = 0.28f), -90f, angle.coerceAtLeast(0.5f), false,
        Offset(center.x - r, center.y - r), androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round),
    )
    val a = (angle - 90f) * PI.toFloat() / 180f
    drawCircle(accent, 14.dp.toPx(), Offset(center.x + r * cos(a), center.y + r * sin(a)))
    drawCircle(Space.Void, 5.dp.toPx(), Offset(center.x + r * cos(a), center.y + r * sin(a)))
}
