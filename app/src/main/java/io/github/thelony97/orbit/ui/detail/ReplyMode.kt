package io.github.thelony97.orbit.ui.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.runtime.collectAsState
import io.github.thelony97.orbit.data.OrbitData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import io.github.thelony97.orbit.actions.ContactActions
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.model.BuiltinTypes
import io.github.thelony97.orbit.core.model.HistoryEntry
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.Mutations
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.ui.components.glass
import io.github.thelony97.orbit.ui.theme.Space

/** Where the reply line sends. */
internal enum class ReplyMode(val label: String, val icon: ImageVector, val color: Color) {
    SMS("SMS", Icons.Rounded.Sms, Space.Teal),
    CHAT("WhatsApp", Icons.AutoMirrored.Rounded.Chat, Space.Teal),
    MAIL("Mail", Icons.Rounded.Email, Space.Blue),
    NOTE("Notiz", Icons.AutoMirrored.Rounded.Notes, Space.Violet),
}

/** The modes a contact offers: SMS/WhatsApp with a number, Mail with an address, a note always. */
internal fun replyModes(c: Contact): List<ReplyMode> = buildList {
    if (c.phones.any { it.value.isNotBlank() }) {
        add(ReplyMode.SMS)
        add(ReplyMode.CHAT)
    }
    if (c.emails.any { it.value.isNotBlank() }) add(ReplyMode.MAIL)
    add(ReplyMode.NOTE)
}

/**
 * Faden (PLAN 9 E2): the answer line under the thread. The orb on the left switches the way
 * (tap); the SMS goes straight out (with Orbit's SMS permission, else the SMS app), WhatsApp and
 * mail open prepared, the note lands in the thread at once. E4: holding Senden (SMS/WhatsApp)
 * opens the Mitternachts-Rad – the message goes at its time.
 */
@Composable
internal fun ReplyBar(c: Contact, data: OrbitData, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    val modes = remember(c.phones, c.emails) { replyModes(c) }
    var mode by remember(modes) { mutableStateOf(modes.first()) }
    val text = rememberTextFieldState()
    var pending by remember { mutableStateOf<String?>(null) }
    var wheel by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val t = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (ok && ContactActions.smsDirect(ctx, c, t)) text.clearText() else ContactActions.sms(ctx, c, null)
    }

    fun send() {
        val t = text.text.toString().trim()
        if (t.isEmpty()) return
        haptics.perform(Haptic.Confirm)
        when {
            sendReply(ctx, c, mode, t) -> text.clearText()
            mode == ReplyMode.SMS -> {
                pending = t
                permission.launch(android.Manifest.permission.SEND_SMS)
            }
        }
    }

    Column(modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        PlannedRow(c)
        SnippetRow(text, c, data.settings.snippets)
        MentionRow(text, data)
        Row(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(26.dp), tint = Space.PanelHighest, border = mode.color.copy(alpha = 0.45f))
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeOrb(mode) {
                haptics.perform(Haptic.Tick)
                mode = modes[(modes.indexOf(mode) + 1) % modes.size]
            }
            ReplyField(text, mode, Modifier.weight(1f))
            SendOrb(
                ready = text.text.isNotBlank(), color = mode.color,
                onSend = { send() },
                onHold = if (mode == ReplyMode.SMS || mode == ReplyMode.CHAT) {
                    { if (text.text.isNotBlank()) { haptics.perform(Haptic.Threshold); wheel = true } }
                } else null,
            )
        }
    }
    if (wheel) {
        MidnightWheel(c, data, onPick = { at ->
            wheel = false
            val ms = at.atZone(io.github.thelony97.orbit.domain.zone()).toInstant().toEpochMilli()
            io.github.thelony97.orbit.live.Scheduled.plan(ctx, io.github.thelony97.orbit.live.Scheduled.Plan(newId(), c.id, text.text.toString().trim(), ms, sms = mode == ReplyMode.SMS))
            text.clearText()
        }, onDismiss = { wheel = false })
    }
}

