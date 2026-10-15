package io.github.thelony97.orbit.ui.list

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.github.thelony97.orbit.core.designsystem.Motion
import io.github.thelony97.orbit.core.designsystem.Spacing
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.domain.OrbitLine
import io.github.thelony97.orbit.core.model.BuiltinTypes
import io.github.thelony97.orbit.core.model.HistoryEntry
import io.github.thelony97.orbit.core.model.Meeting
import io.github.thelony97.orbit.data.Mutations
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.zone
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.Undo
import io.github.thelony97.orbit.ui.theme.Space

// Licht-Code (PLAN 9.2): the person in their own colour, never Teal (= läuft) for a mere kind.
private fun OrbitLine.Kind.color() = when (this) {
    OrbitLine.Kind.PERSON -> Space.Violet
    OrbitLine.Kind.TYPE -> Space.Violet
    OrbitLine.Kind.TIME -> Space.Orange
    OrbitLine.Kind.NOTE -> Space.Blue
    OrbitLine.Kind.GROUP, OrbitLine.Kind.FILTER -> Space.TextDim
}

private fun OrbitLine.Kind.icon() = when (this) {
    OrbitLine.Kind.PERSON -> Icons.Rounded.Person
    OrbitLine.Kind.TYPE -> Icons.Rounded.Event
    OrbitLine.Kind.TIME -> Icons.Rounded.Schedule
    OrbitLine.Kind.NOTE -> Icons.AutoMirrored.Rounded.Notes
    OrbitLine.Kind.GROUP -> Icons.Rounded.Tag
    OrbitLine.Kind.FILTER -> Icons.Rounded.FilterAlt
}

/**
 * Orbit-Zeile (PLAN 9 E1 Moment): the recognised parts come loose from the text as chips – each
 * springs in with its own tick.
 */
@Composable
fun OrbitLineChips(
    line: OrbitLine.Line,
    onClick: (OrbitLine.Chip) -> Unit,
    modifier: Modifier = Modifier,
    onKeep: (() -> Unit)? = null,
    landing: Boolean = false,
    personColor: androidx.compose.ui.graphics.Color? = null,
) {
    // Chips present at the first frame come loose one after another (60 ms apart), later ones at once.
    var born by remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(Unit) { born = false }
    val drop = remember { Animatable(0f) }
    LaunchedEffect(landing) { if (landing) drop.animateTo(1f, tween(Motion.MEDIUM_MS, easing = Motion.Emphasized)) else drop.snapTo(0f) }
    Row(
        modifier.fillMaxWidth()
            // Chips running past the edge fade out instead of being cut („Wohnu…“).
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (size.width <= FADE_DP * density) return@drawWithContent
                val fade = (FADE_DP * density) / size.width
                drawRect(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        0f to androidx.compose.ui.graphics.Color.Black,
                        (1f - fade) to androidx.compose.ui.graphics.Color.Black,
                        1f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0f),
                    ),
                    blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                )
            }
            .horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.Gutter).padding(top = Spacing.S)
            .graphicsLayer {
                // Enter: the chips fall into the row below and dissolve.
                translationY = drop.value * 56.dp.toPx()
                alpha = 1f - drop.value
                scaleX = 1f - 0.15f * drop.value
                scaleY = 1f - 0.15f * drop.value
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        line.chips.forEachIndexed { i, chip ->
            key(chip.kind, chip.text) {
                LooseChip(chip, if (born) i * STAGGER_MS else 0L, if (chip.kind == OrbitLine.Kind.PERSON) personColor else null) { onClick(chip) }
            }
        }
        if (onKeep != null) SpaceChip("Merken", false, onKeep, leading = Icons.Rounded.BookmarkAdd, accent = Space.Violet)
    }
}

@Composable
private fun LooseChip(chip: OrbitLine.Chip, delayMs: Long, color: androidx.compose.ui.graphics.Color?, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMs)
        haptics.perform(Haptic.Tick)
        pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
    }
    SpaceChip(
        chip.text, true, onClick,
        modifier = Modifier.graphicsLayer {
            scaleX = 0.6f + 0.4f * pop.value
            scaleY = 0.6f + 0.4f * pop.value
            alpha = pop.value.coerceIn(0f, 1f)
            translationY = (1f - pop.value) * -10.dp.toPx()
        },
        leading = chip.kind.icon(),
        accent = color ?: chip.kind.color(),
    )
}

private const val STAGGER_MS = 60L
private const val FADE_DP = 24f

/**
 * Enter on a captured line: a time ahead → a planned meeting; otherwise an entry (default kind
 * Treffen, default time now). Rückgängig takes it back. Returns the contact's id.
 */
fun commitLine(line: OrbitLine.Line, data: OrbitData): String? {
    val id = line.personId ?: return null
    val c = data.contacts.firstOrNull { it.id == id } ?: return null
    val now = System.currentTimeMillis()
    val at = line.at?.atZone(zone())?.toInstant()?.toEpochMilli() ?: now
    val name = Logic.shortName(c)
    if (at > now + PLAN_AHEAD_MS && (line.typeId == null || line.typeId == BuiltinTypes.MEET)) {
        val m = Meeting(newId(), id, at, at + HOUR_MS, line.note)
        OrbitRepository.edit { d -> Mutations.upsertMeeting(d, m) }
        Undo.offer("Treffen mit $name geplant") { OrbitRepository.edit { d -> d.copy(meetings = d.meetings.filterNot { it.id == m.id }) } }
    } else {
        val e = HistoryEntry(newId(), id, line.typeId ?: BuiltinTypes.MEET, at, note = line.note)
        OrbitRepository.edit { d -> Mutations.addEntry(d, id, e) }
        Undo.offer("Bei $name eingetragen") { OrbitRepository.edit { d -> Mutations.deleteEntry(d, id, e.id) } }
    }
    return id
}

private const val PLAN_AHEAD_MS = 60_000L
private const val HOUR_MS = 3_600_000L
