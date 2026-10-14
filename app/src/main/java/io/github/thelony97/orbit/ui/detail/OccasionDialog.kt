package io.github.thelony97.orbit.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.designsystem.Corners
import io.github.thelony97.orbit.core.model.OccasionKind
import io.github.thelony97.orbit.core.model.Repeat
import io.github.thelony97.orbit.data.Anniversary
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.DayOfYear
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.sheets.DayOfYearDialog
import io.github.thelony97.orbit.ui.theme.Space
import io.github.thelony97.orbit.util.Fmt

private val REMINDERS = listOf(0 to "Am Tag", 1 to "1 Tag", 3 to "3 Tage", 7 to "7 Tage", 14 to "14 Tage")

/**
 * Anlass hinzufügen / bearbeiten (PLAN 3.5): Art, Datum (Jahr optional), jährlich oder einmalig,
 * Erinnerungen (mehrfach, keine = keine), gemeinsam mit einem zweiten Kontakt, Notiz, Anpinnen.
 * A birthday may only be chosen while the contact has none (or when editing it).
 */
@Composable
fun OccasionDialog(
    initial: Anniversary?,
    people: List<Contact>,
    allowBirthday: Boolean,
    lastFirst: Boolean,
    onDismiss: () -> Unit,
    onSave: (Anniversary) -> Unit,
) {
    var kind by remember { mutableStateOf(initial?.kind ?: OccasionKind.WEDDING) }
    val label = rememberTextFieldState(initial?.label.orEmpty())
    var date by remember { mutableStateOf<DayOfYear?>(initial?.date) }
    var repeat by remember { mutableStateOf(initial?.repeat ?: Repeat.YEARLY) }
    var reminders by remember { mutableStateOf(initial?.reminders?.toSet() ?: setOf(0)) }
    var sharedWith by remember { mutableStateOf(initial?.sharedWith) }
    var pinned by remember { mutableStateOf(initial?.pinned ?: false) }
    val note = rememberTextFieldState(initial?.note.orEmpty())
    var pickDate by remember { mutableStateOf(false) }
    var pickPerson by remember { mutableStateOf(false) }
    val search = rememberTextFieldState()
    val kinds = OccasionKind.entries.filter { it != OccasionKind.BIRTHDAY || allowBirthday || initial?.kind == OccasionKind.BIRTHDAY }
    // A one-off occasion needs its year; a custom one needs a name.
    val valid = date != null && (repeat == Repeat.YEARLY || date?.year != null) && (kind != OccasionKind.CUSTOM || label.text.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Space.PanelHigh,
        title = { Text(if (initial == null) "Anlass" else initial.title) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    kinds.forEach { k ->
                        SpaceChip(k.label, kind == k, {
                            kind = k
                            // A birthday reminds a week ahead by default (with the gift ideas).
                            if (initial == null) reminders = if (k == OccasionKind.BIRTHDAY) Anniversary.BIRTHDAY_REMINDERS.toSet() else setOf(0)
                        })
                    }
                }
                if (kind == OccasionKind.CUSTOM || label.text.isNotEmpty()) {
                    OutlinedTextField(
                        state = label,
                        label = { Text("Bezeichnung") },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = Corners.Row,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Einmalig without a year: the date chip turns coral – it needs the year (no hint text, W7).
                    val needsYear = repeat == Repeat.ONCE && date != null && date?.year == null
                    SpaceChip(date?.let { Fmt.dayOfYear(it) } ?: "Datum", date != null, { pickDate = true }, leading = Icons.Rounded.CalendarMonth, accent = if (needsYear) Space.Coral else Space.Violet)
                    SpaceChip("Jährlich", repeat == Repeat.YEARLY, { repeat = Repeat.YEARLY })
                    SpaceChip("Einmalig", repeat == Repeat.ONCE, { repeat = Repeat.ONCE })
                }
                Text("Erinnern", style = MaterialTheme.typography.labelLarge, color = Space.TextDim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    REMINDERS.forEach { (d, text) ->
                        val on = d in reminders
                        SpaceChip(text, on, { reminders = if (on) reminders - d else reminders + d })
                    }
                }
                // Gemeinsam mit (stored once, shown at both, one reminder).
                if (kind != OccasionKind.BIRTHDAY) {
                    val partner = sharedWith?.let { id -> people.firstOrNull { it.id == id } }
                    if (partner != null) {
                        SpaceChip("mit " + Logic.displayName(partner, lastFirst), true, { sharedWith = null }, leading = Icons.Rounded.Close)
                    } else if (!pickPerson) {
                        SpaceChip("Gemeinsam mit …", false, { pickPerson = true })
                    } else {
                        OutlinedTextField(
                            state = search,
                            placeholder = { Text("Name") },
                            lineLimits = TextFieldLineLimits.SingleLine,
                            shape = Corners.Row,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val q = search.text.toString().trim().lowercase()
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            people.filter { q.isEmpty() || Logic.displayName(it, lastFirst).lowercase().contains(q) }.take(6).forEach { p ->
                                SpaceChip(Logic.displayName(p, lastFirst), false, { sharedWith = p.id; pickPerson = false })
                            }
                        }
                    }
                }
                OutlinedTextField(
                    state = note,
                    label = { Text("Notiz") },
                    lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = Corners.Row,
                    modifier = Modifier.fillMaxWidth(),
                )
                SpaceChip("Anpinnen", pinned, { pinned = !pinned }, leading = Icons.Rounded.PushPin)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val d = date ?: return@TextButton
                val title = label.text.toString().trim()
                onSave(
                    Anniversary(
                        id = initial?.id ?: newId(),
                        kind = kind,
                        // The kind's own name is no label („Hochzeitstag“ stays a plain wedding day).
                        label = if (title.equals(kind.label, ignoreCase = true)) "" else title,
                        date = d,
                        repeat = repeat,
                        pinned = pinned,
                        reminders = reminders.sorted(),
                        sharedWith = if (kind == OccasionKind.BIRTHDAY) null else sharedWith,
                        note = note.text.toString().trim(),
                    ),
                )
                onDismiss()
            }) { Text(if (initial == null) "Hinzufügen" else "Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
    if (pickDate) DayOfYearDialog(initial = date, onDismiss = { pickDate = false }, onPick = { date = it })
}
