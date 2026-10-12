package io.github.thelony97.orbit.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.data.Entry
import io.github.thelony97.orbit.data.EntrySource
import io.github.thelony97.orbit.data.EntryType
import io.github.thelony97.orbit.data.TypeIds
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.domain.zone
import io.github.thelony97.orbit.ui.components.entryIcon
import io.github.thelony97.orbit.util.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** Add or edit a history entry. */
@Composable
fun EntrySheet(
    title: String,
    types: List<EntryType>,
    initial: Entry?,
    defaultType: String = TypeIds.MEET,
    onSave: (Entry) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val start = initial?.at?.let { Instant.ofEpochMilli(it).atZone(zone()).toLocalDateTime() }
        ?: LocalDateTime.now(zone()).withSecond(0).withNano(0)
    var typeId by remember { mutableStateOf(initial?.typeId ?: defaultType) }
    var date by remember { mutableStateOf(start.toLocalDate()) }
    var time by remember { mutableStateOf(start.toLocalTime()) }
    val note = rememberTextFieldState(initial?.note.orEmpty())
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val fromSystem = initial != null && initial.source != EntrySource.MANUAL

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmallEmphasized)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                types.forEach { t ->
                    FilterChip(
                        selected = typeId == t.id,
                        onClick = { typeId = t.id },
                        label = { Text(t.name) },
                        leadingIcon = { Icon(entryIcon(t.icon), null, Modifier.size(18.dp)) },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { pickDate = true },
                    label = { Text(if (date == LocalDate.now(zone())) "Heute" else Fmt.weekdayDate(date)) },
                    leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp)) },
                    enabled = !fromSystem,
                )
                AssistChip(
                    onClick = { pickTime = true },
                    label = { Text("%02d:%02d".format(time.hour, time.minute)) },
                    leadingIcon = { Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp)) },
                    enabled = !fromSystem,
                )
            }

            OutlinedTextField(
                state = note,
                label = { Text("Notiz") },
                placeholder = { Text("Worüber habt ihr gesprochen?") },
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 8),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onDelete != null) {
                    TextButton(onClick = { onDelete(); onDismiss() }) {
                        Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Löschen")
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
                Spacer(Modifier.size(8.dp))
                Button(onClick = {
                    val at = LocalDateTime.of(date, time).atZone(zone()).toInstant().toEpochMilli()
                    val text = note.text.toString().trim()
                    val entry = if (initial != null) {
                        initial.copy(typeId = typeId, at = if (fromSystem) initial.at else at, note = text)
                    } else {
                        Entry(id = newId(), typeId = typeId, at = at, note = text)
                    }
                    onSave(entry)
                    onDismiss()
                }) { Text("Speichern") }
            }
        }
    }

    if (pickDate) DateDialog(initial = date, onDismiss = { pickDate = false }, onPick = { date = it })
    if (pickTime) TimeDialog(initial = time, onDismiss = { pickTime = false }, onPick = { time = it })
}
