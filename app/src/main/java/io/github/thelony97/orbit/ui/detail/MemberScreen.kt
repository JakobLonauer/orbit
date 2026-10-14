package io.github.thelony97.orbit.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.actions.ContactActions
import io.github.thelony97.orbit.core.domain.Members
import io.github.thelony97.orbit.core.model.LabeledValue
import io.github.thelony97.orbit.core.model.Member
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.Mutations
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.components.EmptyHint
import io.github.thelony97.orbit.ui.components.GhostButton
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.OrbButton
import io.github.thelony97.orbit.ui.components.SectionCard
import io.github.thelony97.orbit.ui.components.SpaceBackground
import io.github.thelony97.orbit.ui.components.Undo
import io.github.thelony97.orbit.ui.components.entryIcon
import io.github.thelony97.orbit.ui.sheets.EntrySheet
import io.github.thelony97.orbit.ui.theme.Space
import io.github.thelony97.orbit.util.Fmt
import kotlinx.coroutines.launch

/**
 * Seite eines versteckten Mitarbeiters (PLAN 3.6): name, role, Durchwahl, Mail, dauerhafte Notiz
 * and every conversation with them. Reached only from its organisation's page.
 */
@Composable
fun MemberScreen(orgId: String, memberId: String, data: OrbitData, idx: Index, nav: Nav) {
    val org = idx.contacts[orgId]?.takeIf { it.isOrg && it.deletedAt == null }
    val m = org?.members?.firstOrNull { it.id == memberId }
    if (org == null || m == null) {
        // Removed or turned into a contact meanwhile. No automatic back: during the exit
        // animation that would pop the page we just went to.
        SpaceBackground {
            Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
                EmptyHint("Nicht mehr hier", "", action = "Zurück" to { nav.back() })
            }
        }
        return
    }
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    var newEntry by remember { mutableStateOf(false) }
    var openEntry by remember { mutableStateOf<io.github.thelony97.orbit.data.Entry?>(null) }
    var promote by remember { mutableStateOf(false) }
    var mailEntry by remember { mutableStateOf<io.github.thelony97.orbit.data.Entry?>(null) }
    val talks = Members.talks(org, m.id)
    SpaceBackground {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            // Cards bring their own 16 dp gutter (as on the contact page); the head rows get theirs.
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "top") {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OrbButton(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", { nav.back() })
                    Spacer(Modifier.weight(1f))
                    OrbButton(Icons.Rounded.Edit, "Bearbeiten", { editing = true })
                }
            }
            item(key = "head") {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(Space.Blue.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(memberInitials(m.name), style = MaterialTheme.typography.titleLarge, color = Space.Blue, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.headlineSmallEmphasized, color = Space.Text)
                        Text(
                            listOf(m.role, Logic.shortName(org)).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Space.Violet,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { nav.open(Route.Detail(org.id)) },
                        )
                        Members.lastTalk(org, m.id)?.let { at ->
                            Text("Zuletzt gesprochen " + Fmt.ago(java.time.temporal.ChronoUnit.DAYS.between(java.time.Instant.ofEpochMilli(at).atZone(io.github.thelony97.orbit.domain.zone()).toLocalDate(), io.github.thelony97.orbit.domain.today())), style = MaterialTheme.typography.labelMedium, color = Space.TextFaint)
                        }
                    }
                }
            }
            item(key = "actions") {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlowButton("Gespräch eintragen", { newEntry = true }, icon = Icons.Rounded.EditNote)
                    m.phones.firstOrNull { it.value.isNotBlank() }?.let { p ->
                        OrbButton(Icons.Rounded.Call, "Anrufen", { ContactActions.call(ctx, org, p.value, data.settings) }, accent = Space.Teal)
                    }
                    m.emails.firstOrNull { it.value.isNotBlank() }?.let { e ->
                        OrbButton(Icons.Rounded.Email, "E-Mail", { ContactActions.email(ctx, org, e.value) })
                    }
                }
            }
            if (m.phones.isNotEmpty() || m.emails.isNotEmpty()) item(key = "contact") {
                SectionCard(title = "Kontakt", icon = Icons.Rounded.Call) {
                    (m.phones + m.emails).filter { it.value.isNotBlank() }.forEach { v ->
                        InfoRow(label = v.label, value = v.value)
                    }
                }
            }
            if (m.note.isNotBlank()) item(key = "note") {
                SectionCard(title = "Notiz", icon = Icons.AutoMirrored.Rounded.Notes) {
                    InfoRow(label = "", value = m.note, onClick = { editing = true })
                }
            }
            item(key = "talks-head") {
                SectionCard(title = if (talks.isEmpty()) "Gespräche" else "Gespräche · ${talks.size}", icon = Icons.Rounded.History) {
                    if (talks.isEmpty()) {
                        // The button above is the one way on (W6).
                        EmptyHint("Noch kein Gespräch", "")
                    }
                    talks.forEach { e ->
                        val type = idx.types[e.typeId]
                        Row(
                            // Mails open as mails (PLAN 3.9), everything else in the entry sheet.
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { if (e.mails.isNotEmpty()) mailEntry = e else openEntry = e }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(entryIcon(type?.icon ?: "meet"), null, tint = Space.Violet, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                val title = if (e.mails.isNotEmpty()) io.github.thelony97.orbit.core.domain.MailBundle.title(e) else type?.name ?: "Eintrag"
                                Text(title + " · " + Fmt.dateTime(e.at), style = MaterialTheme.typography.labelLarge, color = Space.TextDim)
                                e.mails.take(3).forEach { m -> Text("• " + m.subject, style = MaterialTheme.typography.bodySmall, color = Space.TextDim, maxLines = 1) }
                                if (e.note.isNotBlank()) Text(e.note, style = MaterialTheme.typography.bodyMedium, color = Space.Text)
                            }
                        }
                    }
                }
            }
            item(key = "promote") {
                GhostButton("Zu eigenem Kontakt machen", { promote = true }, icon = Icons.Rounded.PersonAdd, modifier = Modifier.padding(horizontal = 16.dp))
            }
            item(key = "remove") {
                GhostButton("Mitarbeiter entfernen", {
                    val original = m
                    val talkIds = talks.map { it.id }.toSet()
                    OrbitRepository.edit { d -> Mutations.removeMember(d, org.id, m.id) }
                    Undo.offer("${m.name} entfernt") {
                        OrbitRepository.edit { d ->
                            val back = Mutations.upsertMember(d, org.id, original)
                            // The conversations get their name back.
                            back.copy(contacts = back.contacts.map { c ->
                                if (c.id != org.id) c else c.copy(entries = c.entries.map { if (it.id in talkIds && it.memberId == null) it.copy(memberId = original.id) else it })
                            })
                        }
                    }
                    nav.back()
                }, icon = Icons.Rounded.Delete, color = Space.Coral, modifier = Modifier.padding(horizontal = 16.dp).navigationBarsPadding())
            }
        }
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    if (promote) PromoteDialog(m, data, idx, onDismiss = { promote = false }) { intoId ->
        scope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                // Two saved steps: first the person exists on disk, only then the conversations
                // move to them – a kill in between leaves everything at the member.
                val personId = intoId ?: newId().also { id ->
                    val (first, last) = Mutations.splitMemberName(m.name)
                    OrbitRepository.update { d -> d.copy(contacts = d.contacts + io.github.thelony97.orbit.data.Contact(id = id, firstName = first, lastName = last, createdAt = io.github.thelony97.orbit.data.now())) }
                    OrbitRepository.flush()
                }
                OrbitRepository.update { d -> Mutations.memberToContact(d, org.id, m.id, intoId = personId).first }
                OrbitRepository.flush()
                nav.replace(Route.Detail(personId))
            }
        }
    }
    mailEntry?.let { e -> MailEntryDialog(e, onDismiss = { mailEntry = null }) }
    if (editing) MemberEditDialog(m, onDismiss = { editing = false }) { changed ->
        OrbitRepository.edit { d -> Mutations.upsertMember(d, org.id, changed) }
    }
    if (newEntry) EntrySheet(
        title = "Gespräch mit ${m.name}",
        types = data.entryTypes,
        initial = null,
        defaultType = io.github.thelony97.orbit.data.TypeIds.CALL,
        onSave = { e -> OrbitRepository.edit { d -> Mutations.addEntry(d, org.id, e.copy(memberId = e.memberId ?: m.id)) } },
        onDismiss = { newEntry = false },
        members = org.members,
        initialMember = m.id,
    )
    openEntry?.let { e ->
        EntrySheet(
            title = "Eintrag bearbeiten",
            types = data.entryTypes,
            initial = e,
            onSave = { x -> OrbitRepository.edit { d -> Mutations.updateEntry(d, org.id, x) } },
            onDelete = {
                OrbitRepository.edit { d -> Mutations.deleteEntry(d, org.id, e.id) }
                Undo.offer("Eintrag gelöscht") {
                    OrbitRepository.edit { d -> if (d.contacts.any { it.id == org.id && it.entries.any { x -> x.id == e.id } }) d else Mutations.addEntry(d, org.id, e) }
                }
            },
            onDismiss = { openEntry = null },
            members = org.members,
        )
    }
}

