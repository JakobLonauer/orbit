package io.github.thelony97.orbit.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import io.github.thelony97.orbit.mail.MailAccounts
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
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
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import io.github.thelony97.orbit.ui.components.GhostButton
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.UpdateBanner
import io.github.thelony97.orbit.ui.components.glass
import io.github.thelony97.orbit.ui.edit.spaceSwitch
import io.github.thelony97.orbit.ui.home.BottomBarSpace
import io.github.thelony97.orbit.ui.sheets.TimeDialog
import io.github.thelony97.orbit.ui.theme.Space
import io.github.thelony97.orbit.update.UpdateState
import io.github.thelony97.orbit.update.Updates
import io.github.thelony97.orbit.util.Fmt
import kotlinx.coroutines.launch
import java.time.LocalTime

/** A titled glass group of settings rows. */
@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Space.TextDim,
        modifier = Modifier.padding(start = 26.dp, top = 16.dp, bottom = 6.dp),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .glass(RoundedCornerShape(20.dp)),
        content = content,
    )
}

/** Compact settings row (about 48 dp). */
@Composable
fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = Space.Violet,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(iconTint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = Space.Text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Space.TextFaint)
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Space.TextFaint, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun Hairline() {
    Box(Modifier.fillMaxWidth().padding(start = 56.dp).height(1.dp).background(Space.Line))
}

@Composable
private fun SpaceSwitch(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, colors = spaceSwitch())
}

