package io.github.thelony97.orbit.ui.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onPlaced
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import io.github.thelony97.orbit.core.designsystem.Motion
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.data.CheckItem
import io.github.thelony97.orbit.data.Entry
import io.github.thelony97.orbit.data.EntrySource
import io.github.thelony97.orbit.data.EntryType
import io.github.thelony97.orbit.domain.toLocalDate
import io.github.thelony97.orbit.domain.today
import io.github.thelony97.orbit.ui.components.CheckDot
import io.github.thelony97.orbit.ui.components.OrbButton
import io.github.thelony97.orbit.ui.components.SpaceSearchField
import io.github.thelony97.orbit.ui.components.entryIcon
import io.github.thelony97.orbit.ui.components.glass
import io.github.thelony97.orbit.ui.theme.Space
import io.github.thelony97.orbit.util.Fmt
import java.time.temporal.ChronoUnit

/** Quick action: glass orb with a tiny label underneath. */
@Composable
fun ActionOrb(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    accent: androidx.compose.ui.graphics.Color? = null,
) {
    Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        OrbButton(icon, label, onClick, enabled = enabled, size = 46.dp, accent = accent, onLongClick = onLongClick)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) Space.TextDim else Space.TextFaint,
            maxLines = 1,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

/** A checklist with an inline "add" field, used for topics and gift ideas. */
@Composable
fun Checklist(
    items: List<CheckItem>,
    placeholder: String,
    doneLabel: String,
    onAdd: (String) -> Unit,
    onToggle: (CheckItem) -> Unit,
    onDelete: (CheckItem) -> Unit,
    onClearDone: () -> Unit,
    autoFocus: Boolean = false,
    /** Themen mit Datum (PLAN 3.3): the day picked for a topic, null = taken away. Null = no dates here. */
    onDate: ((CheckItem, java.time.LocalDate?) -> Unit)? = null,
) {
    val input = rememberTextFieldState()
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    // Opened from "+ Thema" / "+ Geschenkidee": straight into typing.
    androidx.compose.runtime.LaunchedEffect(autoFocus) {
        if (autoFocus) {
            androidx.compose.runtime.withFrameNanos { }
            runCatching { focus.requestFocus() }
        }
    }
    var showDone by remember { mutableStateOf(false) }
    val open = items.filterNot { it.done }
    val done = items.filter { it.done }
    fun submit() {
        val text = input.text.toString().trim()
        if (text.isNotEmpty()) {
            onAdd(text)
            input.clearText()
        }
    }
    Column(Modifier.animateContentSize()) {
        open.forEach { item -> CheckRow(item, onToggle, onDelete, onDate) }
        SpaceSearchField(
            state = input,
            placeholder = placeholder,
            leading = Icons.Rounded.Add,
            onSubmit = { submit() },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).focusRequester(focus),
            trailing = if (input.text.isNotBlank()) {
                {
                    Text(
                        "OK",
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = Space.Violet,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { submit() }.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            } else {
                null
            },
        )
        if (done.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    (if (showDone) "▾ " else "▸ ") + "${done.size} $doneLabel",
                    style = MaterialTheme.typography.labelLarge,
                    color = Space.TextDim,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { showDone = !showDone }.padding(horizontal = 6.dp, vertical = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                if (showDone) {
                    Text(
                        "entfernen",
                        style = MaterialTheme.typography.labelLarge,
                        color = Space.Coral,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClearDone).padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
            }
            if (showDone) done.forEach { item -> CheckRow(item, onToggle, onDelete) }
        }
    }
}

@Composable
private fun CheckRow(
    item: CheckItem,
    onToggle: (CheckItem) -> Unit,
    onDelete: (CheckItem) -> Unit,
    onDate: ((CheckItem, java.time.LocalDate?) -> Unit)? = null,
) {
    var picking by remember { mutableStateOf(false) }
    val day = io.github.thelony97.orbit.core.domain.TopicPlan.dayOf(item)
    val haptics = io.github.thelony97.orbit.core.designsystem.rememberHaptics()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            // Langdruck = Datum (PLAN 3.3 „Themen optional mit Datum“): no icon on every row (W5).
            .then(
                if (onDate != null && !item.done) Modifier.pointerInput(item.id) {
                    detectTapGestures(onLongPress = {
                        haptics.perform(io.github.thelony97.orbit.core.designsystem.Haptic.Confirm)
                        picking = true
                    })
                } else Modifier,
            )
            .padding(vertical = 4.dp),
    ) {
        CheckDot(item.done, { onToggle(item) })
        Spacer(Modifier.width(10.dp))
        Text(
            item.text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            color = if (item.done) Space.TextFaint else Space.Text,
        )
        if (onDate != null && !item.done && day != null) {
            // The day it reminds on; past days stay visible in coral until done.
            val past = day.isBefore(io.github.thelony97.orbit.domain.today())
            Text(
                io.github.thelony97.orbit.util.Fmt.shortDate(day),
                style = MaterialTheme.typography.labelMedium,
                color = if (past) Space.Coral else Space.Violet,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { picking = true }.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Icon(
            Icons.Rounded.Close, "Löschen",
            tint = Space.TextFaint,
            modifier = Modifier.size(26.dp).clip(CircleShape).clickable { onDelete(item) }.padding(5.dp),
        )
    }
    if (picking && onDate != null) {
        io.github.thelony97.orbit.ui.sheets.DateDialog(
            day ?: io.github.thelony97.orbit.domain.today().plusDays(1),
            onDismiss = { picking = false },
            onPick = { onDate(item, it) },
            onClear = if (day != null) ({ onDate(item, null) }) else null,
            // The morning notification names it; today's has gone out already.
            minDate = io.github.thelony97.orbit.domain.today().plusDays(1),
        )
    }
}

/** Compact two-line info row: value on top, tiny label below. */
@Composable
fun InfoRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    /** For a shared transition on the value (Datumskachel → Anlass, PLAN W3). */
    valueModifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 5.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = Space.Text, modifier = valueModifier)
            if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelMedium, color = Space.TextFaint)
        }
        trailing?.invoke()
    }
}

