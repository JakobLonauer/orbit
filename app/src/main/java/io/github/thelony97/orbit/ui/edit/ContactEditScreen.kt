package io.github.thelony97.orbit.ui.edit

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
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
import io.github.thelony97.orbit.ui.components.GlassCard
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.OrbButton
import io.github.thelony97.orbit.ui.components.PhotoCache
import io.github.thelony97.orbit.ui.components.SectionTitle
import io.github.thelony97.orbit.ui.components.SpaceBackground
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.SpaceField
import io.github.thelony97.orbit.ui.components.contactColor
import io.github.thelony97.orbit.ui.components.glow
import io.github.thelony97.orbit.ui.detail.rhythmPresets
import io.github.thelony97.orbit.ui.sheets.DayOfYearDialog
import io.github.thelony97.orbit.ui.theme.Space
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

    SpaceBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            // Top bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OrbButton(Icons.Rounded.Close, "Abbrechen", { confirmDiscard = true }, size = 38.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    if (isNew) "Neuer Kontakt" else "Bearbeiten",
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = Space.Text,
                    modifier = Modifier.weight(1f),
                )
                GlowButton("Speichern", { save() }, icon = Icons.Rounded.Check)
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Photo + names
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        Avatar(
                            build().copy(photo = photo),
                            contactColor(original, idx),
                            84.dp,
                            shape = MaterialShapes.Cookie9Sided.toShape(),
                            glowColor = Space.Violet,
                        )
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 4.dp, y = 4.dp)
                                .size(32.dp)
                                .glow(Space.Violet, 10.dp)
                                .clip(CircleShape)
                                .background(Space.Violet)
                                .clickable {
                                    AppLock.allowExternal()
                                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.PhotoCamera, "Foto wählen", tint = Space.Void, modifier = Modifier.size(16.dp)) }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpaceField(first, "", placeholder = "Vorname", keyboardOptions = caps)
                        SpaceField(last, "", placeholder = "Nachname", keyboardOptions = caps)
                    }
                }
                if (photo != null) {
                    Text(
                        "Foto entfernen",
                        style = MaterialTheme.typography.labelLarge,
                        color = Space.Coral,
                        modifier = Modifier
                            .padding(start = 26.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable { photo = null }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }

                GlassCard {
                    SectionTitle("Arbeit / Schule")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpaceField(company, "", Modifier.weight(1f), placeholder = "Firma / Schule / Uni", keyboardOptions = caps)
                        SpaceField(position, "", Modifier.weight(1f), placeholder = "Rolle", keyboardOptions = caps)
                    }
                }

                GlassCard {
                    SectionTitle("Telefon")
                    ValueList(phones, Defaults.phoneLabels, "Nummer", KeyboardType.Phone, defaultPhone, { defaultPhone = it }, "Nummer hinzufügen")
                    Spacer(Modifier.size(8.dp))
                    SectionTitle("E-Mail")
                    ValueList(emails, Defaults.emailLabels, "E-Mail", KeyboardType.Email, null, null, "E-Mail hinzufügen")
                    Spacer(Modifier.size(8.dp))
                    SectionTitle("Adresse")
                    SpaceField(address, "", singleLine = false, placeholder = "Straße, Ort", keyboardOptions = caps)
                }

                GlassCard {
                    SectionTitle("Geburtstag")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SpaceChip(birthday?.let { Fmt.dayOfYear(it) } ?: "Datum wählen", birthday != null, { birthdayDialog = true }, leading = Icons.Rounded.Cake, accent = Space.Orange)
                        if (birthday != null) {
                            SpaceChip("entfernen", false, { birthday = null })
                        }
                    }
                    Spacer(Modifier.size(10.dp))
                    SectionTitle("Gruppen")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        data.groups.forEach { g ->
                            val selected = g.id in groups
                            SpaceChip(
                                g.name + (g.rhythmDays?.let { " · $it T" } ?: ""),
                                selected,
                                { groups = if (selected) groups - g.id else groups + g.id },
                                dot = Color(g.color),
                                accent = Color(g.color),
                            )
                        }
                    }
                    Spacer(Modifier.size(10.dp))
                    SectionTitle("Melde-Rhythmus")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SpaceChip("wie Gruppe", rhythm == null, { rhythm = null })
                        rhythmPresets.forEach { days ->
                            SpaceChip(Fmt.rhythm(days), rhythm == days, { rhythm = days }, accent = Space.Teal)
                        }
                    }
                }

                GlassCard {
                    SectionTitle("Über")
                    SpaceField(howWeMet, "Wie kennengelernt", singleLine = false, keyboardOptions = caps)
                    Spacer(Modifier.size(8.dp))
                    SpaceField(notes, "Notizen", singleLine = false, keyboardOptions = caps)
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, null, tint = Space.Orange, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Favorit", style = MaterialTheme.typography.bodyMedium, color = Space.Text, modifier = Modifier.weight(1f))
                        Switch(checked = favorite, onCheckedChange = { favorite = it }, colors = spaceSwitch())
                    }
                }
            }
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

private val caps = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

@Composable
fun spaceSwitch() = SwitchDefaults.colors(
    checkedThumbColor = Space.Void,
    checkedTrackColor = Space.Violet,
    checkedBorderColor = Space.Violet,
    uncheckedThumbColor = Space.TextDim,
    uncheckedTrackColor = Space.PanelHighest,
    uncheckedBorderColor = Space.LineStrong,
)

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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEachIndexed { i, row ->
            var menu by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    SpaceChip(row.label, false, { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Space.PanelHigh) {
                        labels.forEach { l ->
                            DropdownMenuItem(text = { Text(l) }, onClick = { row.label = l; menu = false })
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
                SpaceField(row.state, "", Modifier.weight(1f), placeholder = hint, keyboardOptions = KeyboardOptions(keyboardType = keyboard))
                if (onDefault != null && rows.size > 1) {
                    Icon(
                        if (defaultIndex == i) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        "Standard",
                        tint = Space.Orange,
                        modifier = Modifier.size(32.dp).clip(CircleShape).clickable { onDefault(i) }.padding(7.dp),
                    )
                }
                Icon(
                    Icons.Rounded.Close, "Entfernen",
                    tint = Space.TextFaint,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable {
                            rows.removeAt(i)
                            if (onDefault != null && defaultIndex != null && defaultIndex >= rows.size) onDefault(0)
                        }
                        .padding(7.dp),
                )
            }
        }
        SpaceChip(addLabel, false, { rows.add(ValueRow(labels.first(), "")) }, leading = Icons.Rounded.Add)
    }
}
