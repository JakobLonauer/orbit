package io.github.thelony97.orbit.ui.callbacks

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.thelony97.orbit.actions.ContactActions
import io.github.thelony97.orbit.call.Callbacks
import io.github.thelony97.orbit.core.designsystem.Corners
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Motion
import io.github.thelony97.orbit.core.designsystem.Spacing
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.domain.CallbackStack
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.EmptyHint
import io.github.thelony97.orbit.ui.components.OrbButton
import io.github.thelony97.orbit.ui.components.SpaceBackground
import io.github.thelony97.orbit.ui.components.Undo
import io.github.thelony97.orbit.ui.components.contactColor
import io.github.thelony97.orbit.ui.components.glass
import io.github.thelony97.orbit.ui.theme.Grotesk
import io.github.thelony97.orbit.ui.theme.Space
import io.github.thelony97.orbit.util.Fmt
import kotlinx.coroutines.launch

/** „2× verpasst · 14:32“, „Später · gestern 18:10“, „Mailbox · 09:12“. */
internal fun sourceLine(card: CallbackStack.Card, now: Long = System.currentTimeMillis()): String {
    val head = when (card.source) {
        CallbackStack.Source.MISSED -> if (card.count > 1) "${card.count}× verpasst" else "Verpasst"
        CallbackStack.Source.LATER -> "Später zurückrufen"
        CallbackStack.Source.MAILBOX -> "Mailbox"
    }
    return head + " · " + Fmt.whenShort(card.lastAt, now)
}

/** Marks a card done (swiped left) – with Rückgängig (PLAN 1.2). */
internal fun markDone(card: CallbackStack.Card) {
    OrbitRepository.editSettings { it.copy(dismissedGhosts = (it.dismissedGhosts + card.key).distinct().takeLast(400)) }
    Undo.offer("Erledigt") {
        OrbitRepository.editSettings { it.copy(dismissedGhosts = it.dismissedGhosts - card.key) }
    }
}

/**
 * Rückruf-Stapel (PLAN 9 B3): everyone waiting for a call back as a stack of cards. Right =
 * call, left = done (Rückgängig). A number that costs or looks like a ping call is orange and
 * wants a second swipe (B2). The next card moves up when one goes.
 */
@Composable
fun CallbackStackScreen(data: OrbitData, idx: Index, nav: Nav, modifier: Modifier = Modifier, preview: List<Callbacks.Item>? = null) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val items by produceState(preview, data.settings.dismissedGhosts, tick) {
        if (preview == null) value = Callbacks.load(ctx, data)
    }
    SpaceBackground(modifier) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter, vertical = Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                OrbButton(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", { nav.back() })
                Spacer(Modifier.width(Spacing.M))
                Text("Rückrufe", style = MaterialTheme.typography.headlineSmallEmphasized, color = Space.Text, modifier = Modifier.weight(1f))
                items?.size?.takeIf { it > 0 }?.let { n ->
                    Text("$n", style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), color = Space.TextDim)
                }
            }
            val list = items
            when {
                list == null -> Spacer(Modifier.weight(1f))
                list.isEmpty() -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    EmptyHint("Niemand wartet auf einen Rückruf", "", action = "Schließen" to { nav.back() })
                }
                else -> Stack(list, data, idx, nav, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stack(list: List<Callbacks.Item>, data: OrbitData, idx: Index, nav: Nav, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter), contentAlignment = Alignment.TopCenter) {
        // Back to front: the top card is drawn last. Three are seen, the rest wait behind.
        list.take(3).withIndex().reversed().forEach { (depth, item) ->
            key(item.card.key) {
                StackCard(item, depth, data, idx, nav)
            }
        }
    }
}

