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
            io.github.thelony97.orbit.ui.components.DialogConfirm(onClick = {
                onPlan(MeetingDraft(LocalDateTime.of(date, time), duration, place.text.toString().trim()))
                onDismiss()
            }) { Text("Planen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen", color = io.github.thelony97.orbit.ui.theme.Space.TextDim) } },
    )
    if (pickDate) DateDialog(initial = date, onDismiss = { pickDate = false }, onPick = { date = it })
    if (pickTime) TimeDialog(initial = time, onDismiss = { pickTime = false }, onPick = { time = it })
}

/** One choice in the relation dialog. [reversed] = the relation is stored from the other side. */
data class RelationOption(val label: String, val kind: String, val reversed: Boolean, val toOrg: Boolean)

/** Human relations only: organisations are Zuordnungen since R4 ([AssignDialog]). */
fun relationOptions(contact: Contact): List<RelationOption> {
    val personKinds = Defaults.relationKinds.filter { !it.toOrg }
    return personKinds.flatMap { k ->
        listOf(RelationOption(k.forward, k.key, false, false)) +
            if (k.backward != k.forward) listOf(RelationOption(k.backward, k.key, true, false)) else emptyList()
    }
}

@Composable
fun RelationDialog(
    contact: Contact,
    candidates: List<Contact>,
    idx: Index,
    lastFirst: Boolean,
    onDismiss: () -> Unit,
    onPick: (otherId: String, kind: String, reversed: Boolean) -> Unit,
) {
    val options = remember(contact.kind) { relationOptions(contact) }
    var option by remember { mutableStateOf(options.first()) }
    var other by remember { mutableStateOf<String?>(null) }
    val search = rememberTextFieldState()
    val q = search.text.toString()
    // Person ↔ person links pick people, person → organisation links pick organisations.
    val wantOrg = option.toOrg && !contact.isOrg
    val filtered = candidates.filter { it.id != contact.id && it.isOrg == wantOrg && Logic.matches(it, q, idx) }
        .sortedWith(compareByDescending<Contact> { it.isMe }.thenBy { Logic.sortKey(it, lastFirst) })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${Logic.shortName(contact)} ist …") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    options.forEach { o ->
                        FilterChip(
                            selected = option == o,
                            onClick = {
                                if (o.toOrg != option.toOrg) other = null
                                option = o
                            },
                            label = { Text(o.label) },
                        )
                    }
                }
                Text("… von", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    state = search,
                    placeholder = { Text(if (wantOrg) "Organisation suchen" else "Person suchen") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                if (wantOrg) "Keine passende Organisation – lege sie über + → „Neue Organisation“ an." else "Niemand gefunden.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
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
            io.github.thelony97.orbit.ui.components.DialogConfirm(enabled = other != null, onClick = {
                other?.let { onPick(it, option.kind, option.reversed) }
                onDismiss()
            }) { Text("Verknüpfen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen", color = io.github.thelony97.orbit.ui.theme.Space.TextDim) } },
    )
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

/**
 * Zuordnung (PLAN 3.6): a person to an organisation with a role – from the person page it picks
 * the organisation, from the organisation page the person. „Eigene“ takes a free name.
 */
@Composable
fun AssignDialog(
    contact: Contact,
    candidates: List<Contact>,
    idx: Index,
    lastFirst: Boolean,
    onDismiss: () -> Unit,
    onPick: (otherId: String, role: String, label: String) -> Unit,
) {
    val roles = io.github.thelony97.orbit.core.model.AssignmentRoles.all
    var role by remember { mutableStateOf(roles.first().key) }
    val custom = rememberTextFieldState()
    var other by remember { mutableStateOf<String?>(null) }
    val search = rememberTextFieldState()
    val q = search.text.toString()
    val wantOrg = !contact.isOrg
    val filtered = candidates.filter { it.id != contact.id && it.isOrg == wantOrg && Logic.matches(it, q, idx) }
        .sortedWith(compareByDescending<Contact> { it.isMe }.thenBy { Logic.sortKey(it, lastFirst) })
    val isCustom = role == io.github.thelony97.orbit.core.model.AssignmentRoles.CUSTOM
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = io.github.thelony97.orbit.ui.theme.Space.PanelHigh,
        title = { Text(Logic.shortName(contact)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    roles.forEach { r ->
                        io.github.thelony97.orbit.ui.components.SpaceChip(
                            io.github.thelony97.orbit.core.domain.Assignments.label(r), role == r.key, { role = r.key },
                            accent = io.github.thelony97.orbit.ui.theme.Space.Blue,
                        )
                    }
                    io.github.thelony97.orbit.ui.components.SpaceChip(
                        "Eigene", isCustom, { role = io.github.thelony97.orbit.core.model.AssignmentRoles.CUSTOM },
                        accent = io.github.thelony97.orbit.ui.theme.Space.Blue,
                    )
                }
                if (isCustom) {
                    OutlinedTextField(
                        state = custom,
                        placeholder = { Text("Bezeichnung") },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    state = search,
                    placeholder = { Text(if (wantOrg) "Organisation" else "Person") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
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
            io.github.thelony97.orbit.ui.components.DialogConfirm(enabled = other != null && (!isCustom || custom.text.isNotBlank()), onClick = {
                other?.let { onPick(it, role, if (isCustom) custom.text.toString().trim() else "") }
                onDismiss()
            }) { Text("Zuordnen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen", color = io.github.thelony97.orbit.ui.theme.Space.TextDim) } },
    )
}
