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
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.automirrored.rounded.ShortText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import io.github.thelony97.orbit.OrbitApp
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
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
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Email
import io.github.thelony97.orbit.mail.MailAccounts
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Euro
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.ScreenRotation
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            .glass(RoundedCornerShape(20.dp))
            // Rows that a switch brings or takes glide in and out (W2) – nothing below jumps.
            .animateContentSize(io.github.thelony97.orbit.core.designsystem.Motion.medium()),
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
internal fun SpaceSwitch(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
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
    var permissionsOpen by remember { mutableStateOf(false) }
    var handbook by remember { mutableStateOf(false) }
    var phoneDialog by remember { mutableStateOf(false) }
    // W1-02/W2-02: only after Android actually refused the dialer role (declined or blocked by
    // restricted settings), not on first sight – same rule as the other permissions above.
    var phoneRoleDenied by remember { mutableStateOf(false) }
    var smsDialog by remember { mutableStateOf(false) }
    val update by Updates.state.collectAsState()
    val mailAccounts by MailAccounts.accounts.collectAsState()

    LifecycleResumeEffect(Unit) {
        permissionTick++
        onPauseOrDispose { }
    }

    fun toast(text: String, ok: Boolean = true) = io.github.thelony97.orbit.ui.components.Undo.note(ctx, text, ok)

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionTick++
        if (result.values.any { granted -> !granted }) {
            permissionDenied = true
            toast("Nicht alles erlaubt – siehe Hinweis", ok = false)
        } else {
            Sync.requestNow(ctx)
        }
    }
    // PLAN 9 A1: Orbit im Telefonbuch – contacts permission first, then the account.
    fun phoneBookOn() {
        io.github.thelony97.orbit.phonebook.PhoneBookSync.turnOn(ctx)
    }
    val phoneBookPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionTick++
        if (result.values.all { it }) phoneBookOn() else permissionDenied = true
    }
    // PLAN 9 A13: Orbit-Kalender.
    fun calendarOn() {
        OrbitRepository.editSettings { it.copy(orbitCalendar = true) }
        io.github.thelony97.orbit.phonebook.OrbitCalendar.request(ctx.applicationContext)
    }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionTick++
        if (result.values.all { it }) calendarOn() else permissionDenied = true
    }
    // PLAN 9 B8: Anruf-Filter – the system's role dialog decides, then the rules apply.
    val screeningLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        io.github.thelony97.orbit.call.ScreeningRole.tidy(ctx)
        permissionTick++
        if (io.github.thelony97.orbit.call.ScreeningRole.held(ctx)) OrbitRepository.editSettings { it.copy(screening = true) }
    }
    var screenListDialog by remember { mutableStateOf<String?>(null) }
    // Orbit als Telefon-App (PLAN 3.12): the system's own role dialog decides.
    val phoneRoleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        io.github.thelony97.orbit.call.PhoneRole.tidy(ctx)
        permissionTick++
        if (io.github.thelony97.orbit.call.PhoneRole.held(ctx)) {
            phoneRoleDenied = false
            io.github.thelony97.orbit.ui.components.Undo.note(ctx, "Orbit ist jetzt die Telefon-App")
        } else {
            phoneRoleDenied = true
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
            }.onFailure { toast("Export fehlgeschlagen: ${it.message}", ok = false) }
        }
    }
    // Export Excel (PLAN 3.14): Kontakte and Verlauf as one .xlsx.
    val excelExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(io.github.thelony97.orbit.backup.ExcelExport.MIME)) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val snapshot = OrbitRepository.snapshot()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)?.use { io.github.thelony97.orbit.backup.ExcelExport.write(it, snapshot) } ?: error("Datei nicht beschreibbar")
                }
            }.onSuccess { toast("Excel gespeichert") }
                .onFailure { toast("Export fehlgeschlagen: ${it.message}", ok = false) }
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
                    is BackupCrypto.WrongPassword -> toast("Passwort falsch", ok = false)
                    else -> toast(e.message ?: "Kein gültiges Backup", ok = false)
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
                    .onFailure { toast("Fehlgeschlagen: ${it.message}", ok = false) }
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
        SettingsArea("auto", "Automatiken", Icons.Rounded.AutoMode, Space.Teal, activeAutomations(data).let { if (it == 1) "1 an" else "$it an" }),
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
                        .animateItem()
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .glass(RoundedCornerShape(20.dp), tint = Space.Panel, border = Space.Coral.copy(alpha = 0.5f))
                        .animateContentSize(io.github.thelony97.orbit.core.designsystem.Motion.medium())
                        .drawBehind {
                            val a = 0.10f + 0.12f * (breath?.value ?: 0.5f)
                            drawRoundRect(
                                Brush.radialGradient(listOf(Space.Coral.copy(alpha = a), Space.Coral.copy(alpha = 0f)), center = Offset(size.width * 0.12f, size.height * 0.2f), radius = size.maxDimension * 0.7f),
                                cornerRadius = CornerRadius(20.dp.toPx()),
                            )
                        }
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null, tint = Space.Coral, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Braucht deine Aufmerksamkeit", style = MaterialTheme.typography.titleSmallEmphasized, color = Space.Text)
                    }
                    // PLAN 1.2: each permission on its own with one sentence why – never all at once.
                    // Many missing: one line that opens the list, so the card stays short (W4).
                    if (missing.size > 2 && !permissionsOpen) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${missing.size} Berechtigungen fehlen", style = MaterialTheme.typography.bodyMedium, color = Space.Text, modifier = Modifier.weight(1f))
                            GlowButton("Ansehen", { permissionsOpen = true }, color = Space.Coral)
                        }
                    }
                    if (missing.size <= 2 || permissionsOpen) missing.forEachIndexed { i, p ->
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
                    // Every problem the same shape: what, then its one action on the right (the
                    // folder is chosen in Sicherheit & Backup, not here).
                    if (backupIssue != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(backupIssue, style = MaterialTheme.typography.bodyMedium, color = Space.Text, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            // W4: one glowing action per card – the first problem gets it.
                            if (missing.isEmpty()) GlowButton("Sichern", { backupNow() }, color = Space.Blue)
                            else GhostButton("Sichern", { backupNow() })
                        }
                    }
                    brokenMail.forEach { acc ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${acc.email}: ${acc.lastError}", style = MaterialTheme.typography.bodyMedium, color = Space.Text, modifier = Modifier.weight(1f), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Spacer(Modifier.width(8.dp))
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

        if (area == "capture" && io.github.thelony97.orbit.call.PhoneRole.available(ctx)) item(key = "phone") {
            val held = remember(permissionTick) { io.github.thelony97.orbit.call.PhoneRole.held(ctx) }
            SettingsGroup("") {
                SettingItem(
                    Icons.Rounded.Call, "Orbit als Telefon-App", subtitle = if (held) "Aktiv" else null, iconTint = Space.Teal,
                    trailing = {
                        SpaceSwitch(held) { on ->
                            // Turning it off happens where Android keeps the default apps.
                            if (on) phoneDialog = true
                            else runCatching { ctx.startActivity(io.github.thelony97.orbit.call.PhoneRole.defaultAppsIntent()) }
                        }
                    },
                )
                if (!held && phoneRoleDenied) {
                    Hairline()
                    // W1-02/W2-02: Android refused the role – a direct way to the App-Info page
                    // instead of leaving Jakob to find "Eingeschränkte Einstellungen" himself.
                    AttentionRow(io.github.thelony97.orbit.ui.Permissions.RESTRICTED_HELP_DIALER, null) {
                        GhostButton("App-Info", {
                            io.github.thelony97.orbit.security.AppLock.allowExternal()
                            runCatching { ctx.startActivity(io.github.thelony97.orbit.ui.Permissions.appInfoIntent(ctx)) }
                                .onFailure { toast("Nicht möglich", ok = false) }
                        })
                    }
                }
                if (held) {
                    Hairline()
                    SettingItem(
                        Icons.Rounded.Sms, "SMS-Vorlagen",
                        subtitle = s.smsTemplates.size.let { if (it == 1) "1 Vorlage" else "$it Vorlagen" },
                        iconTint = Space.Teal, onClick = { smsDialog = true },
                    )
                    Hairline()
                    // The block list belongs to the system (shared with SMS and the stock phone app).
                    SettingItem(Icons.Rounded.Block, "Gesperrte Nummern", iconTint = Space.Teal, onClick = {
                        runCatching {
                            ctx.startActivity(ctx.getSystemService(android.telecom.TelecomManager::class.java).createManageBlockedNumbersIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }.onFailure { toast("Nicht möglich", ok = false) }
                    })
                }
            }
        }

        // PLAN 9 B8: Abwehr ohne Verlust – silence by fixed rules, never reject.
        if (area == "capture" && io.github.thelony97.orbit.call.ScreeningRole.available(ctx)) item(key = "screening") {
            val held = remember(permissionTick) { io.github.thelony97.orbit.call.ScreeningRole.held(ctx) }
            val on = s.screening && held
            SettingsGroup("") {
                SettingItem(
                    Icons.Rounded.Shield, "Anruf-Filter", subtitle = if (on) "Stumm statt abweisen" else null, iconTint = Space.Coral,
                    trailing = {
                        SpaceSwitch(on) { v ->
                            if (!v) {
                                OrbitRepository.editSettings { it.copy(screening = false) }
                            } else if (held) {
                                OrbitRepository.editSettings { it.copy(screening = true) }
                            } else {
                                io.github.thelony97.orbit.call.ScreeningRole.requestIntent(ctx)?.let { runCatching { screeningLauncher.launch(it) } }
                            }
                        }
                    },
                )
                if (on) {
                    Hairline()
                    SettingItem(
                        Icons.Rounded.Bedtime, "Unbekannt nachts (22–7)", iconTint = Space.Coral,
                        trailing = { SpaceSwitch(s.screenNight) { v -> OrbitRepository.editSettings { it.copy(screenNight = v) } } },
                    )
                    Hairline()
                    SettingItem(
                        Icons.Rounded.Euro, "Mehrwertnummern", iconTint = Space.Coral,
                        trailing = { SpaceSwitch(s.screenPremium) { v -> OrbitRepository.editSettings { it.copy(screenPremium = v) } } },
                    )
                    Hairline()
                    SettingItem(
                        Icons.Rounded.Public, "Länder",
                        subtitle = s.screenCountries.joinToString(", ").ifBlank { null },
                        iconTint = Space.Coral, onClick = { screenListDialog = "countries" },
                    )
                    Hairline()
                    SettingItem(
                        Icons.Rounded.FormatListNumbered, "Nummernserien",
                        subtitle = s.screenSeries.joinToString(", ").ifBlank { null },
                        iconTint = Space.Coral, onClick = { screenListDialog = "series" },
                    )
                }
            }
        }

        // PLAN 9 B12: Bewegungsgesten – off until Jakob switches them on.
        if (area == "capture") item(key = "gestures") {
            SettingsGroup("") {
                SettingItem(
                    Icons.Rounded.ScreenRotation, "Bewegungsgesten",
                    subtitle = if (s.motionGestures) "Ohr · Umdrehen · Hinlegen" else null,
                    iconTint = Space.Teal,
                    trailing = { SpaceSwitch(s.motionGestures) { v -> OrbitRepository.editSettings { it.copy(motionGestures = v) } } },
                )
            }
        }

        // PLAN 9 D8: Ankommen – off until switched on; then location, then „immer“ (background).
        if (area == "capture") item(key = "arrive") { ArriveSetting(s.arrive) }
        // PLAN 9 E6: Textbausteine („/“ in der Antwortzeile, Serien-Nachricht).
        if (area == "capture") item(key = "snippets") { SnippetsSetting(s.snippets) }

        if (area == "capture") item(key = "calls") {
            SettingsGroup("") {
                SettingItem(Icons.Rounded.Timer, "Anruf zählt ab", iconTint = Space.Teal)
                FlowRow(
                    Modifier.padding(start = 56.dp, end = 14.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
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

        // PLAN 9 E17: Automatiken.
        if (area == "auto") item(key = "automations") { AutomationsPage(data, onGroups = { nav.open(io.github.thelony97.orbit.ui.Route.SettingsArea("contacts")) }) }

        if (area == "notify") item(key = "reminders") {
            SettingsGroup("") {
                SettingItem(
                    Icons.Rounded.Schedule, "Tägliche Übersicht",
                    "jeden Tag um %02d:%02d".format(s.digestHour, s.digestMinute),
                    onClick = { timeDialog = true },
                )
                Hairline()
                SettingItem(
                    Icons.Rounded.Call, "Anrufer-Info beim Klingeln",
                    trailing = { SpaceSwitch(s.callerInfo) { v -> OrbitRepository.editSettings { it.copy(callerInfo = v) } } },
                )
                Hairline()
                // PLAN 9 B1: unknown Austrian numbers looked up on herold.at (once per number and week).
                SettingItem(
                    Icons.Rounded.Business, "Unbekannte im Herold nachschlagen",
                    trailing = { SpaceSwitch(s.callerLookup) { v -> OrbitRepository.editSettings { it.copy(callerLookup = v) } } },
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
                Hairline()
                SettingItem(
                    Icons.Rounded.Event, "Treffen am Morgen",
                    trailing = { SpaceSwitch(s.meetingBrief) { v -> OrbitRepository.editSettings { it.copy(meetingBrief = v) } } },
                )
                Hairline()
                // PLAN 9 A13: Orbit-Kalender – every occasion in the phone's calendar app.
                SettingItem(
                    Icons.Rounded.CalendarMonth, "Anlässe im Handy-Kalender",
                    trailing = {
                        SpaceSwitch(s.orbitCalendar) { on ->
                            if (on) {
                                val need = listOf(android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR)
                                    .filter { ctx.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
                                if (need.isEmpty()) calendarOn() else calendarPermission.launch(need.toTypedArray())
                            } else {
                                OrbitRepository.editSettings { it.copy(orbitCalendar = false) }
                                val keep = s.phoneBook
                                io.github.thelony97.orbit.ui.components.AppWork.launch { io.github.thelony97.orbit.phonebook.OrbitCalendar.remove(ctx.applicationContext, keepAccount = keep) }
                            }
                        }
                    },
                )
            }
        }

        // PLAN 9 A1/A8: Orbit im Telefonbuch des Handys, Durchklingeln.
        if (area == "contacts") item(key = "phonebook") {
            val count by io.github.thelony97.orbit.phonebook.PhoneBookSync.count.collectAsState()
            LaunchedEffect(s.phoneBook) { if (s.phoneBook) io.github.thelony97.orbit.phonebook.PhoneBookSync.request(ctx) }
            // The status line counts up while Orbit fills the phone book (PLAN 9 A1 Moment).
            val shown by androidx.compose.animation.core.animateIntAsState(
                count ?: 0,
                androidx.compose.animation.core.tween(io.github.thelony97.orbit.core.designsystem.Motion.EMPHASIZED_MS, easing = io.github.thelony97.orbit.core.designsystem.Motion.Emphasized),
                label = "phoneBookCount",
            )
            SettingsGroup("") {
                SettingItem(
                    Icons.Rounded.Contacts, "Im Telefonbuch des Handys",
                    subtitle = if (s.phoneBook && count != null) { if (shown == 1) "1 Kontakt" else "$shown Kontakte" } else null,
                    trailing = {
                        SpaceSwitch(s.phoneBook) { on ->
                            if (on) {
                                val need = listOf(android.Manifest.permission.READ_CONTACTS, android.Manifest.permission.WRITE_CONTACTS)
                                    .filter { ctx.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
                                if (need.isEmpty()) phoneBookOn() else phoneBookPermission.launch(need.toTypedArray())
                            } else {
                                OrbitRepository.editSettings { it.copy(phoneBook = false) }
                                val keep = s.orbitCalendar
                                io.github.thelony97.orbit.ui.components.AppWork.launch { io.github.thelony97.orbit.phonebook.PhoneBookSync.remove(ctx.applicationContext, keepAccount = keep) }
                            }
                        }
                    },
                )
                if (s.phoneBook) {
                    Hairline()
                    SettingItem(
                        Icons.Rounded.Business, "Auch Organisationen",
                        trailing = {
                            SpaceSwitch(s.phoneBookOrgs) { v ->
                                OrbitRepository.editSettings { it.copy(phoneBookOrgs = v) }
                                io.github.thelony97.orbit.phonebook.PhoneBookSync.request(ctx)
                            }
                        },
                    )
                    Hairline()
                    // Favourites are „markiert“ in the phone book; the system lets them through.
                    SettingItem(Icons.Rounded.DoNotDisturbOn, "Favoriten bei Nicht stören", onClick = {
                        AppLock.allowExternal()
                        val tries = listOf("android.settings.ZEN_MODE_PRIORITY_SETTINGS", "android.settings.ZEN_MODE_SETTINGS", android.provider.Settings.ACTION_SOUND_SETTINGS)
                        tries.firstOrNull { action -> runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }
                    })
                }
            }
        }

        // PLAN 9 E15: Smart-Gruppen (gespeicherte Suchen) – entfernen mit Rückgängig.
        if (area == "contacts" && s.smartGroups.isNotEmpty()) item(key = "smart") {
            SettingsGroup("Smart-Gruppen") {
                s.smartGroups.forEachIndexed { i, sg ->
                    if (i > 0) Hairline()
                    SettingItem(Icons.Rounded.Search, sg.name, iconTint = Space.Violet, trailing = {
                        IconButton(onClick = {
                            OrbitRepository.editSettings { st -> st.copy(smartGroups = st.smartGroups.filterNot { it.id == sg.id }) }
                            io.github.thelony97.orbit.ui.components.Undo.offer("Entfernt") {
                                OrbitRepository.editSettings { st -> if (st.smartGroups.any { it.id == sg.id }) st else st.copy(smartGroups = st.smartGroups + sg) }
                            }
                        }) { Icon(Icons.Rounded.Close, "Entfernen", tint = Space.TextFaint) }
                    })
                }
            }
        }

        if (area == "contacts") item(key = "org") {
            SettingsGroup("") {
                SettingItem(Icons.Rounded.Groups, "Gruppen", if (data.groups.size == 1) "1 Gruppe" else "${data.groups.size} Gruppen", onClick = { nav.open(Route.Groups) })
                Hairline()
                SettingItem(Icons.Rounded.Category, "Eintragsarten", if (data.entryTypes.size == 1) "1 Art" else "${data.entryTypes.size} Arten", onClick = { nav.open(Route.EntryTypes) })
                Hairline()
                // Fotos (PLAN 3.10): the page with every picture, how much room they take, full size.
                // Counted like the Fotos page shows them (organisations only while they are shown, PLAN 2.3).
                val orgsShown = io.github.thelony97.orbit.ui.Session.audience != io.github.thelony97.orbit.domain.Audience.PEOPLE
                val photoCount = remember(data, orgsShown) {
                    data.contacts.filter { io.github.thelony97.orbit.core.domain.Visibility.inLists(it, orgsShown) }
                        .flatMap { c -> c.gallery.filter { it.mime.startsWith("image/") }.map { it.file } }.distinct().size
                }
                val photoBytes by androidx.compose.runtime.produceState(0L, photoCount) {
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        OrbitRepository.photoDir.listFiles()?.sumOf { it.length() } ?: 0L
                    }
                }
                SettingItem(
                    Icons.Rounded.PhotoLibrary, "Fotos",
                    "$photoCount Bilder · " + io.github.thelony97.orbit.core.domain.StorageUsage.format(photoBytes),
                    onClick = { nav.open(Route.Photos()) },
                )
                Hairline()
                SettingItem(
                    Icons.Rounded.PhotoSizeSelectLarge, "Originalgröße behalten",
                    trailing = { SpaceSwitch(s.keepOriginalPhotos) { v -> OrbitRepository.editSettings { it.copy(keepOriginalPhotos = v) } } },
                )
                Hairline()
                // PLAN 3.5: hides „Gratuliert“/„Geschenk“ in the Anlässe tab completely.
                SettingItem(
                    Icons.Rounded.CardGiftcard, "Gratuliert und Geschenke abhaken",
                    trailing = { SpaceSwitch(s.giftTracking) { v -> OrbitRepository.editSettings { it.copy(giftTracking = v) } } },
                )
                Hairline()
                SettingItem(Icons.Rounded.SortByAlpha, "Sortieren nach", trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SpaceChip("Vorname", !s.sortByLastName, { OrbitRepository.editSettings { it.copy(sortByLastName = false) } })
                        SpaceChip("Nachname", s.sortByLastName, { OrbitRepository.editSettings { it.copy(sortByLastName = true) } })
                    }
                })
                Hairline()
                SettingItem(Icons.Rounded.Archive, "Archiv", if (archiveCount == 0) "leer" else if (archiveCount == 1) "1 Kontakt" else "$archiveCount Kontakte", iconTint = Space.TextDim, onClick = { nav.open(Route.Archive) })
                Hairline()
                SettingItem(Icons.Rounded.Delete, "Papierkorb", if (trashCount == 0) "leer" else if (trashCount == 1) "1 Kontakt" else "$trashCount Kontakte", iconTint = Space.Coral, onClick = { nav.open(Route.Trash) })
            }
        }

        if (area == "security") item(key = "security") {
            SettingsGroup("") {
                SettingItem(
                    Icons.Rounded.Lock, "App-Sperre",
                    if (lockAvailable) null else "erst eine Displaysperre einrichten",
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                SettingItem(Icons.Rounded.Key, "Verschlüsselung", if (encrypted) "an · mit deinem Passwort" else "aus", iconTint = Space.Blue, onClick = {
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
                SettingItem(Icons.Rounded.Restore, "Wiederherstellen", iconTint = Space.Blue, onClick = {
                    AppLock.allowExternal()
                    importer.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                })
            }
        }

        if (area == "security") item(key = "export") {
            SettingsGroup("Exportieren") {
                SettingItem(Icons.Rounded.TableChart, "Excel", "Kontakte und Verlauf", iconTint = Space.Blue, onClick = {
                    AppLock.allowExternal()
                    excelExporter.launch(io.github.thelony97.orbit.backup.ExcelExport.fileName())
                })
                Hairline()
                SettingItem(Icons.Rounded.ContactPage, "vCard", "alle Kontakte", iconTint = Space.Blue, onClick = {
                    io.github.thelony97.orbit.actions.ShareContact.vcards(ctx, data.contacts.filter { it.deletedAt == null && !it.isMe })
                })
            }
        }

        if (area == "security") item(key = "storage") { StorageCard() }

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
                    onClick = { io.github.thelony97.orbit.ui.components.AppWork.launch { Updates.check() } },
                )
                Hairline()
                // PLAN 9 E22: the Handbuch instead of explanations in the screens.
                SettingItem(Icons.AutoMirrored.Rounded.MenuBook, "Handbuch", "Gesten, Regeln, Automatiken", iconTint = Space.Violet, onClick = { handbook = true })
                Hairline()
                // PLAN 2.4 / 3.13: what is new is one line here – never a popup.
                SettingItem(Icons.Rounded.NewReleases, "Neu in dieser Version", WHATS_NEW, iconTint = Space.Violet)
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
                SettingItem(
                    Icons.Rounded.Info, "Orbit ${BuildConfig.VERSION_NAME}",
                    // W8: the measured cold start of this run, next to the stack it runs on.
                    listOfNotNull("Kotlin · Compose · Material 3 Expressive", io.github.thelony97.orbit.StartTime.lastMs?.let { "Start $it ms" }).joinToString(" · "),
                    iconTint = Space.TextDim,
                )
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
                        .onFailure { toast("Schlüssel konnte nicht gespeichert werden: ${it.message}", ok = false) }
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

    if (handbook) HandbookSheet(onDismiss = { handbook = false })

    if (smsDialog) {
        // Three slots for „Ablehnen mit SMS“; empty ones are dropped.
        val slots = List(3) { i -> androidx.compose.foundation.text.input.rememberTextFieldState(s.smsTemplates.getOrNull(i).orEmpty()) }
        AlertDialog(
            onDismissRequest = { smsDialog = false },
            containerColor = Space.PanelHigh,
            title = { Text("SMS-Vorlagen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    slots.forEach { st ->
                        androidx.compose.material3.OutlinedTextField(
                            state = st,
                            lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
                            shape = io.github.thelony97.orbit.core.designsystem.Corners.Row,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                io.github.thelony97.orbit.ui.components.DialogConfirm(onClick = {
                    val list = slots.map { it.text.toString().trim() }.filter { it.isNotEmpty() }
                    OrbitRepository.editSettings { it.copy(smsTemplates = list) }
                    smsDialog = false
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { smsDialog = false }) { Text("Abbrechen") } },
        )
    }

    screenListDialog?.let { which ->
        ScreenListDialog(countries = which == "countries", s, onDismiss = { screenListDialog = null })
    }

    if (phoneDialog) {
        // PLAN 3.12: one sentence when turning it on.
        AlertDialog(
            onDismissRequest = { phoneDialog = false },
            containerColor = Space.PanelHigh,
            title = { Text("Orbit als Telefon-App") },
            text = {
                Text(
                    "Spam-Warnung, Anruf-Filter und visuelle Mailbox der Google-Telefon-App entfallen; Android verlangt eventuell zuerst „Eingeschränkte Einstellungen zulassen“; zurück jederzeit unter Standard-Apps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Space.TextDim,
                )
            },
            confirmButton = {
                io.github.thelony97.orbit.ui.components.DialogConfirm(onClick = {
                    phoneDialog = false
                    val request = io.github.thelony97.orbit.call.PhoneRole.requestIntent(ctx)
                    if (request != null) runCatching { phoneRoleLauncher.launch(request) }.onFailure {
                        io.github.thelony97.orbit.call.PhoneRole.tidy(ctx)
                        phoneRoleDenied = true
                        toast("Nicht möglich", ok = false)
                    } else {
                        phoneRoleDenied = true
                        toast("Nicht möglich", ok = false)
                    }
                }) { Text("Einschalten") }
            },
            dismissButton = { TextButton(onClick = { phoneDialog = false }) { Text("Abbrechen") } },
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
                io.github.thelony97.orbit.ui.components.DialogConfirm(enabled = errorText.isNotBlank(), onClick = {
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
    io.github.thelony97.orbit.ui.components.DialogMark()
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
            io.github.thelony97.orbit.ui.components.DialogConfirm(enabled = ok, onClick = {
                onConfirm(first.text.toString().toCharArray())
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen", color = io.github.thelony97.orbit.ui.theme.Space.TextDim) } },
    )
}

/** The one „Was ist neu“ line (PLAN 3.13), kept with the release that brings it. */
internal const val WHATS_NEW =
    "Orbit-Zeile · Antwortzeile mit Mitternachts-Rad · Doppelsterne · Geschenke-Board · Automatiken · Sprachnotiz · Scan · Person-Widget"

/**
 * PLAN 9 B8: the filter's lists. Countries: the usual ping-call origins as chips, any other by its
 * code; number series as typed („0900, +43 720“).
 */
@Composable
private fun ScreenListDialog(countries: Boolean, s: io.github.thelony97.orbit.core.model.Settings, onDismiss: () -> Unit) {
    var picked by remember { mutableStateOf(s.screenCountries.map { it.uppercase() }) }
    val typed = androidx.compose.foundation.text.input.rememberTextFieldState(if (countries) "" else s.screenSeries.joinToString(", "))
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Space.PanelHigh,
        title = { Text(if (countries) "Länder" else "Nummernserien") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (countries) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (io.github.thelony97.orbit.core.domain.Screening.pingCountries + picked).distinct().forEach { code ->
                            SpaceChip(countryName(code), code in picked, { picked = if (code in picked) picked - code else picked + code }, accent = Space.Coral)
                        }
                    }
                }
                androidx.compose.material3.OutlinedTextField(
                    state = typed,
                    lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
                    placeholder = { Text(if (countries) "RS, BA" else "0900, +43 720") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = if (countries) androidx.compose.ui.text.input.KeyboardCapitalization.Characters else androidx.compose.ui.text.input.KeyboardCapitalization.None,
                        keyboardType = if (countries) androidx.compose.ui.text.input.KeyboardType.Text else androidx.compose.ui.text.input.KeyboardType.Phone,
                    ),
                    shape = io.github.thelony97.orbit.core.designsystem.Corners.Row,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            io.github.thelony97.orbit.ui.components.DialogConfirm(onClick = {
                val text = typed.text.toString()
                if (countries) {
                    val list = (picked + io.github.thelony97.orbit.core.domain.Screening.countriesOf(text)).distinct()
                    OrbitRepository.editSettings { it.copy(screenCountries = list) }
                } else {
                    OrbitRepository.editSettings { it.copy(screenSeries = io.github.thelony97.orbit.core.domain.Screening.seriesOf(text)) }
                }
                onDismiss()
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** „Nigeria“ for NG – the system's German country names. */
private fun countryName(code: String): String =
    runCatching { java.util.Locale.Builder().setRegion(code).build().getDisplayCountry(java.util.Locale.GERMAN) }.getOrNull()
        ?.takeIf { it.isNotBlank() && it != code } ?: code

@Composable
private fun ArriveSetting(on: Boolean) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    fun enable() {
        // The fences follow with the save (OrbitApp.onSaved → Arrive.sync).
        OrbitRepository.editSettings { it.copy(arrive = true) }
    }
    val background = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) enable()
    }
    val foreground = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) background.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }
    SettingsGroup("") {
        SettingItem(
            Icons.Rounded.Place, "Ankommen",
            subtitle = if (on) "Orte mit Themen, Ausweis, WLAN" else null,
            iconTint = Space.Teal,
            trailing = {
                SpaceSwitch(on) { v ->
                    when {
                        !v -> OrbitRepository.editSettings { it.copy(arrive = false) }
                        io.github.thelony97.orbit.live.Arrive.allowed(ctx) -> enable()
                        ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ->
                            background.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        else -> foreground.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }
            },
        )
    }
}

private const val SNIPPET_SLOTS = 6

@Composable
private fun SnippetsSetting(snippets: List<io.github.thelony97.orbit.core.model.Snippet>) {
    var open by remember { mutableStateOf(false) }
    SettingsGroup("") {
        SettingItem(
            Icons.AutoMirrored.Rounded.ShortText, "Textbausteine",
            subtitle = snippets.joinToString(" · ") { it.name }.ifBlank { null },
            iconTint = Space.Violet, onClick = { open = true },
        )
    }
    if (!open) return
    val names = List(SNIPPET_SLOTS) { i -> androidx.compose.foundation.text.input.rememberTextFieldState(snippets.getOrNull(i)?.name.orEmpty()) }
    val texts = List(SNIPPET_SLOTS) { i -> androidx.compose.foundation.text.input.rememberTextFieldState(snippets.getOrNull(i)?.text.orEmpty()) }
    AlertDialog(
        onDismissRequest = { open = false },
        containerColor = Space.PanelHigh,
        title = { Text("Textbausteine") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(io.github.thelony97.orbit.core.domain.Snippets.PLACEHOLDERS.joinToString(" ") { "{$it}" }, style = MaterialTheme.typography.labelSmall, color = Space.TextDim)
                for (i in 0 until SNIPPET_SLOTS) {
                    io.github.thelony97.orbit.ui.components.SpaceField(names[i], "Name")
                    io.github.thelony97.orbit.ui.components.SpaceField(texts[i], "Text", singleLine = false)
                }
            }
        },
        confirmButton = {
            io.github.thelony97.orbit.ui.components.DialogConfirm(onClick = {
                val list = (0 until SNIPPET_SLOTS).mapNotNull { i ->
                    val n = names[i].text.toString().trim()
                    val t = texts[i].text.toString().trim()
                    if (n.isEmpty() || t.isEmpty()) null else io.github.thelony97.orbit.core.model.Snippet(snippets.getOrNull(i)?.id ?: io.github.thelony97.orbit.data.newId(), n, t)
                }
                OrbitRepository.editSettings { it.copy(snippets = list) }
                open = false
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Abbrechen") } },
    )
}
