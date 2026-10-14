package io.github.thelony97.orbit.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.toLocalDate
import io.github.thelony97.orbit.domain.today
import io.github.thelony97.orbit.mail.MailAccounts
import io.github.thelony97.orbit.mail.MailCache
import io.github.thelony97.orbit.mail.MailClient
import io.github.thelony97.orbit.mail.MailHeader
import io.github.thelony97.orbit.mail.MailResult
import io.github.thelony97.orbit.ui.components.GhostButton
import io.github.thelony97.orbit.ui.components.SectionCard
import io.github.thelony97.orbit.ui.theme.Space
import io.github.thelony97.orbit.util.Fmt
import kotlinx.coroutines.launch
import java.time.temporal.ChronoUnit

/** Latest mails this contact sent me – from all of their addresses, across all linked accounts. */
@Composable
fun MailSection(contact: Contact) {
    val accounts by MailAccounts.accounts.collectAsState()
    val addresses = contact.emails.map { it.value.trim() }.filter { it.contains('@') }
    // Without a linked mailbox there is nothing to show (W5); linking lives in the settings.
    if (addresses.isEmpty() || accounts.isEmpty()) return
    val key = MailCache.key(contact.id, addresses, accounts)
    var result by remember(key) { mutableStateOf(MailCache.get(key)) }
    var loading by remember(key) { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var showAll by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<MailHeader?>(null) }

    LaunchedEffect(key, reload) {
        if (accounts.isEmpty()) return@LaunchedEffect
        val cached = MailCache.get(key)
        if (cached != null && MailCache.isFresh(cached) && reload == 0) {
            result = cached
            return@LaunchedEffect
        }
        loading = true
        val fresh = MailCache.fetch(key) { MailClient.fromAddresses(addresses, accounts) }.await()
        result = fresh
        loading = false
    }

    SectionCard(
        title = result?.headers?.takeIf { it.isNotEmpty() }?.let { "E-Mails von ${Logic.shortName(contact)} · ${it.size}" } ?: "E-Mails",
        icon = Icons.Rounded.Email,
        action = if (accounts.isNotEmpty()) {
            {
                if (loading) {
                    LoadingIndicator(Modifier.size(26.dp), color = Space.Violet)
                } else {
                    Icon(
                        Icons.Rounded.Refresh, "Neu laden",
                        tint = Space.Violet,
                        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(50)).clickable { reload++ }.padding(5.dp),
                    )
                }
            }
        } else null,
    ) {
        val r: MailResult? = result
        if (r == null) {
            // The rows the mailbox is about to fill, in their final shape (PLAN 1.2).
            val breath = io.github.thelony97.orbit.ui.components.rememberBreath()
            listOf(0.7f, 0.5f, 0.62f).forEach { w -> io.github.thelony97.orbit.ui.components.MailRowPlaceholder(breath, w) }
            return@SectionCard
        }
        r.errors.forEach { e ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = Space.Coral, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                Spacer(Modifier.width(6.dp))
                Text(e, style = MaterialTheme.typography.labelMedium, color = Space.Coral)
            }
        }
        if (r.headers.isEmpty() && r.errors.isEmpty()) {
            Text(
                "Keine Mails von ${addresses.joinToString(", ")} gefunden.",
                style = MaterialTheme.typography.bodySmall,
                color = Space.TextFaint,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        val shown = if (showAll) r.headers else r.headers.take(5)
        val multiAccount = accounts.size > 1
        shown.forEach { h -> MailRow(h, multiAccount, addresses.size > 1, onClick = { open = h }) }
        if (!showAll && r.headers.size > 5) {
            Text(
                "Alle ${r.headers.size} anzeigen",
                style = MaterialTheme.typography.labelLarge,
                color = Space.Violet,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { showAll = true }.padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
    }

    open?.let { h -> MailDialog(h, onDismiss = { open = null }) }
}

@Composable
private fun MailRow(h: MailHeader, showAccount: Boolean, showSender: Boolean, onClick: () -> Unit) {
    val days = ChronoUnit.DAYS.between(h.at.toLocalDate(), today())
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(h.subject, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = Space.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = buildList {
                if (showSender) add(h.fromAddress)
                if (showAccount) add("an ${h.accountEmail}")
            }
            if (meta.isNotEmpty()) {
                Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Space.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (days == 0L) Fmt.time(h.at) else if (days < 7) Fmt.ago(days) else Fmt.shortDate(h.at.toLocalDate()),
            style = MaterialTheme.typography.labelMedium,
            color = Space.TextDim,
        )
    }
}

@Composable
internal fun MailDialog(h: MailHeader, onDismiss: () -> Unit, contactId: String? = null) {
    var text by remember(h) { mutableStateOf<String?>(null) }
    var error by remember(h) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(h) {
        scope.launch {
            MailClient.body(h)
                .onSuccess { text = it }
                .onFailure { error = MailClient.describe(it) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Space.PanelHigh,
        title = {
            Column {
                Text(h.subject, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text("${h.from} · ${Fmt.dateTime(h.at)}", style = MaterialTheme.typography.labelMedium, color = Space.TextDim)
            }
        },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                when {
                    error != null -> Text(error!!, style = MaterialTheme.typography.bodySmall, color = Space.Coral)
                    text == null -> {
                        val breath = io.github.thelony97.orbit.ui.components.rememberBreath()
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1f, 0.92f, 0.97f, 0.6f, 0f, 0.85f, 0.4f).forEach { w ->
                                if (w == 0f) Spacer(Modifier.height(6.dp)) else io.github.thelony97.orbit.ui.components.PlaceholderLine(breath, w, 11.dp)
                            }
                        }
                    }
                    else -> SelectionContainer { Text(text!!, style = MaterialTheme.typography.bodySmall, color = Space.Text) }
                }
            }
        },
        confirmButton = {
            androidx.compose.foundation.layout.Row {
                // Bild-Anhänge in den Ordner (PLAN 3.9): one tap, then they are the contact's photos.
                if (contactId != null && text != null) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    var copying by remember(h) { mutableStateOf(false) }
                    TextButton(enabled = !copying, onClick = {
                        copying = true
                        scope.launch {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                                val result = MailClient.images(h, ctx.cacheDir)
                                val files = result.getOrDefault(emptyList())
                                result.exceptionOrNull()?.let { e ->
                                    android.widget.Toast.makeText(ctx, MailClient.describe(e), android.widget.Toast.LENGTH_LONG).show()
                                    return@withContext
                                }
                                if (files.isNotEmpty()) {
                                    val added = GalleryStore.import(ctx.applicationContext, files.map { android.net.Uri.fromFile(it) })
                                    files.forEach { it.delete() }
                                    io.github.thelony97.orbit.data.OrbitRepository.editContact(contactId) { it.copy(gallery = added.asReversed().map { a -> a.copy(contactId = contactId) } + it.gallery) }
                                }
                                android.widget.Toast.makeText(ctx, if (files.isEmpty()) "Keine Bilder in dieser Mail" else "${files.size} Bilder im Ordner", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            copying = false
                        }
                    }) { Text("Bilder") }
                }
                TextButton(onClick = onDismiss) { Text("Schließen") }
            }
        },
        // „Antworten“ (PLAN 3.9): the mail app with recipient and „Re: Betreff“.
        dismissButton = if (h.fromAddress.contains('@')) {
            {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                TextButton(onClick = {
                    val subject = if (h.subject.startsWith("Re:", ignoreCase = true)) h.subject else "Re: ${h.subject}"
                    val uri = android.net.Uri.parse("mailto:" + android.net.Uri.encode(h.fromAddress) + "?subject=" + android.net.Uri.encode(subject))
                    io.github.thelony97.orbit.security.AppLock.allowExternal()
                    runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO, uri)) }
                    onDismiss()
                }) { Text("Antworten") }
            }
        } else null,
    )
}

/** A bundled mail entry (PLAN 3.9): its mails, one tap loads the text. */
@Composable
fun MailEntryDialog(entry: io.github.thelony97.orbit.data.Entry, onDismiss: () -> Unit) {
    var open by remember(entry.id) { mutableStateOf(entry.mails.singleOrNull()) }
    val chosen = open
    if (chosen != null) {
        MailDialog(io.github.thelony97.orbit.mail.MailSync.header(chosen), onDismiss = { if (entry.mails.size == 1) onDismiss() else open = null }, contactId = entry.contactId)
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Space.PanelHigh,
        title = { Text(io.github.thelony97.orbit.core.domain.MailBundle.title(entry)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                entry.mails.forEach { m ->
                    Column(
                        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .clickable { open = m }.padding(vertical = 8.dp, horizontal = 4.dp),
                    ) {
                        Text(m.subject, style = MaterialTheme.typography.bodyMedium, color = Space.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(m.address + " · " + Fmt.time(m.at), style = MaterialTheme.typography.labelMedium, color = Space.TextFaint)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}
