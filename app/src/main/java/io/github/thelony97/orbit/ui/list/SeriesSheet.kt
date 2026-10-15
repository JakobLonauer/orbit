package io.github.thelony97.orbit.ui.list

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.actions.ContactActions
import io.github.thelony97.orbit.core.designsystem.Corners
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Spacing
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.domain.Snippets
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.today
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.SpaceField
import io.github.thelony97.orbit.ui.theme.Space

/**
 * Serien-Nachricht (PLAN 9 E6): one text with {Platzhaltern} to everyone selected – SMS go out one
 * by one by themselves, WhatsApp opens person after person (each row ticks when opened).
 */
@Composable
fun SeriesSheet(data: OrbitData, ids: Set<String>, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val haptics = rememberHaptics()
    val people = remember(ids) { data.contacts.filter { it.id in ids && it.phones.any { p -> p.value.isNotBlank() } } }
    val text = rememberTextFieldState()
    var sms by remember { mutableStateOf(true) }
    var done by remember { mutableStateOf(emptySet<String>()) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (!ok) sms = false }
    val today = today()

    fun sendAll() {
        val t = text.text.toString()
        if (t.isBlank()) return
        val sent = people.filter { it.id !in done && ContactActions.smsDirect(ctx, it, Snippets.fill(t, it, today)) }.map { it.id }
        done = done + sent
        haptics.perform(Haptic.Confirm)
        if (sent.isEmpty()) permission.launch(android.Manifest.permission.SEND_SMS)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Space.PanelHigh) {
        Column(
            modifier.fillMaxWidth().padding(horizontal = Spacing.L).navigationBarsPadding().padding(bottom = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                SpaceChip("SMS", sms, { sms = true }, leading = Icons.Rounded.Sms, accent = Space.Teal)
                SpaceChip("WhatsApp", !sms, { sms = false }, leading = Icons.AutoMirrored.Rounded.Chat, accent = Space.Teal)
            }
            SpaceField(text, "Nachricht", singleLine = false, placeholder = "Hallo {Rufname}, …")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                data.settings.snippets.forEach { sn -> SpaceChip(sn.name, false, { text.edit { replace(0, length, sn.text) } }, accent = Space.Violet) }
                Snippets.PLACEHOLDERS.forEach { ph -> SpaceChip("{$ph}", false, { text.edit { append(" {$ph}") } }) }
            }
            SeriesPeople(people, text.text.toString(), sms, done) { c, preview ->
                ContactActions.congratulate(ctx, c, preview)
                done = done + c.id
            }
            Row {
                Spacer(Modifier.weight(1f))
                if (sms) GlowButton("An ${people.size} senden", { sendAll() }, enabled = text.text.isNotBlank() && people.isNotEmpty(), color = Space.Teal)
            }
        }
    }
}

/** Who gets it, each with the filled text; with WhatsApp a tap opens that chat. */
@Composable
private fun SeriesPeople(
    people: List<io.github.thelony97.orbit.data.Contact>,
    text: String,
    sms: Boolean,
    done: Set<String>,
    onOpen: (io.github.thelony97.orbit.data.Contact, String) -> Unit,
) {
    val today = today()
    Column {
        people.forEach { c ->
            val preview = Snippets.fill(text, c, today)
            Row(
                Modifier.fillMaxWidth().clip(Corners.Row).clickable(enabled = !sms) { onOpen(c, preview) }.padding(vertical = Spacing.XS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(Logic.shortName(c), style = MaterialTheme.typography.bodyMedium, color = Space.Text)
                    Text(preview.ifBlank { "–" }, style = MaterialTheme.typography.labelMedium, color = Space.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (c.id in done) Icon(Icons.Rounded.Check, null, tint = Space.Teal, modifier = Modifier.size(18.dp))
            }
        }
    }
}