@Composable
fun SettingsScreen(data: OrbitData, nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = data.settings
    var timeDialog by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Pair<OrbitData, Int>?>(null) }
    var permissionTick by remember { mutableIntStateOf(0) }
    val update by Updates.state.collectAsState()
    val mailAccounts by MailAccounts.accounts.collectAsState()

    LifecycleResumeEffect(Unit) {
        permissionTick++
        onPauseOrDispose { }
    }

    fun toast(text: String) = Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionTick++
        if (result.values.any { granted -> !granted }) {
            toast("Nicht alles erlaubt – siehe Hinweis")
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
    val folder = s.backupTreeUri?.let { folderName(Uri.parse(it)) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = BottomBarSpace + 24.dp),
    ) {
        item(key = "title") {
            Text(
                "Einstellungen",
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = Space.Text,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
            )
        }
        item(key = "update") { UpdateBanner(showWhenIdle = true) }

        if (missing.isNotEmpty()) {
            item(key = "perm") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .glass(RoundedCornerShape(20.dp), tint = Space.Panel, border = Space.Coral.copy(alpha = 0.5f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null, tint = Space.Coral, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Berechtigungen fehlen", style = MaterialTheme.typography.titleSmallEmphasized, color = Space.Text)
                    }
                    missing.forEach { p ->
                        Text("• ${p.title}: ${p.why}", style = MaterialTheme.typography.bodySmall, color = Space.TextDim)
                    }
                    Text(Permissions.RESTRICTED_HELP, style = MaterialTheme.typography.labelMedium, color = Space.TextFaint)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        GlowButton("Erlauben", { permissionLauncher.launch(missing.map { it.permission }.toTypedArray()) }, color = Space.Coral)
                        GhostButton("App-Info", {
                            AppLock.allowExternal()
                            ctx.startActivity(Permissions.appInfoIntent(ctx))
                        })
                    }
                }
            }
        }

        item(key = "reminders") {
            SettingsGroup("Erinnerungen") {
                SettingItem(
                    Icons.Rounded.Schedule, "Tägliche Übersicht",
                    "jeden Tag um %02d:%02d".format(s.digestHour, s.digestMinute),
                    onClick = { timeDialog = true },
                )
                Hairline()
                SettingItem(
                    Icons.Rounded.Checklist, "Themen während Anrufen",
                    trailing = { SpaceSwitch(s.topicsDuringCall) { v -> OrbitRepository.editSettings { it.copy(topicsDuringCall = v) } } },
                )
                Hairline()
                SettingItem(
                    Icons.Rounded.Notifications, "Nach dem Anruf nach Notiz fragen",
                    trailing = { SpaceSwitch(s.noteAfterCall) { v -> OrbitRepository.editSettings { it.copy(noteAfterCall = v) } } },
                )
            }
        }

        item(key = "calls") {
            SettingsGroup("Anrufe & SMS") {
                SettingItem(Icons.Rounded.Timer, "Anruf zählt ab", "kürzere Anrufe landen nicht im Verlauf", iconTint = Space.Teal)
                FlowRow(
                    Modifier.padding(start = 56.dp, end = 14.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(0 to "jeder", 30 to "30 s", 60 to "1 Min", 120 to "2 Min", 300 to "5 Min").forEach { (sec, label) ->
                        SpaceChip(label, s.minCallSeconds == sec, { OrbitRepository.editSettings { it.copy(minCallSeconds = sec) } }, accent = Space.Teal)
                    }
                }
                Hairline()
                SettingItem(
                    Icons.Rounded.PersonAdd, "Unbekannte Nummern vorschlagen", iconTint = Space.Teal,
                    trailing = { SpaceSwitch(s.suggestUnknown) { v -> OrbitRepository.editSettings { it.copy(suggestUnknown = v) } } },
                )
                Hairline()
                SettingItem(Icons.Rounded.Sync, "Jetzt abgleichen", "Anrufliste und SMS neu einlesen", iconTint = Space.Teal, onClick = {
                    Sync.requestNow(ctx)
                    toast("Abgleich läuft")
                })
                if (missing.isEmpty()) {
                    Hairline()
                    SettingItem(Icons.Rounded.CheckCircle, "Alle Berechtigungen erteilt", iconTint = Space.Teal)
                }
            }
        }

        item(key = "mail") {
            SettingsGroup("E-Mail") {
                SettingItem(
                    Icons.Rounded.Email, "E-Mail-Konten",
                    when (mailAccounts.size) {
                        0 -> "verbinden, um Mails im Kontakt zu sehen"
                        1 -> mailAccounts.first().email + if (mailAccounts.first().lastError != null) " · Fehler" else ""
                        else -> "${mailAccounts.size} Konten verbunden" + if (mailAccounts.any { it.lastError != null }) " · Fehler" else ""
                    },
                    iconTint = if (mailAccounts.any { it.lastError != null }) Space.Coral else Space.Violet,
                    onClick = { nav.open(Route.MailAccounts) },
                )
            }
        }

        item(key = "org") {
            SettingsGroup("Organisation") {
                SettingItem(Icons.Rounded.Groups, "Gruppen", "${data.groups.size} Gruppen mit Rhythmus", onClick = { nav.open(Route.Groups) })
                Hairline()
                SettingItem(Icons.Rounded.Category, "Eintragsarten", "was den Timer zurücksetzt", onClick = { nav.open(Route.EntryTypes) })
                Hairline()
                SettingItem(Icons.Rounded.SortByAlpha, "Sortieren nach", trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SpaceChip("Vorname", !s.sortByLastName, { OrbitRepository.editSettings { it.copy(sortByLastName = false) } })
                        SpaceChip("Nachname", s.sortByLastName, { OrbitRepository.editSettings { it.copy(sortByLastName = true) } })
                    }
                })
            }
        }

        item(key = "security") {
            SettingsGroup("Sicherheit") {
                SettingItem(
                    Icons.Rounded.Lock, "App-Sperre",
                    if (lockAvailable) "Biometrie oder PIN, sperrt sofort beim Verlassen" else "erst eine Displaysperre einrichten",
                    iconTint = Space.Orange,
                    trailing = {
                        SpaceSwitch(s.lockEnabled && lockAvailable, enabled = lockAvailable) { v ->
                            OrbitRepository.editSettings { it.copy(lockEnabled = v) }
                        }
                    },
                )
            }
        }

        item(key = "backup") {
            SettingsGroup("Backup") {
                SettingItem(Icons.Rounded.Folder, "Backup-Ordner", folder ?: "noch nicht gewählt", iconTint = Space.Blue, onClick = {
                    AppLock.allowExternal()
                    folderPicker.launch(null)
                })
                Hairline()
                SettingItem(
                    Icons.Rounded.Backup, "Wöchentlich automatisch",
                    s.lastBackupAt?.let { "zuletzt ${Fmt.date(it)}" } ?: "noch keine Sicherung",
                    iconTint = Space.Blue,
                    trailing = { SpaceSwitch(s.autoBackup) { v -> OrbitRepository.editSettings { it.copy(autoBackup = v) } } },
                )
                Hairline()
                SettingItem(Icons.Rounded.Upload, "Jetzt sichern", if (folder != null) "in den Backup-Ordner" else "als Datei speichern", iconTint = Space.Blue, onClick = {
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
                })
                Hairline()
                SettingItem(Icons.Rounded.Download, "Als Datei exportieren", iconTint = Space.Blue, onClick = {
                    AppLock.allowExternal()
                    exporter.launch(Backups.fileName())
                })
                Hairline()
                SettingItem(Icons.Rounded.Restore, "Wiederherstellen", "aus einer Orbit-Sicherung (.zip)", iconTint = Space.Blue, onClick = {
                    AppLock.allowExternal()
                    importer.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                })
            }
        }

        item(key = "more") {
            SettingsGroup("Sonstiges") {
                SettingItem(Icons.Rounded.Delete, "Papierkorb", if (trashCount == 0) "leer" else "$trashCount Kontakte", iconTint = Space.Coral, onClick = { nav.open(Route.Trash) })
                Hairline()
                SettingItem(
                    Icons.Rounded.SystemUpdate, "Nach Updates suchen",
                    when (update) {
                        is UpdateState.Checking -> "suche …"
                        is UpdateState.UpToDate -> "du hast die neueste Version"
                        is UpdateState.Available -> "neue Version verfügbar"
                        else -> "Version ${BuildConfig.VERSION_NAME}"
                    },
                    onClick = { scope.launch { Updates.check() } },
                )
                Hairline()
                SettingItem(
                    Icons.Rounded.Update, "Beim Start auf Updates prüfen",
                    trailing = { SpaceSwitch(s.autoUpdateCheck) { v -> OrbitRepository.editSettings { it.copy(autoUpdateCheck = v) } } },
                )
                Hairline()
                SettingItem(Icons.Rounded.Info, "Orbit ${BuildConfig.VERSION_NAME}", "Kotlin · Compose · Material 3 Expressive", iconTint = Space.TextDim)
            }
        }
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