@Composable
private fun ModeOrb(mode: ReplyMode, onClick: () -> Unit) {
    AnimatedContent(mode, label = "replyMode") { m ->
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(m.color.copy(alpha = 0.18f)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(m.icon, m.label, tint = m.color, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun ReplyField(text: androidx.compose.foundation.text.input.TextFieldState, mode: ReplyMode, modifier: Modifier = Modifier) {
    BasicTextField(
        state = text,
        modifier = modifier.heightIn(min = 36.dp).padding(vertical = 8.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Space.Text),
        cursorBrush = SolidColor(mode.color),
        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
        decorator = { inner ->
            Box {
                if (text.text.isEmpty()) Text(mode.label, style = MaterialTheme.typography.bodyMedium, color = Space.TextFaint)
                inner()
            }
        },
    )
}

@Composable
private fun SendOrb(ready: Boolean, color: Color, onSend: () -> Unit, onHold: (() -> Unit)?) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(if (ready) color else Color.Transparent)
            .combinedClickable(onClick = onSend, onLongClick = onHold),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Rounded.Send, "Senden", tint = if (ready) Space.Void else Space.TextFaint, modifier = Modifier.size(18.dp))
    }
}

/** E4 Moment: what waits for its time sits as a little clock above the line; tap = Zurückholen. */
@Composable
private fun PlannedRow(c: Contact) {
    val ctx = LocalContext.current
    val all by io.github.thelony97.orbit.live.Scheduled.plans.collectAsState()
    val mine = all.filter { it.contactId == c.id }.sortedBy { it.at }
    val now = java.time.LocalDateTime.now(io.github.thelony97.orbit.domain.zone())
    mine.forEach { p ->
        androidx.compose.runtime.key(p.id) {
            val at = java.time.Instant.ofEpochMilli(p.at).atZone(io.github.thelony97.orbit.domain.zone()).toLocalDateTime()
            io.github.thelony97.orbit.ui.components.SpaceChip(
                io.github.thelony97.orbit.core.domain.SendLater.label(at, now) + " · " + p.text.take(PREVIEW_CHARS),
                true,
                {
                    io.github.thelony97.orbit.live.Scheduled.cancel(ctx, p.id)
                    io.github.thelony97.orbit.ui.components.Undo.offer("Zurückgeholt") { io.github.thelony97.orbit.live.Scheduled.plan(ctx, p) }
                },
                modifier = Modifier.padding(bottom = 6.dp),
                leading = Icons.Rounded.Schedule,
                accent = Space.Orange,
            )
        }
    }
}

private const val PREVIEW_CHARS = 28
private const val MENTION_HITS = 5

/** E13: „@An“ offers the matching people; a tap writes the @handle. */
@Composable
internal fun MentionRow(text: androidx.compose.foundation.text.input.TextFieldState, data: OrbitData) {
    val typed = io.github.thelony97.orbit.core.domain.Mentions.typing(text.text.toString()) ?: return
    val q = io.github.thelony97.orbit.core.domain.Fold.fold(typed)
    val live = data.contacts.filter { it.deletedAt == null && !it.isMe && !it.archived }
    val hits = live.filter { io.github.thelony97.orbit.core.domain.Fold.fold(it.firstName + " " + it.lastName).startsWith(q) || io.github.thelony97.orbit.core.domain.Fold.fold(it.nickname).startsWith(q) }
        .take(MENTION_HITS)
    if (hits.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        hits.forEach { h ->
            val unique = live.count { io.github.thelony97.orbit.core.domain.Fold.fold(it.firstName) == io.github.thelony97.orbit.core.domain.Fold.fold(h.firstName) } == 1
            io.github.thelony97.orbit.ui.components.SpaceChip(io.github.thelony97.orbit.domain.Logic.displayName(h, false), false, {
                val done = io.github.thelony97.orbit.core.domain.Mentions.complete(text.text.toString(), h, unique)
                text.edit { replace(0, length, done) }
            }, accent = Space.Violet)
        }
    }
}

/** E6: „/“ at the start offers the Textbausteine, filled for this contact; a tap puts it in the line. */
@Composable
internal fun SnippetRow(text: androidx.compose.foundation.text.input.TextFieldState, c: Contact, all: List<io.github.thelony97.orbit.core.model.Snippet>) {
    val hits = io.github.thelony97.orbit.core.domain.Snippets.matching(text.text.toString(), all)
    if (hits.isEmpty()) return
    val haptics = rememberHaptics()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        hits.forEach { sn ->
            io.github.thelony97.orbit.ui.components.SpaceChip(sn.name, false, {
                haptics.perform(Haptic.Tick)
                val filled = io.github.thelony97.orbit.core.domain.Snippets.fill(sn.text, c, io.github.thelony97.orbit.domain.today())
                text.edit { replace(0, length, filled) }
            }, accent = Space.Violet)
        }
    }
}
private const val SUBJECT_CHARS = 60

/** Sends [t] the [mode]'s way; false when the SMS needs the permission first (or nothing could open). */
private fun sendReply(ctx: android.content.Context, c: Contact, mode: ReplyMode, t: String): Boolean = when (mode) {
    ReplyMode.SMS -> ContactActions.smsDirect(ctx, c, t)
    ReplyMode.CHAT -> ContactActions.congratulate(ctx, c, t)
    ReplyMode.MAIL -> ContactActions.mailDraft(ctx, c, t.lineSequence().first().take(SUBJECT_CHARS), t)
    ReplyMode.NOTE -> {
        val e = HistoryEntry(newId(), c.id, BuiltinTypes.NOTE, System.currentTimeMillis(), note = t)
        OrbitRepository.edit { d -> Mutations.addEntry(d, c.id, e) }
        true
    }
}
private const val SETTLE_MS = 1_500L

/**
 * Faden live (PLAN 9 E2): while the page is open, a new call or SMS in the system is read in at
 * once (the incremental sync, after a short settle) and appears in the thread.
 */
@Composable
internal fun LiveThread() {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.DisposableEffect(Unit) {
        var job: kotlinx.coroutines.Job? = null
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                job?.cancel()
                job = scope.launch {
                    kotlinx.coroutines.delay(SETTLE_MS)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                        runCatching { io.github.thelony97.orbit.sync.SyncEngine.syncAll(ctx) }
                    }
                }
            }
        }
        val cr = ctx.contentResolver
        runCatching { if (io.github.thelony97.orbit.sync.SyncEngine.canReadCalls(ctx)) cr.registerContentObserver(android.provider.CallLog.Calls.CONTENT_URI, true, observer) }
        runCatching { if (io.github.thelony97.orbit.sync.SyncEngine.canReadSms(ctx)) cr.registerContentObserver(android.provider.Telephony.Sms.CONTENT_URI, true, observer) }
        onDispose { cr.unregisterContentObserver(observer) }
    }
}
