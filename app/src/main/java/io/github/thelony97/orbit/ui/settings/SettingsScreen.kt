package io.github.thelony97.orbit.ui.settings

import java.time.temporal.ChronoUnit
import io.github.thelony97.orbit.domain.toLocalDate
import io.github.thelony97.orbit.domain.today
import io.github.thelony97.orbit.ui.components.OrbButton
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import io.github.thelony97.orbit.backup.BackupKey
import io.github.thelony97.orbit.core.domain.BackupCrypto
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import io.github.thelony97.orbit.OrbitApp
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
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
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
import io.github.thelony97.orbit.data.now
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
private data class SettingsArea(val key: String, val title: String, val icon: ImageVector, val tint: Color, val status: String)

/** One line of the attention card: what is wrong, an optional small hint, the fix as buttons. */
@Composable
private fun AttentionRow(text: String, hint: String?, actions: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Space.Text)
        if (hint != null) Text(hint, style = MaterialTheme.typography.labelMedium, color = Space.TextFaint)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
    }
}

@Composable
fun SettingsGroup(title: String, status: String? = null, content: @Composable ColumnScope.() -> Unit) {
    // PLAN 3.13: five areas, each with a status line; an empty title continues the area above.
    if (title.isNotEmpty()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = Space.TextDim)
            if (status != null) {
                Spacer(Modifier.width(10.dp))
                Text(
                    status,
                    style = MaterialTheme.typography.labelSmall,
                    color = Space.TextFaint,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    } else {
        Spacer(Modifier.height(8.dp))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
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
    // PLAN 1.3 Einstellungen: switches morph (thumb grows a check) and answer with haptics.
    val haptics = io.github.thelony97.orbit.core.designsystem.rememberHaptics()
    Switch(
        checked = checked,
        onCheckedChange = { v ->
            haptics.perform(if (v) io.github.thelony97.orbit.core.designsystem.Haptic.Confirm else io.github.thelony97.orbit.core.designsystem.Haptic.Tick)
            onChange(v)
        },
        enabled = enabled,
        colors = spaceSwitch(),
        thumbContent = if (checked) {
            { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
        } else null,
    )
}

@Composable
fun SettingsScreen(data: OrbitData, nav: Nav, area: String? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = data.settings
    var timeDialog by remember { mutableStateOf(false) }
    val errorLog = remember { (ctx.applicationContext as? OrbitApp)?.errorLog }
    var errorText by remember { mutableStateOf(errorLog?.read().orEmpty()) }
    var errorDialog by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Pair<OrbitData, Int>?>(null) }
    var passwordFor by remember { mutableStateOf<Uri?>(null) }
    var encryptionDialog by remember { mutableStateOf(false) }
    var encrypted by remember { mutableStateOf(BackupKey.isSet(ctx)) }
    var permissionTick by remember { mutableIntStateOf(0) }
    // The long "restricted settings" help only after Android actually refused (W7).
    var permissionDenied by remember { mutableStateOf(false) }
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
            permissionDenied = true
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
                ctx.contentResolver.openOutputStream(uri)?.use { Backups.writeZip(ctx, it) }
            }.onSuccess {
                // A saved file is a full backup too: the attention card must calm down.
                OrbitRepository.editSettings { it.copy(lastBackupAt = now()) }
                toast("Backup gespeichert")
            }.onFailure { toast("Export fehlgeschlagen: ${it.message}") }
        }
    }
    fun readBackup(uri: Uri, password: CharArray?) {
        scope.launch {
            runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { Backups.readZip(ctx, it, password) } ?: error("Datei nicht lesbar")
            }.onSuccess { restored ->
                passwordFor = null
                pendingRestore = restored to restored.contacts.count { it.deletedAt == null }
            }.onFailure { e ->
                when (e) {
                    is Backups.NeedsPassword -> passwordFor = uri
                    is BackupCrypto.WrongPassword -> toast("Passwort falsch")
                    else -> toast(e.message ?: "Kein gültiges Backup")
                }
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) readBackup(uri, null)
    }

    val lockAvailable = remember(permissionTick) { AppLock.isAvailable(ctx) }
    val missing = remember(permissionTick) { Permissions.core.filterNot { Permissions.granted(ctx, it.permission) } }
    val trashCount = data.contacts.count { it.deletedAt != null }
    val archiveCount = data.contacts.count { it.deletedAt == null && it.archived && !it.isMe }
    val folder = s.backupTreeUri?.let { folderName(Uri.parse(it)) }

    fun backupNow() {
        if (folder != null) {
            scope.launch {
                Backups.backupToFolder(ctx)
                    .onSuccess { toast("Backup gespeichert: $it") }
                    .onFailure { toast("Fehlgeschlagen: ${it.message}") }
            }
        } else {
            AppLock.allowExternal()
            exporter.launch(Backups.fileName())
        }
    }

    // PLAN 3.13: five areas, each with a status line on the start page and its own page.
    val backupLine = s.lastBackupAt?.let { "Backup " + Fmt.ago(ChronoUnit.DAYS.between(it.toLocalDate(), today())) } ?: "noch kein Backup"
    val areas = listOf(
        SettingsArea("capture", "Mitschreiben", Icons.Rounded.Timer, Space.Teal, "Anrufe ab ${s.minCallSeconds} s" + when (mailAccounts.size) { 0 -> ""; 1 -> " · 1 Mail-Konto"; else -> " · ${mailAccounts.size} Mail-Konten" }),
        SettingsArea("notify", "Benachrichtigungen", Icons.Rounded.Notifications, Space.Violet, "Anlässe um %d:%02d".format(s.digestHour, s.digestMinute)),
        SettingsArea("contacts", "Kontakte", Icons.Rounded.Groups, Space.Orange, "${data.groups.size} Gruppen · ${if (s.sortByLastName) "Nachname" else "Vorname"} zuerst"),
        SettingsArea(
            "security", "Sicherheit & Backup", Icons.Rounded.Lock, Space.Blue,
            (if (!s.lockEnabled) "Sperre aus" else if (s.lockDelaySeconds > 0) "Sperre nach ${s.lockDelaySeconds / 60} Min" else "Sperre an") + " · " + backupLine,
        ),
        SettingsArea("about", "Über Orbit", Icons.Rounded.Info, Space.TextDim, "Version ${BuildConfig.VERSION_NAME}" + if (update is UpdateState.Available) " · Update da" else ""),
    )
    val current = areas.firstOrNull { it.key == area }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(key = "title") {
            // A page behind the gear in the contact list (PLAN 3.1), no longer a tab.
            Row(
                Modifier.padding(start = 16.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OrbButton(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", nav.back, size = 38.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(current?.title ?: "Einstellungen", style = MaterialTheme.typography.headlineSmallEmphasized, color = Space.Text)
                    if (current != null) Text(current.status, style = MaterialTheme.typography.labelMedium, color = Space.TextFaint)
                }
            }
        }
        if (current == null) item(key = "update") { UpdateBanner(showWhenIdle = true) }

        // PLAN 3.13: only when something needs Jakob – permissions, backup, mail accounts.
        val backupDays = s.lastBackupAt?.let { ChronoUnit.DAYS.between(it.toLocalDate(), today()) }
        val backupIssue = when {
            backupDays == null -> "Noch kein Backup"
            s.autoBackup && folder != null && backupDays > 9 -> "Letztes Backup " + Fmt.ago(backupDays)
            folder == null && backupDays > 30 -> "Letztes Backup " + Fmt.ago(backupDays)
            else -> null
        }
        val brokenMail = mailAccounts.filter { it.lastError != null }
        if (current == null && (missing.isNotEmpty() || backupIssue != null || brokenMail.isNotEmpty())) {
            item(key = "attention") {
                // „Die Achtung-Karte glimmt“ (PLAN 1.3): a slow coral breath behind it, read in the
                // draw phase only; still in battery saver (PLAN 1.1: no permanent animations).
                val saver = remember { ctx.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true }
                val breath = if (saver) null else io.github.thelony97.orbit.ui.components.rememberBreath()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .glass(RoundedCornerShape(20.dp), tint = Space.Panel, border = Space.Coral.copy(alpha = 0.5f))
                        .drawBehind {
                            val a = 0.10f + 0.12f * (breath?.value ?: 0.5f)
                            drawRoundRect(
                                Brush.radialGradient(listOf(Space.Coral.copy(alpha = a), Space.Coral.copy(alpha = 0f)), center = Offset(size.width * 0.12f, size.height * 0.2f), radius = size.maxDimension * 0.7f),
                                cornerRadius = CornerRadius(20.dp.toPx()),
                            )
                        }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null, tint = Space.Coral, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Braucht deine Aufmerksamkeit", style = MaterialTheme.typography.titleSmallEmphasized, color = Space.Text)
                    }
                    // PLAN 1.2: each permission on its own with one sentence why – never all at once.
                    missing.forEachIndexed { i, p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.title, style = MaterialTheme.typography.bodyMedium, color = Space.Text)
                                Text(p.why, style = MaterialTheme.typography.labelMedium, color = Space.TextFaint)
                            }
                            Spacer(Modifier.width(8.dp))
                            if (i == 0) GlowButton("Erlauben", { permissionLauncher.launch(arrayOf(p.permission)) }, color = Space.Coral)
                            else GhostButton("Erlauben", { permissionLauncher.launch(arrayOf(p.permission)) })
                        }
                    }
                    if (missing.isNotEmpty() && permissionDenied) {
                        // Only after Android refused: how to unlock restricted settings for sideloaded apps.
                        AttentionRow(Permissions.RESTRICTED_HELP, null) {
                            GhostButton("App-Info", {
                                AppLock.allowExternal()
                                ctx.startActivity(Permissions.appInfoIntent(ctx))
                            })
                        }
                    }
                    if (backupIssue != null) {
                        AttentionRow(backupIssue, null) {
                            // W4: one glowing action per card – the first problem gets it.
                            if (missing.isEmpty()) GlowButton("Jetzt sichern", { backupNow() }, color = Space.Blue)
                            else GhostButton("Jetzt sichern", { backupNow() }, icon = Icons.Rounded.Upload)
                            if (folder == null) GhostButton("Ordner wählen", {
                                AppLock.allowExternal()
                                folderPicker.launch(null)
                            })
                        }
                    }
                    brokenMail.forEach { acc ->
                        AttentionRow("${acc.email}: ${acc.lastError}", null) {
                            GhostButton("Öffnen", { nav.open(Route.MailAccounts) })
                        }
                    }
                }
            }
        }

        if (current == null) item(key = "areas") {
            SettingsGroup("") {
                areas.forEachIndexed { i, a ->
                    if (i > 0) Hairline()
                    SettingItem(a.icon, a.title, a.status, iconTint = a.tint, onClick = { nav.open(Route.SettingsArea(a.key)) })
                }
            }
        }

        if (area == "capture") item(key = "calls") {
            SettingsGroup("") {
                SettingItem(Icons.Rounded.Timer, "Anruf zählt ab", iconTint = Space.Teal)
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
                SettingItem(Icons.Rounded.Sync, "Jetzt abgleichen", iconTint = Space.Teal, onClick = {
                    Sync.requestNow(ctx)
                    toast("Abgleich läuft")
                })
                if (missing.isEmpty()) {
                    Hairline()
                    SettingItem(Icons.Rounded.CheckCircle, "Alle Berechtigungen erteilt", iconTint = Space.Teal)
                }
            }
        }

        if (area == "capture") item(key = "mail") {
            SettingsGroup("") {
                SettingItem(
                    Icons.Rounded.Email, "E-Mail-Konten",
                    when (mailAccounts.size) {
                        0 -> "keine"
                        1 -> mailAccounts.first().email + if (mailAccounts.first().lastError != null) " · Fehler" else ""
                        else -> "${mailAccounts.size} Konten verbunden" + if (mailAccounts.any { it.lastError != null }) " · Fehler" else ""
                    },
                    iconTint = if (mailAccounts.any { it.lastError != null }) Space.Coral else Space.Violet,
                    onClick = { nav.open(Route.MailAccounts) },
                )
            }
        }

        if (area == "notify") item(key = "reminders") {
            SettingsGroup("") {
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

        if (area == "contacts") item(key = "org") {
            SettingsGroup("") {
                SettingItem(Icons.Rounded.Groups, "Gruppen", "${data.groups.size} Gruppen", onClick = { nav.open(Route.Groups) })
                Hairline()
                SettingItem(Icons.Rounded.Category, "Eintragsarten", "${data.entryTypes.size} Arten", onClick = { nav.open(Route.EntryTypes) })
                Hairline()
                SettingItem(Icons.Rounded.SortByAlpha, "Sortieren nach", trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SpaceChip("Vorname", !s.sortByLastName, { OrbitRepository.editSettings { it.copy(sortByLastName = false) } })
                        SpaceChip("Nachname", s.sortByLastName, { OrbitRepository.editSettings { it.copy(sortByLastName = true) } })
                    }
                })
                Hairline()
                SettingItem(Icons.Rounded.Archive, "Archiv", if (archiveCount == 0) "leer" else "$archiveCount Kontakte", iconTint = Space.TextDim, onClick = { nav.open(Route.Archive) })
                Hairline()
                SettingItem(Icons.Rounded.Delete, "Papierkorb", if (trashCount == 0) "leer" else "$trashCount Kontakte", iconTint = Space.Coral, onClick = { nav.open(Route.Trash) })
            }
        }

        if (area == "security") item(key = "security") {
            SettingsGroup("") {
                SettingItem(
                    Icons.Rounded.Lock, "App-Sperre",
                    if (lockAvailable) "Biometrie oder PIN" else "erst eine Displaysperre einrichten",
                    iconTint = Space.Orange,
                    trailing = {
                        SpaceSwitch(s.lockEnabled && lockAvailable, enabled = lockAvailable) { v ->
                            OrbitRepository.editSettings { it.copy(lockEnabled = v) }
                        }
                    },
                )
                if (s.lockEnabled && lockAvailable) {
                    // PLAN 3.13: sofort / 1 / 5 Min.
                    FlowRow(
                        Modifier.padding(start = 56.dp, end = 14.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(0 to "sofort", 60 to "nach 1 Min", 300 to "nach 5 Min").forEach { (sec, label) ->
                            SpaceChip(label, s.lockDelaySeconds == sec, { OrbitRepository.editSettings { it.copy(lockDelaySeconds = sec) } })
                        }
                    }
                }
            }
        }

        if (area == "security") item(key = "backup") {
            SettingsGroup("") {
                SettingItem(Icons.Rounded.Folder, "Backup-Ordner", folder ?: "noch nicht gewählt", iconTint = Space.Blue, onClick = {
                    AppLock.allowExternal()
                    folderPicker.launch(null)
                })
                // Automatic backups need a folder: without one the switch would promise nothing (W5).
                if (folder != null) {
                    Hairline()
                    SettingItem(
                        Icons.Rounded.Backup, "Wöchentlich automatisch",
                        s.lastBackupAt?.let { "zuletzt ${Fmt.date(it)}" } ?: "noch kein Backup",
                        iconTint = Space.Blue,
                        trailing = { SpaceSwitch(s.autoBackup) { v -> OrbitRepository.editSettings { it.copy(autoBackup = v) } } },
                    )
                }
                Hairline()
                SettingItem(Icons.Rounded.Upload, "Jetzt sichern", if (folder != null) "in den Backup-Ordner" else "als Datei speichern", iconTint = Space.Blue, onClick = { backupNow() })
                Hairline()
                SettingItem(Icons.Rounded.Lock, "Verschlüsselung", if (encrypted) "an · mit deinem Passwort" else "aus", iconTint = Space.Blue, onClick = {
                    encryptionDialog = true
                })
                // Without a folder "Jetzt sichern" already saves a file – no second row for the same (W5).
                if (folder != null) {
                    Hairline()
                    SettingItem(Icons.Rounded.Download, "An anderem Ort speichern", iconTint = Space.Blue, onClick = {
                        AppLock.allowExternal()
                        exporter.launch(Backups.fileName())
                    })
                }
                Hairline()
                SettingItem(Icons.Rounded.Restore, "Wiederherstellen", "aus einem Orbit-Backup (.zip)", iconTint = Space.Blue, onClick = {
                    AppLock.allowExternal()
                    importer.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                })
            }
        }

        if (area == "about") item(key = "more") {
            SettingsGroup("") {
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
                SettingItem(
                    Icons.Rounded.BugReport, "Fehlerprotokoll",
                    if (errorText.isBlank()) "leer" else "${errorText.split("\n\n").count { it.isNotBlank() }} Einträge",
                    iconTint = Space.TextDim,
                    onClick = { errorDialog = true },
                )
                Hairline()
                SettingItem(Icons.Rounded.Info, "Orbit ${BuildConfig.VERSION_NAME}", "Kotlin · Compose · Material 3 Expressive", iconTint = Space.TextDim)
            }
        }
    }

    if (encryptionDialog) {
        BackupPasswordDialog(
            title = if (encrypted) "Neues Backup-Passwort" else "Backups verschlüsseln",
            confirmTwice = true,
            onDismiss = { encryptionDialog = false },
            onConfirm = { pw ->
                scope.launch {
                    runCatching { BackupKey.set(ctx, pw) }
                        .onSuccess {
                            encrypted = true
                            toast("Backups werden verschlüsselt")
                        }
                        .onFailure { toast("Schlüssel konnte nicht gespeichert werden: ${it.message}") }
                }
            },
            extra = if (encrypted) {
                {
                    TextButton(onClick = {
                        BackupKey.clear(ctx)
                        encrypted = false
                        encryptionDialog = false
                    }) { Text("Ausschalten", color = Space.Coral) }
                }
            } else null,
        )
    }

    passwordFor?.let { uri ->
        BackupPasswordDialog(
            title = "Passwort des Backups",
            confirmTwice = false,
            onDismiss = { passwordFor = null },
            onConfirm = { pw -> readBackup(uri, pw) },
        )
    }

    if (errorDialog) {
        AlertDialog(
            onDismissRequest = { errorDialog = false },
            containerColor = Space.PanelHigh,
            title = { Text("Fehlerprotokoll") },
            text = {
                Text(
                    errorText.ifBlank { "leer" },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = Space.TextDim,
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(enabled = errorText.isNotBlank(), onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, errorText)
                    ctx.startActivity(Intent.createChooser(send, "Fehlerprotokoll teilen"))
                }) { Text("Teilen") }
            },
            dismissButton = {
                TextButton(onClick = {
                    errorLog?.clear()
                    errorText = ""
                    errorDialog = false
                }) { Text("Leeren") }
            },
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
            title = "Backup wiederherstellen?",
            text = "Alle aktuellen Daten in Orbit werden durch das Backup ersetzt ($count Kontakte).",
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

/** Password entry for backups. [confirmTwice] asks for it twice (setting a new one). */
@Composable
private fun BackupPasswordDialog(
    title: String,
    confirmTwice: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    val first = rememberTextFieldState()
    val second = rememberTextFieldState()
    val a = first.text.toString()
    val ok = if (confirmTwice) a.length >= 8 && a == second.text.toString() else a.isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Space.PanelHigh,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedSecureTextField(state = first, label = { Text("Passwort") }, modifier = Modifier.fillMaxWidth())
                if (confirmTwice) {
                    OutlinedSecureTextField(
                        state = second,
                        label = { Text("Wiederholen") },
                        supportingText = { Text("mindestens 8 Zeichen · ohne Passwort keine Wiederherstellung") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                extra?.invoke()
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                onConfirm(first.text.toString().toCharArray())
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

