package io.github.thelony97.orbit.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.data.Defaults
import io.github.thelony97.orbit.data.EntryType
import io.github.thelony97.orbit.data.Group
import io.github.thelony97.orbit.data.Mutations
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.toLocalDate
import io.github.thelony97.orbit.domain.today
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.Photos
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.ConfirmDialog
import io.github.thelony97.orbit.ui.components.EmptyHint
import io.github.thelony97.orbit.ui.components.contactColor
import io.github.thelony97.orbit.ui.components.entryIcon
import io.github.thelony97.orbit.util.Fmt
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.OrbButton
import io.github.thelony97.orbit.ui.components.SpaceBackground
import io.github.thelony97.orbit.ui.theme.Space
import java.time.temporal.ChronoUnit

@Composable
internal fun ManageScaffold(
    title: String,
    nav: Nav,
    fabLabel: String? = null,
    onFab: () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    SpaceBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OrbButton(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", nav.back, size = 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(title, style = MaterialTheme.typography.titleLargeEmphasized, color = Space.Text)
                }
            },
            floatingActionButton = {
                if (fabLabel != null) GlowButton(fabLabel, onFab, icon = Icons.Rounded.Add)
            },
            content = content,
        )
    }
}

@Composable
internal fun segmentedColors() = ListItemDefaults.colors(
    containerColor = Space.Panel,
    contentColor = Space.Text,
)

// ---------------------------------------------------------------- Groups

@Composable
fun GroupsScreen(data: OrbitData, nav: Nav) {
    var editing by remember { mutableStateOf<Group?>(null) }
    var creating by remember { mutableStateOf(false) }
    ManageScaffold("Gruppen", nav, fabLabel = "Neue Gruppe", onFab = { creating = true }) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = 120.dp)) {
            itemsIndexed(data.groups, key = { _, g -> g.id }) { i, g ->
                val members = data.contacts.count { it.deletedAt == null && g.id in it.groupIds }
                ListItem(
                    onClick = { editing = g },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp),
                    leadingContent = { Box(Modifier.size(24.dp).background(Color(g.color), CircleShape)) },
                    supportingContent = {
                        Text("$members Personen" + if (g.pinned) " · angepinnt" else "")
                    },
                    trailingContent = {
                        IconButton(onClick = { OrbitRepository.edit { d -> Mutations.upsertGroup(d, g.copy(pinned = !g.pinned)) } }) {
                            Icon(
                                if (g.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                                if (g.pinned) "Lösen" else "Anpinnen",
                                tint = if (g.pinned) Space.Orange else Space.TextFaint,
                            )
                        }
                    },
                    shapes = ListItemDefaults.segmentedShapes(index = i, count = data.groups.size),
                    colors = segmentedColors(),
                ) { Text(g.name) }
            }
        }
    }
    if (creating) {
        GroupDialog(
            initial = Group(newId(), "", Defaults.groupColors[data.groups.size % Defaults.groupColors.size]),
            isNew = true,
            onDismiss = { creating = false },
            onSave = { g -> OrbitRepository.edit { d -> Mutations.upsertGroup(d, g) } },
            onDelete = null,
        )
    }
    editing?.let { g ->
        GroupDialog(
            initial = g,
            isNew = false,
            onDismiss = { editing = null },
            onSave = { updated -> OrbitRepository.edit { d -> Mutations.upsertGroup(d, updated) } },
            onDelete = { OrbitRepository.edit { d -> Mutations.deleteGroup(d, g.id) } },
        )
    }
}

@Composable
private fun GroupDialog(
    initial: Group,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Group) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val name = rememberTextFieldState(initial.name)
    var color by remember { mutableStateOf(initial.color) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Neue Gruppe" else "Gruppe bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    state = name,
                    label = { Text("Name") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Farbe", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Defaults.groupColors.forEach { c ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .background(Color(c), CircleShape)
                                .then(
                                    if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier,
                                )
                                .clickable { color = c },
                        )
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Gruppe löschen", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.text.isNotBlank(),
                onClick = {
                    onSave(initial.copy(name = name.text.toString().trim(), color = color))
                    onDismiss()
                },
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
    if (confirmDelete && onDelete != null) {
        ConfirmDialog(
            title = "Gruppe löschen?",
            text = "Die Kontakte bleiben erhalten, sie sind nur nicht mehr in dieser Gruppe.",
            confirm = "Löschen",
            onConfirm = { onDelete(); onDismiss() },
            onDismiss = { confirmDelete = false },
        )
    }
}

// ---------------------------------------------------------------- Entry types