/**
 * One history entry as a timeline node. With [drawIn] the node pops in and its rail draws down
 * to the next one the first time the row scrolls into view (PLAN 3.3); read in the draw phase.
 */
@Composable
fun EntryRow(entry: Entry, type: EntryType?, last: Boolean, onClick: () -> Unit, drawIn: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    val drawn = remember { androidx.compose.animation.core.Animatable(if (drawIn) 0f else 1f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val days = ChronoUnit.DAYS.between(entry.at.toLocalDate(), today())
    val accent = when (entry.source) {
        EntrySource.CALL_LOG -> Space.Teal
        EntrySource.SMS -> Space.Blue
        EntrySource.MEETING -> Space.Orange
        else -> Space.Violet
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            // Start on the first placement, not on composition: prefetched rows are composed
            // before they are on screen and would otherwise finish drawing out of sight.
            .onPlaced {
                if (drawn.value < 1f && !drawn.isRunning) {
                    scope.launch { drawn.animateTo(1f, androidx.compose.animation.core.tween(Motion.EMPHASIZED_MS, easing = Motion.Emphasized)) }
                }
            }
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp),
    ) {
        // Timeline rail
        Column(Modifier.width(30.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(30.dp)
                    .graphicsLayer {
                        val p = (drawn.value * 2.5f).coerceAtMost(1f)
                        scaleX = 0.6f + 0.4f * p
                        scaleY = 0.6f + 0.4f * p
                        alpha = p
                    }
                    .glass(CircleShape, tint = Space.PanelHigh, border = accent.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(entryIcon(type?.icon ?: "star"), null, tint = accent, modifier = Modifier.size(15.dp))
            }
            if (!last) {
                Box(
                    Modifier.width(2.dp).weight(1f).drawBehind {
                        val end = size.height * drawn.value
                        // Starts in the node's colour and fades into the quiet line colour.
                        drawLine(
                            Brush.verticalGradient(listOf(accent.copy(alpha = 0.5f), Space.Line), endY = size.height),
                            Offset(size.width / 2, 0f), Offset(size.width / 2, end),
                            strokeWidth = 1.dp.toPx(),
                        )
                    },
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(top = 6.dp, bottom = 12.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Title and arrow take the row; "vor …" sits in one right-aligned column (W9).
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildString {
                            append(type?.name ?: "Eintrag")
                            entry.durationSec?.let { append(" · ").append(Fmt.duration(it)) }
                            if (entry.source == EntrySource.SMS) append(" · ${entry.messages.size} SMS")
                        },
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = Space.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    when (entry.incoming) {
                        true -> Icon(Icons.AutoMirrored.Rounded.CallReceived, "eingehend", Modifier.padding(start = 4.dp).size(13.dp), tint = accent)
                        false -> Icon(Icons.AutoMirrored.Rounded.CallMade, "ausgehend", Modifier.padding(start = 4.dp).size(13.dp), tint = accent)
                        null -> Unit
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    Fmt.short(days),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = Space.TextFaint,
                )
            }
            // Date once, with the year when it is not this year, then the time.
            val d = entry.at.toLocalDate()
            Text(
                io.github.thelony97.orbit.core.domain.AtFormat.weekdayDate(d) + (if (d.year != today().year) " ${d.year}" else "") + " · " + Fmt.time(entry.at),
                style = MaterialTheme.typography.labelSmall,
                color = Space.TextFaint,
            )
            if (entry.note.isNotBlank()) {
                Text(
                    entry.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Space.Text,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (entry.messages.isNotEmpty()) {
                val shown = if (expanded) entry.messages else entry.messages.takeLast(2)
                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    shown.forEach { m ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.incoming) Arrangement.Start else Arrangement.End) {
                            Text(
                                m.text,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (m.incoming) Space.PanelHigh else Space.Violet.copy(alpha = 0.22f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = Space.Text,
                                maxLines = if (expanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (entry.messages.size > 2 || entry.messages.any { it.text.length > 120 }) {
                    Text(
                        if (expanded) "weniger" else "alle ${entry.messages.size} Nachrichten",
                        style = MaterialTheme.typography.labelMedium,
                        color = Space.Violet,
                        modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).clickable { expanded = !expanded }.padding(4.dp),
                    )
                }
            }
        }
    }
}

