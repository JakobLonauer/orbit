package io.github.thelony97.orbit.ui.list

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Motion
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.toLocalDate
import io.github.thelony97.orbit.domain.today
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.contactColor
import io.github.thelony97.orbit.ui.components.entryIcon
import io.github.thelony97.orbit.ui.components.glow
import io.github.thelony97.orbit.ui.theme.Space
import io.github.thelony97.orbit.util.Fmt
import java.time.temporal.ChronoUnit

enum class PreviewAction(val label: String, val icon: ImageVector) {
    CALL("Anrufen", Icons.Rounded.Call),
    SMS("SMS", Icons.Rounded.Sms),
    LOG("Eintragen", Icons.Rounded.EditNote),
    SELECT("Auswählen", Icons.Rounded.CheckCircle),
    OPEN("Öffnen", Icons.Rounded.OpenInFull),
}

/**
 * Long-press preview (PLAN 3.2): the contact's last three entries and actions. The finger
 * stays down – sliding onto an action highlights it, releasing there runs it, releasing
 * anywhere else just closes. [pointer] is the finger in screen coordinates.
 */
class PreviewState(val contact: Contact) {
    var pointer by mutableStateOf(Offset.Unspecified)
    val actionBounds = mutableStateMapOf<PreviewAction, Rect>()
    /** PLAN 9 E20: group chips the contact can be dropped on (id → screen bounds). */
    val groupBounds = mutableStateMapOf<String, Rect>()

    fun hovered(): PreviewAction? =
        if (pointer == Offset.Unspecified) null else actionBounds.entries.firstOrNull { it.value.contains(pointer) }?.key

    fun hoveredGroup(): String? =
        if (pointer == Offset.Unspecified) null else groupBounds.entries.firstOrNull { it.value.contains(pointer) }?.key
}

/** At most this many group targets – pinned first, then the rest in their order. */
private const val GROUP_TARGETS = 4

@Composable
fun PreviewCard(state: PreviewState, idx: Index, groups: List<io.github.thelony97.orbit.core.model.Group> = emptyList()) {
    val c = state.contact
    val haptics = rememberHaptics()
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        haptics.perform(Haptic.Threshold)
        appear.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 700f))
    }
    val hovered = state.hovered()
    LaunchedEffect(hovered) { if (hovered != null) haptics.perform(Haptic.Tick) }
    val hoveredGroup = state.hoveredGroup()
    LaunchedEffect(hoveredGroup) { if (hoveredGroup != null) haptics.perform(Haptic.Threshold) }
    val targets = remember(groups) { (groups.filter { it.pinned } + groups.filterNot { it.pinned }).take(GROUP_TARGETS) }
    val color = contactColor(c, idx)
    val actions = buildList {
        if (c.phones.isNotEmpty()) { add(PreviewAction.CALL); add(PreviewAction.SMS) }
        add(PreviewAction.LOG)
        add(PreviewAction.SELECT)
        add(PreviewAction.OPEN)
    }
    Popup(alignment = Alignment.Center, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }
                .background(Space.Void.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .padding(horizontal = 28.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val s = 0.9f + 0.1f * appear.value
                        scaleX = s; scaleY = s
                    }
                    .glow(color, 30.dp, RoundedCornerShape(28.dp), 0.35f)
                    .background(Space.PanelHigh, RoundedCornerShape(28.dp))
                    .border(1.dp, Space.LineStrong, RoundedCornerShape(28.dp))
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(c, color, 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Logic.displayName(c, false), style = MaterialTheme.typography.titleLargeEmphasized, color = Space.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val job = listOf(c.position, if (c.isOrg) "" else c.company).filter { it.isNotBlank() }.joinToString(" · ")
                        if (job.isNotEmpty()) Text(job, style = MaterialTheme.typography.bodySmall, color = Space.TextDim, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(14.dp))
                val todayDate = today()
                c.entries.take(3).forEach { e ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        Icon(entryIcon(idx.types[e.typeId]?.icon ?: "star"), null, tint = Space.TextDim, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            val days = ChronoUnit.DAYS.between(e.at.toLocalDate(), todayDate)
                            Text(
                                "${idx.types[e.typeId]?.name ?: "Eintrag"} · ${Fmt.ago(days)}" + (e.durationSec?.let { " · " + Fmt.duration(it) } ?: ""),
                                style = MaterialTheme.typography.labelLarge,
                                color = Space.Text,
                            )
                            val text = if (e.memberId != null) "" else e.note.ifBlank { e.messages.lastOrNull()?.text.orEmpty() }
                            if (text.isNotBlank()) {
                                Text(text, style = MaterialTheme.typography.bodySmall, color = Space.TextFaint, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (c.entries.isEmpty()) {
                    Text("Noch kein Eintrag", style = MaterialTheme.typography.bodySmall, color = Space.TextFaint)
                }
                if (targets.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    GroupTargets(state, c, targets, hoveredGroup)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth()) {
                    actions.forEach { a ->
                        val on = hovered == a
                        val grow = remember(a) { Animatable(1f) }
                        LaunchedEffect(on) { grow.animateTo(if (on) 1.18f else 1f, tween(Motion.FAST_MS, easing = Motion.Emphasized)) }
                        // Equal slots: the orbs sit on one pitch whatever the label length (W9).
                        Column(
                            Modifier
                                .weight(1f)
                                .onGloballyPositioned {
                                    // Screen coordinates: the popup is its own window.
                                    val o = it.positionOnScreen()
                                    state.actionBounds[a] = Rect(o, androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat()))
                                }
                                .graphicsLayer { scaleX = grow.value; scaleY = grow.value },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .size(52.dp)
                                    .then(if (on) Modifier.glow(Space.Violet, 16.dp, CircleShape, 0.6f) else Modifier)
                                    .background(if (on) Space.Violet.copy(alpha = 0.35f) else Space.Panel, CircleShape)
                                    .border(1.dp, if (on) Space.Violet else Space.Line, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(a.icon, a.label, tint = if (on) Space.Text else Space.TextDim)
                            }
                            Text(a.label, style = MaterialTheme.typography.labelSmall, color = if (on) Space.Text else Space.TextFaint, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * PLAN 9 E20: the contact rides on the finger – sliding onto a group chip and letting go puts
 * them in (or takes them out). Members show filled; the hovered chip lights in its colour.
 */
@Composable
private fun GroupTargets(state: PreviewState, c: Contact, targets: List<io.github.thelony97.orbit.core.model.Group>, hovered: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        targets.forEach { g ->
            val color = androidx.compose.ui.graphics.Color(g.color)
            val member = g.id in c.groupIds
            val on = hovered == g.id
            val grow = remember(g.id) { Animatable(1f) }
            LaunchedEffect(on) { grow.animateTo(if (on) 1.12f else 1f, tween(Motion.FAST_MS, easing = Motion.Emphasized)) }
            Row(
                Modifier
                    .weight(1f)
                    .onGloballyPositioned {
                        val o = it.positionOnScreen()
                        state.groupBounds[g.id] = Rect(o, androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat()))
                    }
                    .graphicsLayer { scaleX = grow.value; scaleY = grow.value }
                    .height(34.dp)
                    .then(if (on) Modifier.glow(color, 12.dp, RoundedCornerShape(50), 0.5f) else Modifier)
                    .background(if (member || on) color.copy(alpha = if (on) 0.35f else 0.18f) else Space.Panel, RoundedCornerShape(50))
                    .border(1.dp, if (member || on) color.copy(alpha = 0.8f) else Space.Line, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(7.dp).background(color, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(g.name, style = MaterialTheme.typography.labelMedium, color = if (member || on) Space.Text else Space.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
