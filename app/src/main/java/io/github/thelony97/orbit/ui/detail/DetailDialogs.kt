package io.github.thelony97.orbit.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.DayOfYear
import io.github.thelony97.orbit.data.Defaults
import io.github.thelony97.orbit.data.LabeledValue
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.zone
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.contactColor
import io.github.thelony97.orbit.ui.sheets.DateDialog
import io.github.thelony97.orbit.ui.sheets.DayOfYearDialog
import io.github.thelony97.orbit.ui.sheets.TimeDialog
import io.github.thelony97.orbit.util.Fmt
import java.time.LocalDateTime
import java.time.LocalTime

val rhythmPresets = listOf(7, 14, 30, 60, 90, 180, 365)

/** null = follow the group rhythm. */
@Composable
fun RhythmDialog(current: Int?, groupRhythm: Int?, onDismiss: () -> Unit, onPick: (Int?) -> Unit) {
    var selected by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wie oft melden?") },
        text = {
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable { selected = null }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == null, onClick = { selected = null })
                    Text(
                        if (groupRhythm != null) "Wie Gruppe (${Fmt.rhythm(groupRhythm)})" else "Wie Gruppe (keine Erinnerung)",
                    )
                }
                rhythmPresets.forEach { days ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = days }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == days, onClick = { selected = days })
                        Text(Fmt.rhythm(days).replaceFirstChar { it.uppercase() })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(selected); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

data class MeetingDraft(val start: LocalDateTime, val durationMin: Int, val place: String)

@Composable
fun MeetingDialog(name: String, onDismiss: () -> Unit, onPlan: (MeetingDraft) -> Unit) {
    val now = LocalDateTime.now(zone())
    var date by remember { mutableStateOf(now.toLocalDate().plusDays(1)) }
    var time by remember { mutableStateOf(LocalTime.of(18, 0)) }
    var duration by remember { mutableStateOf(120) }
    val place = rememberTextFieldState()
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Treffen mit $name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { pickDate = true },
                        label = { Text(Fmt.weekdayDate(date)) },
                        leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp)) },
                    )
                    AssistChip(
                        onClick = { pickTime = true },
                        label = { Text("%02d:%02d".format(time.hour, time.minute)) },
                        leadingIcon = { Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp)) },
                    )
                }
                Text("Dauer", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(60 to "1 h", 120 to "2 h", 180 to "3 h", 300 to "5 h").forEach { (min, label) ->
                        FilterChip(selected = duration == min, onClick = { duration = min }, label = { Text(label) })
                    }
                }
                OutlinedTextField(
                    state = place,
                    label = { Text("Ort (optional)") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Danach öffnet sich dein Kalender zum Speichern. Am Tag nach dem Treffen fragt Orbit nach.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onPlan(MeetingDraft(LocalDateTime.of(date, time), duration, place.text.toString().trim()))
                onDismiss()
            }) { Text("Planen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
    if (pickDate) DateDialog(initial = date, onDismiss = { pickDate = false }, onPick = { date = it })
    if (pickTime) TimeDialog(initial = time, onDismiss = { pickTime = false }, onPick = { time = it })
}

@Composable
fun RelationDialog(
    contact: Contact,
    candidates: List<Contact>,
    idx: Index,
    lastFirst: Boolean,
    onDismiss: () -> Unit,
    onPick: (otherId: String, kind: String) -> Unit,
) {
    var kind by remember { mutableStateOf(Defaults.relationKinds.first().key) }
    var other by remember { mutableStateOf<String?>(null) }
    val search = rememberTextFieldState()
    val q = search.text.toString()
    val filtered = candidates.filter { it.id != contact.id && Logic.matches(it, q, idx) }
        .sortedBy { Logic.sortKey(it, lastFirst) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${contact.firstName.ifBlank { Logic.displayName(contact, lastFirst) }} ist …") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Defaults.relationKinds.forEach { k ->
                        FilterChip(selected = kind == k.key, onClick = { kind = k.key }, label = { Text(k.forward) })
                    }
                }
                Text("… von", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    state = search,
                    placeholder = { Text("Person suchen") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(filtered, key = { it.id }) { c ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { other = c.id }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            RadioButton(selected = other == c.id, onClick = { other = c.id })
                            Avatar(c, contactColor(c, idx), 32.dp)
                            Text(Logic.displayName(c, lastFirst))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = other != null, onClick = {
                other?.let { onPick(it, kind) }
                onDismiss()
            }) { Text("Verknüpfen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun AnniversaryDialog(onDismiss: () -> Unit, onPick: (String, DayOfYear) -> Unit) {
    val label = rememberTextFieldState()
    var date by remember { mutableStateOf<DayOfYear?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jahrestag hinzufügen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    state = label,
                    label = { Text("Bezeichnung") },
                    placeholder = { Text("z. B. Hochzeitstag, Namenstag") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Hochzeitstag", "Namenstag", "Jahrestag", "Kennenlerntag").forEach { s ->
                        AssistChip(onClick = {
                            label.edit { replace(0, length, s) }
                        }, label = { Text(s) })
                    }
                }
                AssistChip(
                    onClick = { pickDate = true },
                    label = { Text(date?.let { Fmt.dayOfYear(it) } ?: "Datum wählen") },
                    leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = date != null && label.text.isNotBlank(),
                onClick = {
                    date?.let { onPick(label.text.toString().trim(), it) }
                    onDismiss()
                },
            ) { Text("Hinzufügen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
    if (pickDate) DayOfYearDialog(initial = date, onDismiss = { pickDate = false }, onPick = { date = it })
}

/** Pick one of several numbers / addresses; the star marks the default phone number. */
@Composable
fun ValueChooserDialog(
    title: String,
    values: List<LabeledValue>,
    defaultIndex: Int?,
    onDismiss: () -> Unit,
    onSetDefault: ((Int) -> Unit)?,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                values.forEachIndexed { i, v ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(v.value); onDismiss() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(v.value, style = MaterialTheme.typography.bodyLarge)
                            Text(v.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (onSetDefault != null) {
                            IconButton(onClick = { onSetDefault(i) }) {
                                Icon(
                                    if (defaultIndex == i) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                    "Als Standard",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}