@Composable
fun EntryTypesScreen(data: OrbitData, nav: Nav) {
    var editing by remember { mutableStateOf<EntryType?>(null) }
    var creating by remember { mutableStateOf(false) }
    ManageScaffold("Eintragsarten", nav, fabLabel = "Neue Art", onFab = { creating = true }) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = 120.dp)) {
            itemsIndexed(data.entryTypes, key = { _, t -> t.id }) { i, t ->
                ListItem(
                    onClick = { if (!t.builtin) editing = t },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp),
                    leadingContent = { Icon(entryIcon(t.icon), null, tint = MaterialTheme.colorScheme.primary) },
                    shapes = ListItemDefaults.segmentedShapes(index = i, count = data.entryTypes.size),
                    colors = segmentedColors(),
                ) { Text(t.name + if (t.builtin) "" else " · eigene") }
            }
        }
    }
    if (creating) {
        TypeDialog(
            initial = EntryType(newId(), "", "coffee"),
            onDismiss = { creating = false },
            onSave = { t -> OrbitRepository.edit { d -> Mutations.upsertType(d, t) } },
            onDelete = null,
        )
    }
    editing?.let { t ->
        TypeDialog(
            initial = t,
            onDismiss = { editing = null },
            onSave = { updated -> OrbitRepository.edit { d -> Mutations.upsertType(d, updated) } },
            onDelete = { OrbitRepository.edit { d -> Mutations.deleteType(d, t.id) } },
        )
    }
}

@Composable
private fun TypeDialog(initial: EntryType, onDismiss: () -> Unit, onSave: (EntryType) -> Unit, onDelete: (() -> Unit)?) {
    val name = rememberTextFieldState(initial.name)
    var icon by remember { mutableStateOf(initial.icon) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (onDelete == null) "Neue Eintragsart" else "Eintragsart bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    state = name,
                    label = { Text("Name") },
                    placeholder = { Text("z. B. Kaffee, Spieleabend") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Defaults.entryIcons.forEach { key ->
                        val selected = key == icon
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    RoundedCornerShape(if (selected) 14.dp else 22.dp),
                                )
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                entryIcon(key), null,
                                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = { onDelete(); onDismiss() }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.text.isNotBlank(), onClick = {
                onSave(initial.copy(name = name.text.toString().trim(), icon = icon))
                onDismiss()
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

// ---------------------------------------------------------------- Trash

@Composable
fun TrashScreen(data: OrbitData, idx: Index, nav: Nav) {
    val trashed = data.contacts.filter { it.deletedAt != null }.sortedByDescending { it.deletedAt }
    var confirmEmpty by remember { mutableStateOf(false) }
    ManageScaffold(
        "Papierkorb", nav,
        fabLabel = if (trashed.isNotEmpty()) "Leeren" else null,
        onFab = { confirmEmpty = true },
    ) { padding ->
        if (trashed.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyHint("Papierkorb ist leer", "Gelöschte Kontakte bleiben hier 30 Tage.")
            }
            return@ManageScaffold
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = 120.dp)) {
            itemsIndexed(trashed, key = { _, c -> c.id }) { i, c ->
                val left = 30 - ChronoUnit.DAYS.between((c.deletedAt ?: 0L).toLocalDate(), today())
                ListItem(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp),
                    leadingContent = { Avatar(c, contactColor(c, idx), 40.dp) },
                    supportingContent = { Text("Wird in ${left.coerceAtLeast(0)} Tagen endgültig gelöscht") },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { OrbitRepository.edit { d -> Mutations.restore(d, c.id) } }) {
                                Icon(Icons.Rounded.RestoreFromTrash, "Wiederherstellen")
                            }
                            IconButton(onClick = {
                                Mutations.files(c).forEach { Photos.delete(it) }
                                OrbitRepository.edit { d -> Mutations.purge(d, c.id) }
                            }) { Icon(Icons.Rounded.DeleteForever, "Endgültig löschen") }
                        }
                    },
                    shapes = ListItemDefaults.segmentedShapes(index = i, count = trashed.size),
                    colors = segmentedColors(),
                ) { Text(Logic.displayName(c, data.settings.sortByLastName)) }
            }
        }
    }
    if (confirmEmpty) {
        ConfirmDialog(
            title = "Papierkorb leeren?",
            text = "${trashed.size} Kontakte werden endgültig gelöscht.",
            confirm = "Leeren",
            onConfirm = {
                trashed.forEach { c -> Mutations.files(c).forEach { Photos.delete(it) } }
                OrbitRepository.edit { d -> trashed.fold(d) { acc, c -> Mutations.purge(acc, c.id) } }
            },
            onDismiss = { confirmEmpty = false },
        )
    }
}
