package io.github.thelony97.orbit.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.os.PersistableBundle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Motion
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.actions.ShareContact
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.ui.components.GhostButton
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.delay

/** One "+ Anlass" style shortcut for a section that is not shown yet (PLAN 3.3). */
data class AddItem(val label: String, val onClick: () -> Unit)

/** The line at the end of the page: only what is missing, nothing greyed out (W5). */
@Composable
fun AddRow(items: List<AddItem>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    FlowRow(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { SpaceChip(it.label, false, it.onClick, leading = Icons.Rounded.Add) }
    }
}

/**
 * Copies [value]; the icon turns into a check for a moment. Android shows its own clipboard
 * preview, so there is no toast. Marked sensitive so the preview hides numbers like an IBAN.
 */
@Composable
fun CopyButton(value: String, label: String) {
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1400)
            copied = false
        }
    }
    AnimatedContent(
        targetState = copied,
        transitionSpec = { (scaleIn(tween(Motion.FAST_MS), initialScale = 0.6f) + fadeIn(tween(Motion.FAST_MS))) togetherWith fadeOut(tween(Motion.FAST_MS)) },
        label = "copy",
    ) { done ->
        Icon(
            if (done) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
            "$label kopieren",
            tint = if (done) Space.Teal else Space.Violet,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable {
                    copyToClipboard(ctx, label, value)
                    haptics.perform(Haptic.Confirm)
                    copied = true
                }
                .padding(8.dp),
        )
    }
}

fun copyToClipboard(ctx: android.content.Context, label: String, value: String) {
    val clip = ClipData.newPlainText(label, value).apply {
        description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    }
    ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}

private val sentence = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

/** "+ Link": title optional, the address is enough. */
@Composable
fun LinkDialog(onDismiss: () -> Unit, onSave: (title: String, url: String) -> Unit) {
    val title = rememberTextFieldState()
    val url = rememberTextFieldState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    state = url,
                    label = { Text("Adresse") },
                    placeholder = { Text("www.beispiel.at") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    state = title,
                    label = { Text("Titel") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = sentence,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = url.text.isNotBlank(), onClick = {
                onSave(title.text.toString().trim(), url.text.toString().trim())
                onDismiss()
            }) { Text("Hinzufügen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** "+ Feld": label with suggestions, value. */
@Composable
fun FieldDialog(suggestions: List<String>, onDismiss: () -> Unit, onSave: (label: String, value: String) -> Unit) {
    val label = rememberTextFieldState()
    val value = rememberTextFieldState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eigenes Feld") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    state = label,
                    label = { Text("Bezeichnung") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = sentence,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.forEach { s ->
                        AssistChip(onClick = { label.edit { replace(0, length, s) } }, label = { Text(s) })
                    }
                }
                OutlinedTextField(
                    state = value,
                    label = { Text("Wert") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = value.text.isNotBlank(), onClick = {
                onSave(label.text.toString().trim(), value.text.toString().trim())
                onDismiss()
            }) { Text("Hinzufügen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Notes of a contact, edited in place instead of opening the whole edit page. */
@Composable
fun NoteDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val text = rememberTextFieldState(initial)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                state = text,
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 4, maxHeightInLines = 12),
                keyboardOptions = sentence,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(text.text.toString().trim())
                onDismiss()
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** The contact as a QR code to scan with another phone; the screen goes to full brightness. */
@Composable
fun QrDialog(c: Contact, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val qr = remember(c) { ShareContact.qr(c)?.asImageBitmap() }
    Dialog(onDismissRequest = onDismiss) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = 1f } }
            onDispose { }
        }
        Column(
            Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(Space.PanelHigh)
                .border(1.dp, Space.LineStrong, RoundedCornerShape(28.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(Logic.displayName(c, false), style = MaterialTheme.typography.titleLargeEmphasized, color = Space.Text)
            Spacer(Modifier.height(14.dp))
            if (qr != null) {
                // One bitmap pixel per module: scaled without smoothing, so the edges stay sharp.
                Image(
                    qr, "QR-Code",
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.size(260.dp).clip(RoundedCornerShape(18.dp)),
                )
            } else {
                Text("Zu viele Angaben für einen QR-Code", style = MaterialTheme.typography.bodyMedium, color = Space.TextDim)
            }
            Spacer(Modifier.height(16.dp))
            GhostButton("Als Datei teilen", { ShareContact.vcard(ctx, c) }, icon = Icons.Rounded.Share)
        }
    }
}
