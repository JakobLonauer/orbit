package io.github.thelony97.orbit.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.thelony97.orbit.BuildConfig
import io.github.thelony97.orbit.backup.Backups
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.notify.DigestScheduler
import io.github.thelony97.orbit.security.AppLock
import io.github.thelony97.orbit.sync.Sync
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.Permissions
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.components.ConfirmDialog
import io.github.thelony97.orbit.ui.components.UpdateBanner
import io.github.thelony97.orbit.ui.sheets.TimeDialog
import io.github.thelony97.orbit.update.UpdateState
import io.github.thelony97.orbit.update.Updates
import io.github.thelony97.orbit.util.Fmt
import kotlinx.coroutines.launch
import java.time.LocalTime

/** One entry of a settings group. */
private data class SettingRow(
    val icon: ImageVector,
    val title: String,
    val subtitle: String? = null,
    val onClick: (() -> Unit)? = null,
    val trailing: (@Composable () -> Unit)? = null,
)

private fun LazyListScope.group(title: String, rows: List<SettingRow>) {
    if (title.isNotBlank()) item(key = "t-$title") {
        Text(
            title,
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 8.dp),
        )
    }
    rows.forEachIndexed { i, r ->
        item(key = "r-$title-$i") {
            val shapes = ListItemDefaults.segmentedShapes(index = i, count = rows.size)
            val colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
            val modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp)
            val leading: @Composable () -> Unit = { Icon(r.icon, null, tint = MaterialTheme.colorScheme.primary) }
            val supporting: (@Composable () -> Unit)? = r.subtitle?.let { textSlot(it) }
            if (r.onClick != null) {
                ListItem(
                    onClick = r.onClick,
                    modifier = modifier,
                    leadingContent = leading,
                    supportingContent = supporting,
                    trailingContent = r.trailing ?: chevron,
                    shapes = shapes,
                    colors = colors,
                ) { Text(r.title) }
            } else {
                ListItem(
                    modifier = modifier,
                    leadingContent = leading,
                    supportingContent = supporting,
                    trailingContent = r.trailing,
                    shapes = shapes,
                    colors = colors,
                ) { Text(r.title) }
            }
        }
    }
}

private val chevron: @Composable () -> Unit = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) }

private fun textSlot(text: String): @Composable () -> Unit = { Text(text) }