/** „Herr Mayer“ → „M“, „Anna Maria Huber“ → „AM“: titles are no initials. */
internal fun memberInitials(name: String): String =
    name.split(' ').filter { it.isNotBlank() && it.first().isLetter() }
        .filterNot { it.lowercase().trimEnd('.') in setOf("herr", "frau", "hr", "fr", "dr", "mag", "di", "ing") }
        .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { name.take(1).uppercase() }

/** Name, Rolle, Durchwahl, Mail, dauerhafte Notiz – all in one dialog. */
@Composable
private fun MemberEditDialog(m: Member, onDismiss: () -> Unit, onSave: (Member) -> Unit) {
    val name = rememberTextFieldState(m.name)
    val role = rememberTextFieldState(m.role)
    val phone = rememberTextFieldState(m.phones.firstOrNull()?.value.orEmpty())
    val mail = rememberTextFieldState(m.emails.firstOrNull()?.value.orEmpty())
    val note = rememberTextFieldState(m.note)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Space.PanelHigh,
        title = { Text(m.name) },
        text = {
            Column(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(name to "Name", role to "Rolle", phone to "Durchwahl", mail to "E-Mail").forEach { (st, label) ->
                    OutlinedTextField(state = st, label = { Text(label) }, lineLimits = TextFieldLineLimits.SingleLine, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(state = note, label = { Text("Notiz") }, lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = name.text.isNotBlank(), onClick = {
                // The first number/address is edited here; further ones stay as they are.
                fun swapFirst(list: List<LabeledValue>, value: String, label: String): List<LabeledValue> {
                    val v = value.trim()
                    return when {
                        v.isEmpty() -> list.drop(1)
                        list.isEmpty() -> listOf(LabeledValue(label, v))
                        else -> listOf(list.first().copy(value = v)) + list.drop(1)
                    }
                }
                onSave(
                    m.copy(
                        name = name.text.toString().trim(),
                        role = role.text.toString().trim(),
                        phones = swapFirst(m.phones, phone.text.toString(), "Durchwahl"),
                        emails = swapFirst(m.emails, mail.text.toString(), "E-Mail"),
                        note = note.text.toString().trim(),
                    ),
                )
                onDismiss()
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** A new member from one typed line (PLAN 3.6); returns the new id. */
fun addMemberFromText(orgId: String, text: String): String? {
    val (name, role) = Members.parse(text) ?: return null
    val id = newId()
    OrbitRepository.edit { d -> Mutations.upsertMember(d, orgId, Member(id, name, role = role)) }
    return id
}

/**
 * „Zu eigenem Kontakt machen“: a new contact, or „Mit bestehendem Kontakt zusammenführen“ –
 * conversations and note go along, the Zuordnung to the organisation comes by itself.
 */
@Composable
private fun PromoteDialog(m: Member, data: OrbitData, idx: Index, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    var into by remember { mutableStateOf<String?>(null) }
    var merging by remember { mutableStateOf(false) }
    val search = rememberTextFieldState(m.name.split(' ').lastOrNull().orEmpty())
    val q = search.text.toString()
    val people = Logic.active(data).filter { !it.isOrg && !it.isMe && !it.archived && Logic.matches(it, q, idx) }
        .sortedBy { Logic.sortKey(it, data.settings.sortByLastName) }.take(30)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Space.PanelHigh,
        title = { Text(m.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    io.github.thelony97.orbit.ui.components.SpaceChip("Neuer Kontakt", !merging, { merging = false; into = null })
                    io.github.thelony97.orbit.ui.components.SpaceChip("Zusammenführen", merging, { merging = true })
                }
                if (merging) {
                    OutlinedTextField(state = search, placeholder = { Text("Person") }, lineLimits = TextFieldLineLimits.SingleLine, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                    LazyColumn(Modifier.heightIn(max = 240.dp)) {
                        items(people, key = { it.id }) { p ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { into = p.id }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.RadioButton(selected = into == p.id, onClick = { into = p.id })
                                Spacer(Modifier.width(6.dp))
                                Text(Logic.displayName(p, data.settings.sortByLastName), color = Space.Text)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !merging || into != null, onClick = { onPick(if (merging) into else null); onDismiss() }) { Text("Übernehmen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