@Composable
private fun StackCard(item: Callbacks.Item, depth: Int, data: OrbitData, idx: Index, nav: Nav) {
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // The next card moves up when the one in front goes (PLAN 9 B3 „die nächste rückt nach“).
    val lift by animateFloatAsState(depth.toFloat(), tween(Motion.MEDIUM_MS, easing = Motion.Emphasized), label = "depth")
    val drag = remember { Animatable(0f) }
    val leave = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }
    val top = depth == 0
    val warnColor = if (item.warn) Space.Orange else Space.TextDim

    fun call() {
        val number = item.card.number
        scope.launch {
            haptics.perform(Haptic.Confirm)
            // The card stretches towards the call (Moment), then the call starts.
            leave.animateTo(1f, tween(Motion.MEDIUM_MS, easing = Motion.Emphasized))
            val c = item.contact
            if (c != null) ContactActions.call(ctx, c, number, data.settings) else ContactActions.callNumber(ctx, number)
            drag.snapTo(0f)
            leave.snapTo(0f)
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val d = lift
                translationY = d * with(density) { 16.dp.toPx() }
                val s = 1f - 0.05f * d
                scaleX = s * (1f + 0.06f * leave.value)
                scaleY = s * (1f + 0.25f * leave.value)
                alpha = (1f - 0.28f * d) * (1f - leave.value)
                translationX = drag.value
                rotationZ = drag.value / 60f
            }
            .then(
                if (!top) Modifier else Modifier.pointerInput(item.card.key) {
                    val limit = size.width * 0.32f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                when {
                                    drag.value > limit && item.warn && !armed -> {
                                        // B2: a number that costs – the first swipe only arms it.
                                        haptics.perform(Haptic.Threshold)
                                        armed = true
                                        drag.animateTo(0f, Motion.snappy())
                                    }
                                    drag.value > limit -> call()
                                    drag.value < -limit -> {
                                        haptics.perform(Haptic.Confirm)
                                        drag.animateTo(-size.width * 1.3f, tween(Motion.FAST_MS, easing = Motion.Exit))
                                        markDone(item.card)
                                    }
                                    else -> drag.animateTo(0f, Motion.snappy())
                                }
                            }
                        },
                        onDragCancel = { scope.launch { drag.animateTo(0f, Motion.snappy()) } },
                    ) { change, delta ->
                        change.consume()
                        scope.launch { drag.snapTo(drag.value + delta) }
                    }
                },
            )
            .glass(Corners.Card, tint = Space.PanelHigh, border = if (item.warn) Space.Orange.copy(alpha = 0.45f) else Space.Line)
            .clickable(enabled = top) {
                val c = item.contact
                if (c != null) nav.open(Route.Detail(c.id))
            }
            .padding(Spacing.L),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val c = item.contact
                if (c != null) {
                    Avatar(c, contactColor(c, idx), 56.dp)
                } else {
                    Box(
                        Modifier.size(56.dp).background(Space.Violet.copy(alpha = 0.18f), CircleShape).border(1.5.dp, Space.Violet.copy(alpha = 0.7f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("?", fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = Space.Text)
                    }
                }
                Spacer(Modifier.width(Spacing.L))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleLargeEmphasized, color = Space.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(sourceLine(item.card), style = MaterialTheme.typography.labelLarge, color = Space.TextDim)
                    val second = listOfNotNull(
                        item.hit?.category?.takeIf { it.isNotBlank() },
                        item.info?.line?.takeIf { it.isNotBlank() },
                        if (item.warn && item.info?.costs != true) "Ping-Anruf?" else null,
                    ).joinToString(" · ")
                    if (second.isNotBlank()) Text(second, style = MaterialTheme.typography.labelMedium, color = warnColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(Spacing.L))
            // The two directions, quiet until the finger moves (Bediensprache: right = call, left = done).
            val toward = drag.value
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val left = (-toward / 300f).coerceIn(0f, 1f)
                val right = (toward / 300f).coerceIn(0f, 1f)
                Icon(
                    Icons.Rounded.Check, "Erledigt",
                    tint = lerpColor(Space.TextFaint, Space.Text, left),
                    modifier = Modifier.size(22.dp).graphicsLayer { scaleX = 1f + 0.4f * left; scaleY = 1f + 0.4f * left },
                )
                Spacer(Modifier.weight(1f))
                if (armed) Text("Nochmal wischen · " + (item.info?.line ?: "kostet"), style = MaterialTheme.typography.labelMedium, color = Space.Orange)
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Rounded.Call, "Anrufen",
                    tint = lerpColor(if (item.warn) Space.Orange.copy(alpha = 0.6f) else Space.Teal.copy(alpha = 0.6f), if (item.warn) Space.Orange else Space.Teal, right),
                    modifier = Modifier.size(22.dp).graphicsLayer { scaleX = 1f + 0.4f * right; scaleY = 1f + 0.4f * right },
                )
            }
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))
