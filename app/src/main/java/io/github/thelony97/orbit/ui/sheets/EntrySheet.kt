package io.github.thelony97.orbit.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import io.github.thelony97.orbit.ui.components.GhostButton
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.SpaceField
import io.github.thelony97.orbit.ui.components.entryIcon
import io.github.thelony97.orbit.ui.theme.Space
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Space.PanelHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLargeEmphasized, color = Space.Text)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                types.forEach { t ->
                    SpaceChip(t.name, typeId == t.id, { typeId = t.id }, leading = entryIcon(t.icon))
                }
            }

            if (!fromSystem) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SpaceChip(
                        if (date == LocalDate.now(zone())) "Heute" else Fmt.weekdayDate(date),
                        false, { pickDate = true }, leading = Icons.Rounded.CalendarMonth,
                    )
                    SpaceChip("%02d:%02d".format(time.hour, time.minute), false, { pickTime = true }, leading = Icons.Rounded.Schedule)
                }
            } else {
                Text(Fmt.dateTime(initial!!.at), style = MaterialTheme.typography.labelLarge, color = Space.TextDim)
            }

            SpaceField(note, "Notiz", singleLine = false, placeholder = "Worüber habt ihr gesprochen?")

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onDelete != null) {
                    GhostButton("Löschen", { onDelete(); onDismiss() }, icon = Icons.Rounded.Delete, color = Space.Coral)
                }
                Spacer(Modifier.weight(1f))
                GlowButton("Speichern", {
                    val at = LocalDateTime.of(date, time).atZone(zone()).toInstant().toEpochMilli()
                    val text = note.text.toString().trim()
                    val entry = if (initial != null) {
                        initial.copy(typeId = typeId, at = if (fromSystem) initial.at else at, note = text)
                    } else {
                        Entry(id = newId(), typeId = typeId, at = at, note = text)
                    }
                    onSave(entry)
                    onDismiss()
                }, icon = Icons.Rounded.Check)
            }
        }
    }

    if (pickDate) DateDialog(initial = date, onDismiss = { pickDate = false }, onPick = { date = it })
    if (pickTime) TimeDialog(initial = time, onDismiss = { pickTime = false }, onPick = { time = it })
}
