package io.github.thelony97.orbit.ui.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    MAIL("Mail", Icons.Rounded.Email, Space.Blue),
    NOTE("Notiz", Icons.AutoMirrored.Rounded.Notes, Space.Violet),
}

/** The modes a contact offers: SMS with a number, Mail with an address, a note always. */
internal fun replyModes(c: Contact): List<ReplyMode> = buildList {
    if (c.phones.any { it.value.isNotBlank() }) add(ReplyMode.SMS)
    if (c.emails.any { it.value.isNotBlank() }) add(ReplyMode.MAIL)
    add(ReplyMode.NOTE)
}

/**
 * Faden (PLAN 9 E2): the answer line under the thread. The orb on the left switches SMS → Mail →
 * Notiz (tap); the SMS goes straight out (with Orbit's SMS permission, else the SMS app), the mail
 * opens prepared, the note lands in the thread at once.
 */
@Composable
internal fun ReplyBar(c: Contact, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    val modes = remember(c.phones, c.emails) { replyModes(c) }
    var mode by remember(modes) { mutableStateOf(modes.first()) }
    val text = rememberTextFieldState()
    var pending by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val t = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (ok && ContactActions.smsDirect(ctx, c, t)) text.clearText() else ContactActions.sms(ctx, c, null)
    }

    fun send() {
        val t = text.text.toString().trim()
        if (t.isEmpty()) return
        haptics.perform(Haptic.Confirm)
        val done = sendReply(ctx, c, mode, t)
        if (done) text.clearText()
        else if (mode == ReplyMode.SMS) {
            pending = t
            permission.launch(android.Manifest.permission.SEND_SMS)
        }
    }

    Row(
        modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .glass(RoundedCornerShape(26.dp), tint = Space.PanelHighest, border = mode.color.copy(alpha = 0.45f))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedContent(mode, label = "replyMode") { m ->
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(m.color.copy(alpha = 0.18f)).clickable {
                    haptics.perform(Haptic.Tick)
                    mode = modes[(modes.indexOf(m) + 1) % modes.size]
                },
                contentAlignment = Alignment.Center,
            ) { Icon(m.icon, m.label, tint = m.color, modifier = Modifier.size(18.dp)) }
        }
        BasicTextField(
            state = text,
            modifier = Modifier.weight(1f).heightIn(min = 36.dp).padding(vertical = 8.dp),
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
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(if (text.text.isBlank()) Color.Transparent else mode.color).clickable { send() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.Send, "Senden", tint = if (text.text.isBlank()) Space.TextFaint else Space.Void, modifier = Modifier.size(18.dp))
        }
    }
}

private const val SUBJECT_CHARS = 60

/** Sends [t] the [mode]'s way; false when the SMS needs the permission first (or nothing could open). */
private fun sendReply(ctx: android.content.Context, c: Contact, mode: ReplyMode, t: String): Boolean = when (mode) {
    ReplyMode.SMS -> ContactActions.smsDirect(ctx, c, t)
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
