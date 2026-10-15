package io.github.thelony97.orbit.ui.call

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.ui.components.GhostButton
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.SpaceBackground
import io.github.thelony97.orbit.ui.components.glow
import io.github.thelony97.orbit.ui.theme.Grotesk
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.delay


private fun initials(name: String): String =
    name.split(' ').filter { it.isNotBlank() && it.first().isLetter() }.take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "#" }

private fun clock(millis: Long): String = io.github.thelony97.orbit.util.Fmt.time(millis)

private fun battery(ctx: android.content.Context): Boolean =
    ctx.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true

/** The planet of the one to call, teal – this screen is about getting through (läuft). */
@Composable
private fun PersistPlanet(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(96.dp)
            .glow(Space.Teal, 36.dp, CircleShape, 0.5f)
            .clip(CircleShape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Space.Teal.copy(alpha = 0.55f), Space.Teal.copy(alpha = 0.18f))))
            .border(2.dp, Space.Teal, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials(name), fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, color = Space.Text)
    }
}

/**
 * PLAN 9 B5: the card at the planned time, like an alarm clock – the time big, who, and one
 * large „Anrufen“ with rings going out; „10 Min später“ and „Abbrechen“ quietly below.
 */
@Composable
fun PersistAlarmCard(
    name: String,
    number: String,
    at: Long,
    phoneApp: Boolean,
    onCall: () -> Unit,
    onLater: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val ctx = LocalContext.current
    val still = remember { battery(ctx) }
    SpaceBackground(modifier, nebula = { Space.Teal }) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Repeat, null, tint = Space.Teal, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (phoneApp) "Dranbleiben" else "Anrufen", style = MaterialTheme.typography.labelLarge, color = Space.Teal)
            }
            Text(
                clock(at),
                fontFamily = Grotesk,
                fontWeight = FontWeight.Medium,
                fontSize = 76.sp,
                color = Space.Text,
                style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
            )
            Spacer(Modifier.height(28.dp))
            PersistPlanet(name)
            Spacer(Modifier.height(16.dp))
            Text(name, style = MaterialTheme.typography.headlineMediumEmphasized, color = Space.Text, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(number, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"), color = Space.TextDim)
            Spacer(Modifier.weight(1f))
            // Rings go out from the button like a ringing alarm – still in battery saver (PLAN 1.1).
            val wave = if (still) null else rememberInfiniteTransition(label = "wave").animateFloat(
                0f, 1f, infiniteRepeatable(tween(1_600, easing = LinearEasing), RepeatMode.Restart), label = "wave",
            )
            Box(
                Modifier
                    .size(176.dp)
                    .drawBehind {
                        val w = wave?.value ?: return@drawBehind
                        listOf(w, (w + 0.5f) % 1f).forEach { t ->
                            drawCircle(Space.Teal.copy(alpha = 0.35f * (1f - t)), radius = size.minDimension / 2f * (0.5f + 0.5f * t), style = Stroke(2.dp.toPx()))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(88.dp)
                        .glow(Space.Teal, 28.dp, CircleShape, 0.6f)
                        .clip(CircleShape)
                        .background(Space.Teal)
                        .clickable { haptics.perform(Haptic.Confirm); onCall() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Call, "Anrufen", tint = Space.Void, modifier = Modifier.size(36.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GhostButton("10 Min später", { haptics.perform(Haptic.Tick); onLater() })
                GhostButton("Abbrechen", { haptics.perform(Haptic.Tick); onCancel() })
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * PLAN 9 B5 Moment: between two tries – „Versuch 8 · 07:31“ as a live chip, the ring counts the
 * 15 s down (a tick for each of the last three), „Jetzt“ skips the wait.
 */
@Composable
fun PersistPause(p: PersistUi, onNow: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    var t by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(p.nextAt) {
        var lastSecond = -1L
        while (p.nextAt > 0) {
            t = System.currentTimeMillis()
            val left = ((p.nextAt - t + 999) / 1_000).coerceAtLeast(0)
            if (left != lastSecond && left in 1..3) haptics.perform(Haptic.Segment)
            lastSecond = left
            if (left == 0L) break
            androidx.compose.runtime.withFrameMillis { }
        }
    }
    val total = io.github.thelony97.orbit.core.domain.Redial.PAUSE_MS.toFloat()
    val waiting = p.nextAt > 0
    SpaceBackground(modifier, nebula = { Space.Teal }) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            Text(p.name, style = MaterialTheme.typography.headlineMediumEmphasized, color = Space.Text, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(p.number, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"), color = Space.TextDim)
            Spacer(Modifier.height(16.dp))
            // The live chip: which try and when it goes out.
            val tryNo = if (waiting) p.tries + 1 else p.tries
            val at = if (waiting) p.nextAt else t
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Space.Teal.copy(alpha = 0.16f))
                    .border(1.dp, Space.Teal.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Repeat, null, tint = Space.Teal, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Versuch $tryNo · ${clock(at)}", style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), color = Space.Teal)
            }
            Spacer(Modifier.weight(1f))
            val left = if (waiting) (p.nextAt - t).coerceIn(0L, total.toLong()) else 0L
            Box(
                Modifier
                    .size(208.dp)
                    .drawBehind {
                        val stroke = 8.dp.toPx()
                        val inset = stroke / 2f
                        val arc = Size(size.width - stroke, size.height - stroke)
                        drawArc(Space.PanelHighest, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                        val frac = if (waiting) left / total else 1f
                        drawArc(Space.Teal, -90f, 360f * frac, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (waiting) {
                    Text(
                        "${(left + 999) / 1_000}",
                        fontFamily = Grotesk,
                        fontWeight = FontWeight.Medium,
                        fontSize = 72.sp,
                        color = Space.Text,
                        style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                    )
                } else {
                    val ctx = LocalContext.current
                    val still = remember { battery(ctx) }
                    val pulse = if (still) null else rememberInfiniteTransition(label = "dial").animateFloat(
                        0.9f, 1.06f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "dial",
                    )
                    Icon(
                        Icons.Rounded.Call, null, tint = Space.Teal,
                        modifier = Modifier.size(56.dp).graphicsLayer { val s = pulse?.value ?: 1f; scaleX = s; scaleY = s },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (waiting) p.last else "Wählt …",
                style = MaterialTheme.typography.titleMedium,
                color = Space.TextDim,
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                GhostButton("Abbrechen", { haptics.perform(Haptic.Tick); onStop() })
                if (waiting) GlowButton("Jetzt", { haptics.perform(Haptic.Confirm); onNow() }, color = Space.Teal, icon = Icons.Rounded.Call)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * PLAN 9 B5: Jakob's own call was busy – one offer to stay on it. Goes away by itself after a few
 * seconds; nothing else to do here.
 */
@Composable
fun BusyOffer(name: String, onPersist: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val close by androidx.compose.runtime.rememberUpdatedState(onClose)
    LaunchedEffect(Unit) {
        delay(BUSY_OFFER_MS)
        close()
    }
    SpaceBackground(modifier) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(72.dp))
            PersistPlanet(name)
            Spacer(Modifier.height(16.dp))
            Text(name, style = MaterialTheme.typography.headlineMediumEmphasized, color = Space.Text, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Besetzt", style = MaterialTheme.typography.titleMedium, color = Space.TextDim)
            Spacer(Modifier.weight(1f))
            GlowButton(
                "Dranbleiben", { haptics.perform(Haptic.Confirm); onPersist() },
                modifier = Modifier.fillMaxWidth(0.7f), color = Space.Teal, icon = Icons.Rounded.Repeat,
            )
            Spacer(Modifier.height(12.dp))
            GhostButton("Schließen", onClose)
            Spacer(Modifier.height(32.dp))
        }
    }
}

private const val BUSY_OFFER_MS = 10_000L
