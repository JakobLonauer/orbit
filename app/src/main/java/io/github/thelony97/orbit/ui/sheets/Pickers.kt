package io.github.thelony97.orbit.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.data.DayOfYear
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.utcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
fun DateDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
    /** Shown as „Entfernen“ when the value can be taken away again. */
    onClear: (() -> Unit)? = null,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.utcMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(it.utcDate()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                if (onClear != null) TextButton(onClick = { onClear(); onDismiss() }) { Text("Entfernen", color = io.github.thelony97.orbit.ui.theme.Space.Coral) }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    ) {
        DatePicker(state = state)
    }
}

@Composable
fun TimeDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onPick: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(LocalTime.of(state.hour, state.minute))
                onDismiss()
            }) { Text("OK") }
        },
        title = { Text("Uhrzeit") },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    ) {
        TimePicker(state = state)
    }
}

/** Birthday / anniversary picker: a date, optionally without year. */
@Composable
fun DayOfYearDialog(
    initial: DayOfYear?,
    onDismiss: () -> Unit,
    onPick: (DayOfYear) -> Unit,
) {
    val start = initial?.let {
        runCatching { LocalDate.of(it.year ?: 2000, it.month, it.day) }.getOrNull()
    } ?: LocalDate.of(1995, 1, 1)
    val state = rememberDatePickerState(
        initialSelectedDateMillis = start.utcMillis(),
        yearRange = 1900..LocalDate.now().year + 50,
    )
    var noYear by remember { mutableStateOf(initial != null && initial.year == null) }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.utcDate()?.let { d ->
                    onPick(DayOfYear(d.dayOfMonth, d.monthValue, if (noYear) null else d.year))
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Jahr unbekannt", style = MaterialTheme.typography.bodyLarge)
            Switch(checked = noYear, onCheckedChange = { noYear = it })
        }
        DatePicker(state = state, showModeToggle = true)
    }
}
