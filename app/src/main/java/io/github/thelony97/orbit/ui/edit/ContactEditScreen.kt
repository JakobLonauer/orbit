package io.github.thelony97.orbit.ui.edit

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.Defaults
import io.github.thelony97.orbit.data.LabeledValue
import io.github.thelony97.orbit.data.Mutations
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.security.AppLock
import io.github.thelony97.orbit.sync.Sync
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.Photos
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.ConfirmDialog
import io.github.thelony97.orbit.ui.components.PhotoCache
import io.github.thelony97.orbit.ui.components.contactColor
import io.github.thelony97.orbit.ui.detail.rhythmPresets
import io.github.thelony97.orbit.ui.sheets.DayOfYearDialog
import io.github.thelony97.orbit.util.Fmt
import kotlinx.coroutines.launch

private class ValueRow(label: String, value: String) {
    var label by mutableStateOf(label)
    val state = TextFieldState(value)
}

@Composable
fun ContactEditScreen(id: String?, prefill: String?, data: OrbitData, idx: Index, nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val original: Contact = remember(id, prefill) {
        id?.let { idx.contacts[it] }
            ?: prefill?.let { runCatching { OrbitRepository.json.decodeFromString(Contact.serializer(), it) }.getOrNull() }
            ?: Contact(id = newId())
    }
    val isNew = id == null

    val first = remember { TextFieldState(original.firstName) }
    val last = remember { TextFieldState(original.lastName) }
    val company = remember { TextFieldState(original.company) }
    val position = remember { TextFieldState(original.position) }
    val address = remember { TextFieldState(original.address) }
    val howWeMet = remember { TextFieldState(original.howWeMet) }
    val notes = remember { TextFieldState(original.notes) }
    val phones = remember { mutableStateListOf<ValueRow>().apply { original.phones.forEach { add(ValueRow(it.label, it.value)) } } }
    val emails = remember { mutableStateListOf<ValueRow>().apply { original.emails.forEach { add(ValueRow(it.label, it.value)) } } }
    var defaultPhone by remember { mutableStateOf(original.defaultPhone) }
    var birthday by remember { mutableStateOf(original.birthday) }
    var groups by remember { mutableStateOf(original.groupIds.toSet()) }
    var rhythm by remember { mutableStateOf(original.rhythmDays) }
    var favorite by remember { mutableStateOf(original.favorite) }
    var photo by remember { mutableStateOf(original.photo) }
    var birthdayDialog by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            Photos.saveFromUri(ctx, uri)?.let { name ->
                if (photo != original.photo) Photos.delete(photo)
                photo = name
            }
        }
    }

    fun build(): Contact {
        val phoneValues = phones.map { LabeledValue(it.label, it.state.text.toString().trim()) }.filter { it.value.isNotEmpty() }
        val emailValues = emails.map { LabeledValue(it.label, it.state.text.toString().trim()) }.filter { it.value.isNotEmpty() }
        return original.copy(
            firstName = first.text.toString().trim(),
            lastName = last.text.toString().trim(),
            company = company.text.toString().trim(),
            position = position.text.toString().trim(),
            address = address.text.toString().trim(),
            howWeMet = howWeMet.text.toString().trim(),
            notes = notes.text.toString().trim(),
            phones = phoneValues,
            defaultPhone = defaultPhone.coerceIn(0, (phoneValues.size - 1).coerceAtLeast(0)),
            emails = emailValues,
            birthday = birthday,
            groupIds = data.groups.map { it.id }.filter { it in groups },
            rhythmDays = rhythm,
            favorite = favorite,
            photo = photo,
        )
    }

    fun save() {
        val contact = build()
        if (contact.firstName.isBlank() && contact.lastName.isBlank() && contact.company.isBlank()) {
            Toast.makeText(ctx, "Bitte einen Namen eingeben", Toast.LENGTH_SHORT).show()
            return
        }
        if (original.photo != null && original.photo != contact.photo && !isNew) {
            Photos.delete(original.photo)
            PhotoCache.evict(original.photo)
        }
        val phonesChanged = contact.phones.map { it.value } != original.phones.map { it.value } || isNew
        OrbitRepository.edit { d -> Mutations.upsertContact(d, contact) }
        if (phonesChanged && contact.phones.isNotEmpty()) Sync.backfill(ctx, contact.id)
        if (isNew) nav.replace(Route.Detail(contact.id)) else nav.back()
    }

    fun discard() {
        if (photo != original.photo) Photos.delete(photo)
        if (isNew && prefill != null) Photos.delete(original.photo)
        nav.back()
    }

    BackHandler { confirmDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Neuer Kontakt" else "Bearbeiten") },
                navigationIcon = { IconButton(onClick = { confirmDiscard = true }) { Icon(Icons.Rounded.Close, "Abbrechen") } },
                actions = {
                    Button(onClick = { save() }, modifier = Modifier.padding(end = 8.dp)) { Text("Speichern") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Photo
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box {
                    Avatar(build().copy(photo = photo), contactColor(original, idx), 112.dp, shape = MaterialShapes.Cookie9Sided.toShape())
                    FilledIconButton(
                        onClick = {
                            AppLock.allowExternal()
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.align(Alignment.BottomEnd).size(40.dp),
                    ) { Icon(Icons.Rounded.PhotoCamera, "Foto wählen", Modifier.size(20.dp)) }
                }
            }
            if (photo != null) {
                TextButton(onClick = { photo = null }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Foto entfernen") }
            }

            Field(first, "Vorname", capitalize = true)
            Field(last, "Nachname", capitalize = true)
            Field(company, "Firma / Schule / Uni", capitalize = true)
            Field(position, "Position / Rolle", capitalize = true)

            SectionLabel("Telefon")
            ValueList(
                rows = phones,
                labels = Defaults.phoneLabels,
                hint = "Nummer",
                keyboard = KeyboardType.Phone,
                defaultIndex = defaultPhone,
                onDefault = { defaultPhone = it },
                addLabel = "Nummer hinzufügen",
            )

            SectionLabel("E-Mail")
            ValueList(
                rows = emails,
                labels = Defaults.emailLabels,
                hint = "E-Mail",
                keyboard = KeyboardType.Email,
                defaultIndex = null,
                onDefault = null,
                addLabel = "E-Mail hinzufügen",
            )

            Field(address, "Adresse", singleLine = false, capitalize = true)

            SectionLabel("Geburtstag")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { birthdayDialog = true },
                    label = { Text(birthday?.let { Fmt.dayOfYear(it) } ?: "Geburtstag wählen") },
                    leadingIcon = { Icon(Icons.Rounded.Cake, null, Modifier.size(18.dp)) },
                )
                if (birthday != null) {
                    IconButton(onClick = { birthday = null }) { Icon(Icons.Rounded.Close, "Entfernen") }
                }
            }

            SectionLabel("Gruppen")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                data.groups.forEach { g ->
                    val selected = g.id in groups
                    FilterChip(
                        selected = selected,
                        onClick = { groups = if (selected) groups - g.id else groups + g.id },
                        label = { Text(g.name + (g.rhythmDays?.let { " · ${it} T" } ?: "")) },
                        leadingIcon = { Box(Modifier.size(10.dp).background(Color(g.color), CircleShape)) },
                    )
                }
            }

            SectionLabel("Melde-Rhythmus")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = rhythm == null, onClick = { rhythm = null }, label = { Text("Wie Gruppe") })
                rhythmPresets.forEach { days ->
                    FilterChip(
                        selected = rhythm == days,
                        onClick = { rhythm = days },
                        label = { Text(Fmt.rhythm(days)) },
                    )
                }
            }

            Field(howWeMet, "Wie kennengelernt", singleLine = false, capitalize = true)
            Field(notes, "Notizen", singleLine = false, capitalize = true)

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(12.dp))
                Text("Favorit", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = favorite, onCheckedChange = { favorite = it })
            }
            Spacer(Modifier.size(48.dp))
        }
    }

    if (birthdayDialog) {
        DayOfYearDialog(initial = birthday, onDismiss = { birthdayDialog = false }, onPick = { birthday = it })
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = "Änderungen verwerfen?",
            text = "Was du hier eingegeben hast, wird nicht gespeichert.",
            confirm = "Verwerfen",
            onConfirm = { discard() },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun Field(
    state: TextFieldState,
    label: String,
    singleLine: Boolean = true,
    capitalize: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        state = state,
        label = { Text(label) },
        lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 8),
        keyboardOptions = KeyboardOptions(
            capitalization = if (capitalize) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
            keyboardType = keyboard,
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun ValueList(
    rows: SnapshotStateList<ValueRow>,
    labels: List<String>,
    hint: String,
    keyboard: KeyboardType,
    defaultIndex: Int?,
    onDefault: ((Int) -> Unit)?,
    addLabel: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEachIndexed { i, row ->
            var menu by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    AssistChip(onClick = { menu = true }, label = { Text(row.label) })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        labels.forEach { l ->
                            DropdownMenuItem(text = { Text(l) }, onClick = { row.label = l; menu = false })
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
                Field(row.state, hint, keyboard = keyboard, modifier = Modifier.weight(1f))
                if (onDefault != null && rows.size > 1) {
                    IconButton(onClick = { onDefault(i) }) {
                        Icon(
                            if (defaultIndex == i) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            "Standard",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(onClick = {
                    rows.removeAt(i)
                    if (onDefault != null && defaultIndex != null && defaultIndex >= rows.size) onDefault(0)
                }) { Icon(Icons.Rounded.Close, "Entfernen") }
            }
        }
        TextButton(onClick = { rows.add(ValueRow(labels.first(), "")) }) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(addLabel)
        }
    }
}
