package io.github.thelony97.orbit.ui.live

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.call.CallActivity
import io.github.thelony97.orbit.call.CallSession
import io.github.thelony97.orbit.call.Persist
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Motion
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.domain.LiveMeeting
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.components.fallbackColor
import io.github.thelony97.orbit.ui.components.glass
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val UNDO_MS = 5_000L

/**
 * Live-Insel (PLAN 9 C2): a glass capsule at the top that grows out of a point (250 ms) while
 * something runs; its content changes by morphing. Tap opens what it is about. C3: on a meeting
 * the minutes are pulled down – they snap at +5/+10/+15 (Einrasten each), letting go sends
 * „Bin ca. 10 Min später da“ after a 5-second Rückgängig ring.
 */
@Composable
fun LiveIsland(live: Live?, nav: Nav, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        live != null,
        modifier = modifier,
        enter = scaleIn(tween(Motion.MEDIUM_MS, easing = Motion.Emphasized), initialScale = 0.05f, transformOrigin = TransformOrigin(0.5f, 0f)) +
            fadeIn(tween(Motion.FAST_MS)),
        exit = scaleOut(tween(Motion.FAST_MS), targetScale = 0.05f, transformOrigin = TransformOrigin(0.5f, 0f)) + fadeOut(tween(Motion.FAST_MS)),
    ) {
        // While it shrinks away the last content stays.
        val keep = remember { arrayOfNulls<Live>(1) }
        if (live != null) keep[0] = live
        val l = keep[0] ?: return@AnimatedVisibility
        AnimatedContent(
            l,
            contentKey = { it.key },
            transitionSpec = { (fadeIn(tween(Motion.MEDIUM_MS)) togetherWith fadeOut(tween(Motion.FAST_MS))).using(SizeTransform(clip = false)) },
            label = "island",
        ) { item ->
            when (item) {
                is Live.Call -> CallPill(item)
                is Live.Redial -> RedialPill(item)
                is Live.Meeting -> MeetingPill(item, nav)
            }
        }
    }
}

@Composable
private fun Capsule(color: Color, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier
            .height(36.dp)
            .glass(RoundedCornerShape(50), tint = Space.PanelHighest, border = color.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun Dot(icon: ImageVector, color: Color) {
    Box(Modifier.padding(end = 8.dp).size(22.dp).background(color.copy(alpha = 0.22f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
    }
}

@Composable
private fun PillText(text: String, color: Color = Space.Text) {
    Text(text, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun CallPill(item: Live.Call) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(item.since) {
        while (item.since > 0) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Capsule(Space.Teal, onClick = { ctx.startActivity(CallActivity.intent(ctx)) }) {
        Dot(Icons.Rounded.Call, Space.Teal)
        val secs = if (item.since > 0) ((now - item.since) / 1_000).coerceAtLeast(0) else -1
        PillText(item.name + if (secs >= 0) " · %d:%02d".format(secs / 60, secs % 60) else "")
    }
}

@Composable
private fun RedialPill(item: Live.Redial) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(item.nextAt) {
        while (item.nextAt > now) {
            delay(250)
            now = System.currentTimeMillis()
        }
    }
    Capsule(Space.Teal, onClick = { ctx.startActivity(CallActivity.intent(ctx)) }) {
        Dot(Icons.Rounded.Repeat, Space.Teal)
        val left = if (item.nextAt > 0) ((item.nextAt - now + 999) / 1_000).coerceAtLeast(0) else -1L
        PillText("Versuch ${if (left >= 0) item.tries + 1 else item.tries} · " + if (left >= 0) "$left s" else "wählt")
    }
}

/** A meeting soon or running; the minutes can be pulled down (C3). */
@Composable
private fun MeetingPill(item: Live.Meeting, nav: Nav) {
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val person = fallbackColor(item.contact.id)
    var drag by remember { mutableFloatStateOf(0f) }
    var step by remember { mutableIntStateOf(0) }
    var sending by remember { mutableIntStateOf(0) }
    val ring = remember { Animatable(0f) }
    val name = Logic.shortName(item.contact)
    val canSend = remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canSend.value = it }

    fun send(minutes: Int) {
        sending = minutes
        scope.launch {
            ring.snapTo(0f)
            ring.animateTo(1f, tween(UNDO_MS.toInt(), easing = androidx.compose.animation.core.LinearEasing))
            if (sending == minutes) {
                io.github.thelony97.orbit.actions.ContactActions.late(ctx, item.contact, LiveMeeting.message(minutes))
                haptics.perform(Haptic.Confirm)
            }
            sending = 0
        }
    }

    val late = if (sending > 0) sending else step
    Capsule(
        if (late > 0) Space.Orange else person,
        onClick = {
            if (sending > 0) {
                // Rückgängig while the ring runs.
                sending = 0
                scope.launch { ring.snapTo(0f) }
                haptics.perform(Haptic.Reject)
            } else {
                nav.open(Route.Detail(item.contact.id))
            }
        },
        modifier = Modifier.pointerInput(item.key) {
            detectVerticalDragGestures(
                onDragEnd = {
                    val minutes = step
                    drag = 0f
                    step = 0
                    if (minutes > 0) {
                        if (ctx.checkSelfPermission(android.Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED && !canSend.value) {
                            permission.launch(android.Manifest.permission.SEND_SMS)
                        }
                        send(minutes)
                    }
                },
                onDragCancel = {
                    drag = 0f
                    step = 0
                },
            ) { change, dy ->
                change.consume()
                drag = (drag + dy).coerceAtLeast(0f)
                val next = LiveMeeting.stepFor(with(density) { drag.toDp().value })
                if (next != step) {
                    haptics.perform(Haptic.Threshold)
                    step = next
                }
            }
        },
    ) {
        Box(
            Modifier.size(22.dp).drawBehind {
                if (sending > 0) {
                    val w = 2.dp.toPx()
                    drawArc(Space.Orange, -90f, 360f * (1f - ring.value), false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Event, null, tint = if (late > 0) Space.Orange else person, modifier = Modifier.size(13.dp))
        }
        Spacer(Modifier.width(8.dp))
        val phase = item.phase
        val time = when (phase) {
            is LiveMeeting.Phase.Soon -> "in ${phase.minutes} Min"
            is LiveMeeting.Phase.On -> "noch ${phase.minutes} Min"
        }
        when {
            sending > 0 -> PillText("+$sending Min · Rückgängig", Space.Orange)
            step > 0 -> PillText("$name · +$step Min", Space.Orange)
            else -> PillText("$name · $time")
        }
    }
}