@Composable
fun SettingsScreen(data: OrbitData, nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = data.settings
    var timeDialog by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Pair<OrbitData, Int>?>(null) }
    var permissionTick by remember { mutableIntStateOf(0) }
    val update by Updates.state.collectAsState()

    LifecycleResumeEffect(Unit) {
        permissionTick++
        onPauseOrDispose { }
    }

    fun toast(text: String) = Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionTick++
        if (result.values.any { granted -> !granted }) {
            toast("Nicht alles erlaubt – siehe Hinweis unten")
        } else {
            Sync.requestNow(ctx)
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            OrbitRepository.editSettings { it.copy(backupTreeUri = uri.toString()) }
            Backups.schedule(ctx)
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { Backups.writeZip(it) }
            }.onSuccess { toast("Sicherung exportiert") }.onFailure { toast("Export fehlgeschlagen: ${it.message}") }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { Backups.readZip(it) } ?: error("Datei nicht lesbar")
            }.onSuccess { restored ->
                pendingRestore = restored to restored.contacts.count { it.deletedAt == null }
            }.onFailure { toast(it.message ?: "Keine gültige Sicherung") }
        }
    }

    val lockAvailable = remember(permissionTick) { AppLock.isAvailable(ctx) }
    val missing = remember(permissionTick) { Permissions.core.filterNot { Permissions.granted(ctx, it.permission) } }
    val trashCount = data.contacts.count { it.deletedAt != null }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "bar") { TopAppBar(title = { Text("Einstellungen") }) }
        item(key = "update") { UpdateBanner(showWhenIdle = true) }

        group(
            "Erinnerungen",
            listOf(
                SettingRow(
                    Icons.Rounded.Schedule, "Tägliche Übersicht",
                    "Jeden Tag um %02d:%02d".format(s.digestHour, s.digestMinute),
                    onClick = { timeDialog = true },
                ),
                SettingRow(
                    Icons.Rounded.Checklist, "Themen während Anrufen",
                    "„Nächstes Mal ansprechen“ als Benachrichtigung",
                    trailing = { Switch(s.topicsDuringCall, { v -> OrbitRepository.editSettings { it.copy(topicsDuringCall = v) } }) },
                ),
                SettingRow(
                    Icons.Rounded.Notifications, "Nach dem Anruf nach Notiz fragen",
                    trailing = { Switch(s.noteAfterCall, { v -> OrbitRepository.editSettings { it.copy(noteAfterCall = v) } }) },
                ),
            ),
        )

        item(key = "calls-title") {
            Text(
                "Anrufe & SMS",
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 8.dp),
            )
        }
        item(key = "calls-min") {
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Anruf zählt ab", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Kürzere Anrufe landen nicht im Verlauf",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        listOf(0 to "jeder", 30 to "30 s", 60 to "1 Min", 120 to "2 Min", 300 to "5 Min").forEach { (sec, label) ->
                            FilterChip(
                                selected = s.minCallSeconds == sec,
                                onClick = { OrbitRepository.editSettings { it.copy(minCallSeconds = sec) } },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }
        }
        group(
            "",
            listOf(
                SettingRow(
                    Icons.Rounded.PersonAdd, "Unbekannte Nummern vorschlagen",
                    "Wer öfter anruft oder schreibt, taucht als Vorschlag auf",
                    trailing = { Switch(s.suggestUnknown, { v -> OrbitRepository.editSettings { it.copy(suggestUnknown = v) } }) },
                ),
                SettingRow(Icons.Rounded.Sync, "Jetzt abgleichen", "Anrufliste und SMS neu einlesen", onClick = {
                    Sync.requestNow(ctx)
                    toast("Abgleich läuft")
                }),
            ),
        )

        if (missing.isNotEmpty()) {
            item(key = "perm") {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text(
                                "Berechtigungen fehlen",
                                style = MaterialTheme.typography.titleSmallEmphasized,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                        missing.forEach { p ->
                            Text(
                                "• ${p.title}: ${p.why}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        Text(
                            Permissions.RESTRICTED_HELP,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = {
                                permissionLauncher.launch(missing.map { it.permission }.toTypedArray())
                            }) { Text("Erlauben") }
                            TextButton(onClick = {
                                AppLock.allowExternal()
                                ctx.startActivity(Permissions.appInfoIntent(ctx))
                            }) { Text("App-Info öffnen") }
                        }
                    }
                }
            }
        } else {
            group(
                "Berechtigungen",
                listOf(SettingRow(Icons.Rounded.CheckCircle, "Alles erlaubt", "Anrufliste, SMS, Telefonstatus, Anrufen, Benachrichtigungen")),
            )
        }

        group(
            "Organisation",
            listOf(
                SettingRow(Icons.Rounded.Groups, "Gruppen", "${data.groups.size} Gruppen mit Rhythmus", onClick = { nav.open(Route.Groups) }),
                SettingRow(Icons.Rounded.Category, "Eintragsarten", "Was den Timer zurücksetzt", onClick = { nav.open(Route.EntryTypes) }),
            ),
        )
        item(key = "sort") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sortieren und anzeigen nach", style = MaterialTheme.typography.bodyLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !s.sortByLastName,
                            onClick = { OrbitRepository.editSettings { it.copy(sortByLastName = false) } },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) { Text("Vorname") }
                        SegmentedButton(
                            selected = s.sortByLastName,
                            onClick = { OrbitRepository.editSettings { it.copy(sortByLastName = true) } },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) { Text("Nachname") }
                    }
                }
            }
        }

        group(
            "Sicherheit",
            listOf(
                SettingRow(
                    Icons.Rounded.Lock, "App-Sperre",
                    if (lockAvailable) "Fingerabdruck, Gesicht oder PIN – sperrt sofort beim Verlassen"
                    else "Richte zuerst eine Displaysperre am Handy ein",
                    trailing = {
                        Switch(
                            checked = s.lockEnabled && lockAvailable,
                            onCheckedChange = { v -> OrbitRepository.editSettings { it.copy(lockEnabled = v) } },
                            enabled = lockAvailable,
                        )
                    },
                ),
            ),
        )

        val folder = s.backupTreeUri?.let { folderName(Uri.parse(it)) }
        group(
            "Backup",
            listOf(
                SettingRow(Icons.Rounded.Folder, "Backup-Ordner", folder ?: "Noch nicht gewählt", onClick = {
                    AppLock.allowExternal()
                    folderPicker.launch(null)
                }),
                SettingRow(
                    Icons.Rounded.Backup, "Wöchentlich automatisch",
                    s.lastBackupAt?.let { "Letzte Sicherung: ${Fmt.date(it)}" } ?: "Noch keine Sicherung",
                    trailing = { Switch(s.autoBackup, { v -> OrbitRepository.editSettings { it.copy(autoBackup = v) } }) },
                ),
                SettingRow(Icons.Rounded.Upload, "Jetzt sichern", if (folder != null) "In den Backup-Ordner" else "Als Datei speichern", onClick = {
                    if (folder != null) {
                        scope.launch {
                            Backups.backupToFolder(ctx)
                                .onSuccess { toast("Gesichert: $it") }
                                .onFailure { toast("Fehlgeschlagen: ${it.message}") }
                        }
                    } else {
                        AppLock.allowExternal()
                        exporter.launch(Backups.fileName())
                    }
                }),
                SettingRow(Icons.Rounded.Download, "Als Datei exportieren", onClick = {
                    AppLock.allowExternal()
                    exporter.launch(Backups.fileName())
                }),
                SettingRow(Icons.Rounded.Restore, "Wiederherstellen", "Aus einer Orbit-Sicherung (.zip)", onClick = {
                    AppLock.allowExternal()
                    importer.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                }),
            ),
        )

        group(
            "Sonstiges",
            listOf(
                SettingRow(Icons.Rounded.Delete, "Papierkorb", if (trashCount == 0) "Leer" else "$trashCount Kontakte", onClick = { nav.open(Route.Trash) }),
                SettingRow(
                    Icons.Rounded.SystemUpdate, "Nach Updates suchen",
                    when (update) {
                        is UpdateState.Checking -> "Suche …"
                        is UpdateState.UpToDate -> "Du hast die neueste Version"
                        is UpdateState.Available -> "Neue Version verfügbar"
                        else -> "Version ${BuildConfig.VERSION_NAME}"
                    },
                    onClick = { scope.launch { Updates.check() } },
                ),
                SettingRow(
                    Icons.Rounded.Call, "Beim Start auf Updates prüfen",
                    trailing = { Switch(s.autoUpdateCheck, { v -> OrbitRepository.editSettings { it.copy(autoUpdateCheck = v) } }) },
                ),
                SettingRow(Icons.Rounded.Info, "Orbit ${BuildConfig.VERSION_NAME}", "Kotlin · Jetpack Compose · Material 3 Expressive"),
            ),
        )
    }

    if (timeDialog) {
        TimeDialog(
            initial = LocalTime.of(s.digestHour, s.digestMinute),
            onDismiss = { timeDialog = false },
            onPick = { t ->
                scope.launch {
                    OrbitRepository.update { d -> d.copy(settings = d.settings.copy(digestHour = t.hour, digestMinute = t.minute)) }
                    DigestScheduler.schedule(ctx)
                }
            },
        )
    }

    pendingRestore?.let { (restored, count) ->
        ConfirmDialog(
            title = "Sicherung wiederherstellen?",
            text = "Alle aktuellen Daten in Orbit werden durch die Sicherung ersetzt ($count Kontakte).",
            confirm = "Ersetzen",
            onConfirm = {
                scope.launch {
                    Backups.restore(ctx, restored)
                    toast("Wiederhergestellt")
                }
            },
            onDismiss = { pendingRestore = null },
        )
    }

    LaunchedEffect(Unit) { permissionTick++ }
}

private fun folderName(uri: Uri): String =
    runCatching { DocumentsContract.getTreeDocumentId(uri).substringAfter(':').ifBlank { "Hauptordner" } }
        .getOrDefault("Gewählter Ordner")
