package io.github.thelony97.orbit.ui.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.data.CheckItem
import io.github.thelony97.orbit.data.Entry
import io.github.thelony97.orbit.data.EntrySource
import io.github.thelony97.orbit.data.EntryType
import io.github.thelony97.orbit.domain.today
import io.github.thelony97.orbit.domain.toLocalDate
import io.github.thelony97.orbit.ui.components.entryIcon
import io.github.thelony97.orbit.util.Fmt
import java.time.temporal.ChronoUnit

/** Square-ish button that morphs its corners while pressed. */
@Composable
fun RowScope.ActionTile(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) 14.dp else 28.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "corner",
    )
    val container = if (enabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (enabled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.outline
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 60.dp, height = 56.dp)
                .clip(RoundedCornerShape(corner))
                .background(container)
                .combinedClickable(
                    enabled = enabled,
                    interactionSource = interaction,
                    onLongClick = onLongClick,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = content)
        }
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
) {
    val input = rememberTextFieldState()
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
        open.forEach { item -> CheckRow(item, onToggle, onDelete) }
        TextField(
            state = input,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 4.dp),
            placeholder = { Text(placeholder) },
            lineLimits = TextFieldLineLimits.SingleLine,
            onKeyboardAction = { submit() },
            trailingIcon = {
                IconButton(onClick = { submit() }, enabled = input.text.isNotBlank()) { Icon(Icons.Rounded.Add, "Hinzufügen") }
            },
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        )
        if (done.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showDone = !showDone }) {
                    Text("${done.size} $doneLabel")
                    Icon(if (showDone) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                }
                Spacer(Modifier.weight(1f))
                if (showDone) TextButton(onClick = onClearDone) { Text("Entfernen") }
            }
            if (showDone) done.forEach { item -> CheckRow(item, onToggle, onDelete) }
        }
    }
}

@Composable
private fun CheckRow(item: CheckItem, onToggle: (CheckItem) -> Unit, onDelete: (CheckItem) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = item.done, onCheckedChange = { onToggle(item) })
        Text(
            item.text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = { onDelete(item) }) {
            Icon(Icons.Rounded.Close, "Löschen", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

/** One history entry in the timeline. */
@Composable
fun EntryRow(entry: Entry, type: EntryType?, onClick: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val days = ChronoUnit.DAYS.between(entry.at.toLocalDate(), today())
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
    ) {
        Row(Modifier.padding(14.dp)) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    entryIcon(type?.icon ?: "star"), null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).animateContentSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildString {
                            append(type?.name ?: "Eintrag")
                            entry.durationSec?.let { append(" · ").append(Fmt.duration(it)) }
                            if (entry.source == EntrySource.SMS) append(" · SMS")
                        },
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    when (entry.incoming) {
                        true -> Icon(Icons.Rounded.CallReceived, "eingehend", Modifier.padding(start = 6.dp).size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        false -> Icon(Icons.Rounded.CallMade, "ausgehend", Modifier.padding(start = 6.dp).size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        null -> Unit
                    }
                }
                Text(
                    "${Fmt.ago(days).replaceFirstChar { it.uppercase() }} · ${Fmt.dateTime(entry.at)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (entry.note.isNotBlank()) {
                    Text(
                        entry.note,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (entry.messages.isNotEmpty()) {
                    val shown = if (expanded) entry.messages else entry.messages.takeLast(2)
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        shown.forEach { m ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.incoming) Arrangement.Start else Arrangement.End) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (m.incoming) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        m.text,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    if (entry.messages.size > 2 || entry.messages.any { it.text.length > 120 }) {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "Weniger" else "Alle ${entry.messages.size} Nachrichten")
                        }
                    }
                }
            }
        }
    }
}

fun dueLine(daysUntilDue: Long): String = when {
    daysUntilDue < 0 -> "Fällig seit ${-daysUntilDue} ${if (daysUntilDue == -1L) "Tag" else "Tagen"}"
    daysUntilDue == 0L -> "Heute fällig"
    else -> "Nächstes Mal ${Fmt.until(daysUntilDue)}"
}
