package io.github.thelony97.orbit.ui.callbacks

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.actions.ContactActions
import io.github.thelony97.orbit.core.designsystem.Corners
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Motion
import io.github.thelony97.orbit.core.designsystem.Spacing
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.model.CheckItem
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.EmptyHint
import io.github.thelony97.orbit.ui.components.OrbButton
import io.github.thelony97.orbit.ui.components.SpaceBackground
import io.github.thelony97.orbit.ui.components.Undo
import io.github.thelony97.orbit.ui.components.contactColor
import io.github.thelony97.orbit.ui.components.glass
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.launch

/** One open topic and whose it is. */
data class OpenTopic(val contact: Contact, val topic: CheckItem)

/** All open topics of visible contacts – the oldest first (they waited longest). */
fun openTopics(data: OrbitData): List<OpenTopic> = data.contacts
    .filter { it.deletedAt == null && !it.archived && !it.isMe }
    .flatMap { c -> c.topics.filter { !it.done }.map { OpenTopic(c, it) } }
    .sortedBy { it.topic.createdAt }

private fun setDone(t: OpenTopic, done: Boolean) =
    OrbitRepository.editContact(t.contact.id) { c -> c.copy(topics = c.topics.map { if (it.id == t.topic.id) it.copy(done = done) else it }) }

/**
 * Themen-Stapel (PLAN 9 E8): every open topic as a card. Right = call (or write without a
 * number), left = erledigt with Rückgängig; the next card moves up.
 */
@Composable
fun TopicStackScreen(data: OrbitData, idx: Index, nav: Nav, modifier: Modifier = Modifier) {
    val list = openTopics(data)
    SpaceBackground(modifier) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter, vertical = Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                OrbButton(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", { nav.back() })
                Spacer(Modifier.width(Spacing.M))
                Text("Themen", style = MaterialTheme.typography.headlineSmallEmphasized, color = Space.Text, modifier = Modifier.weight(1f))
                if (list.isNotEmpty()) Text("${list.size}", style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), color = Space.TextDim)
            }
            if (list.isEmpty()) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { EmptyHint("Alles besprochen", "", action = "Schließen" to { nav.back() }) }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.Gutter), contentAlignment = Alignment.TopCenter) {
                    list.take(3).withIndex().reversed().forEach { (depth, t) ->
                        key(t.topic.id) { TopicCard(t, depth, data, idx, nav) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicCard(t: OpenTopic, depth: Int, data: OrbitData, idx: Index, nav: Nav) {
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val lift by animateFloatAsState(depth.toFloat(), tween(Motion.MEDIUM_MS, easing = Motion.Emphasized), label = "depth")
    val drag = remember { Animatable(0f) }
    val top = depth == 0
    val canCall = t.contact.phones.any { it.value.isNotBlank() }

    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = lift * with(density) { 16.dp.toPx() }
                val s = 1f - 0.05f * lift
                scaleX = s
                scaleY = s
                alpha = 1f - 0.28f * lift
                translationX = drag.value
                rotationZ = drag.value / 60f
            }
            .then(
                if (!top) Modifier else Modifier.stackSwipe(t.topic.id, drag, scope, onRight = {
                    haptics.perform(Haptic.Confirm)
                    if (canCall) ContactActions.call(ctx, t.contact, null, data.settings) else nav.open(Route.Detail(t.contact.id))
                }, onLeft = {
                    haptics.perform(Haptic.Confirm)
                    setDone(t, true)
                    Undo.offer("Besprochen") { setDone(t, false) }
                }),
            )
            .background(Space.Deep, Corners.Card)
            .glass(Corners.Card, tint = Space.PanelHigh, border = Space.Line)
            .clickable(enabled = top) { nav.open(Route.Detail(t.contact.id)) }
            .padding(Spacing.L),
    ) {
        TopicCardContent(t, idx, canCall, top)
    }
}

@Composable
private fun TopicCardContent(t: OpenTopic, idx: Index, canCall: Boolean, top: Boolean) {
    val content by animateFloatAsState(if (top) 1f else 0f, tween(Motion.MEDIUM_MS), label = "content")
    Column(Modifier.graphicsLayer { alpha = content }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(t.contact, contactColor(t.contact, idx), 44.dp)
            Spacer(Modifier.width(Spacing.M))
            Text(Logic.shortName(t.contact), style = MaterialTheme.typography.titleMedium, color = Space.TextDim)
        }
        Spacer(Modifier.height(Spacing.M))
        Text(t.topic.text, style = MaterialTheme.typography.headlineSmall, color = Space.Text, maxLines = 4, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(Spacing.L))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Check, "Besprochen", tint = Space.TextFaint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.weight(1f))
            Icon(if (canCall) Icons.Rounded.Call else Icons.AutoMirrored.Rounded.Chat, if (canCall) "Anrufen" else "Öffnen", tint = Space.Teal, modifier = Modifier.size(22.dp))
        }
    }
}

/** The stack's swipe: past a third of the width right or left; otherwise it springs back. */
private fun Modifier.stackSwipe(
    key: Any,
    drag: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    scope: kotlinx.coroutines.CoroutineScope,
    onRight: () -> Unit,
    onLeft: () -> Unit,
): Modifier = pointerInput(key) {
    val limit = size.width * 0.32f
    detectHorizontalDragGestures(
        onDragEnd = {
            scope.launch {
                when {
                    drag.value > limit -> {
                        drag.animateTo(0f, Motion.snappy())
                        onRight()
                    }
                    drag.value < -limit -> {
                        drag.animateTo(-size.width * 1.3f, tween(Motion.FAST_MS, easing = Motion.Exit))
                        onLeft()
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
}
