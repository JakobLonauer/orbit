package io.github.thelony97.orbit.ui.detail

import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.actions.ContactActions
import io.github.thelony97.orbit.data.Anniversary
import io.github.thelony97.orbit.data.CheckItem
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.Defaults
import io.github.thelony97.orbit.data.Entry
import io.github.thelony97.orbit.data.Meeting
import io.github.thelony97.orbit.data.Mutations
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.TypeIds
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.data.now
import io.github.thelony97.orbit.domain.DueState
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.startMillis
import io.github.thelony97.orbit.domain.today
import io.github.thelony97.orbit.domain.zone
import io.github.thelony97.orbit.security.AppLock
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.ConfirmDialog
import io.github.thelony97.orbit.ui.components.Pill
import io.github.thelony97.orbit.ui.components.SectionCard
import io.github.thelony97.orbit.ui.components.contactColor
import io.github.thelony97.orbit.ui.sheets.DayOfYearDialog
import io.github.thelony97.orbit.ui.sheets.EntrySheet
import io.github.thelony97.orbit.util.Fmt
import java.time.temporal.ChronoUnit

private sealed interface Chooser {
    data object Call : Chooser
    data object Sms : Chooser
    data object WhatsApp : Chooser
    data object Mail : Chooser
}

private sealed interface EntryTarget {
    data class New(val typeId: String) : EntryTarget
    data class Existing(val entry: Entry) : EntryTarget
}

@Composable
fun ContactDetailScreen(id: String, data: OrbitData, idx: Index, nav: Nav) {
    val contact = idx.contacts[id]
    if (contact == null) {
        LaunchedEffect(Unit) { nav.back() }
        return
    }
    val ctx = LocalContext.current
    val c: Contact = contact
    val lastFirst = data.settings.sortByLastName
    val color = contactColor(c, idx)
    val todayDate = today()
    val due = Logic.due(c, idx, todayDate)

    var menu by remember { mutableStateOf(false) }
    var entryTarget by remember { mutableStateOf<EntryTarget?>(null) }
    var chooser by remember { mutableStateOf<Chooser?>(null) }
    var rhythmDialog by remember { mutableStateOf(false) }
    var meetingDialog by remember { mutableStateOf(false) }
    var relationDialog by remember { mutableStateOf(false) }
    var anniversaryDialog by remember { mutableStateOf(false) }
    var birthdayDialog by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    var showAllHistory by remember { mutableStateOf(false) }

    fun edit(transform: (Contact) -> Contact) = OrbitRepository.editContact(c.id, transform)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") }
                },
                actions = {
                    IconButton(onClick = { edit { it.copy(favorite = !it.favorite) } }) {
                        Icon(
                            if (c.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            "Favorit",
                            tint = if (c.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { nav.open(Route.Edit(id = c.id)) }) { Icon(Icons.Rounded.Edit, "Bearbeiten") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Mehr") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (c.paused) "Erinnerungen fortsetzen" else "Erinnerungen pausieren") },
                            leadingIcon = { Icon(if (c.paused) Icons.Rounded.PlayCircle else Icons.Rounded.PauseCircle, null) },
                            onClick = { menu = false; edit { it.copy(paused = !it.paused) } },
                        )
                        DropdownMenuItem(
                            text = { Text("Um eine Woche verschieben") },
                            leadingIcon = { Icon(Icons.Rounded.Snooze, null) },
                            onClick = {
                                menu = false
                                edit { it.copy(snoozedUntil = todayDate.plusDays(7).startMillis()) }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (c.archived) "Aus dem Archiv holen" else "Archivieren") },
                            leadingIcon = { Icon(if (c.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, null) },
                            onClick = { menu = false; edit { it.copy(archived = !it.archived) } },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("In den Papierkorb") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = { menu = false; confirmTrash = true },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
        ) {
            // Header
            item(key = "header") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Avatar(c, color, 128.dp, shape = MaterialShapes.Cookie9Sided.toShape())
                    Spacer(Modifier.size(14.dp))
                    Text(
                        Logic.displayName(c, false),
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                        textAlign = TextAlign.Center,
                    )
                    val job = listOf(c.position, c.company).filter { it.isNotBlank() }.joinToString(" · ")
                    if (job.isNotBlank()) {
                        Text(job, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val groups = c.groupIds.mapNotNull { idx.groups[it] }
                    if (groups.isNotEmpty() || c.paused || c.archived) {
                        Spacer(Modifier.size(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            groups.forEach { g -> Pill(g.name, Color(g.color)) }
                            if (c.paused) Pill("Pausiert", MaterialTheme.colorScheme.outline)
                            if (c.archived) Pill("Archiviert", MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            // Quick actions
            item(key = "actions") {
                val number = ContactActions.defaultNumber(c)
                val email = c.emails.firstOrNull { it.value.isNotBlank() }?.value
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
                    ActionTile(
                        Icons.Rounded.Call, "Anrufen", number != null,
                        onClick = { ContactActions.call(ctx, c, null, data.settings) },
                        onLongClick = { chooser = Chooser.Call },
                    )
                    ActionTile(
                        Icons.Rounded.Sms, "SMS", number != null,
                        onClick = { ContactActions.sms(ctx, c, null) },
                        onLongClick = { chooser = Chooser.Sms },
                    )
                    ActionTile(
                        Icons.Rounded.ChatBubble, "WhatsApp", number != null,
                        onClick = { ContactActions.whatsapp(ctx, c, null) },
                        onLongClick = { chooser = Chooser.WhatsApp },
                    )
                    ActionTile(
                        Icons.Rounded.Email, "E-Mail", email != null,
                        onClick = { if (c.emails.count { it.value.isNotBlank() } > 1) chooser = Chooser.Mail else ContactActions.email(ctx, c, null) },
                    )
                    ActionTile(
                        Icons.Rounded.Map, "Karte", c.address.isNotBlank(),
                        onClick = { ContactActions.maps(ctx, c.address) },
                    )
                }
            }

            // Rhythm / status
            item(key = "status") {
                SectionCard(title = "Im Orbit", icon = Icons.Rounded.Timer) {
                    val groupRhythm = c.groupIds.mapNotNull { idx.groups[it]?.rhythmDays }.minOrNull()
                    val rhythmText = when {
                        c.rhythmDays != null -> "Melde dich ${Fmt.rhythm(c.rhythmDays)} (persönlich)"
                        groupRhythm != null -> {
                            val g = c.groupIds.mapNotNull { idx.groups[it] }.filter { it.rhythmDays == groupRhythm }.joinToString { it.name }
                            "Melde dich ${Fmt.rhythm(groupRhythm)} ($g)"
                        }
                        else -> "Kein Rhythmus festgelegt"
                    }
                    InfoRow(label = "Rhythmus · tippen zum Ändern", value = rhythmText, onClick = { rhythmDialog = true })
                    val last = c.entries.maxByOrNull { it.at }
                    if (last != null) {
                        val days = ChronoUnit.DAYS.between(java.time.Instant.ofEpochMilli(last.at).atZone(zone()).toLocalDate(), todayDate)
                        InfoRow(label = "Zuletzt", value = "${idx.types[last.typeId]?.name ?: "Kontakt"} · ${Fmt.ago(days)}")
                    }
                    when {
                        c.archived -> Text("Archiviert – keine Erinnerungen", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        c.paused -> Text("Pausiert – keine Erinnerungen", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        due.rhythm != null && due.dueDate != null -> {
                            val until = ChronoUnit.DAYS.between(todayDate, due.dueDate)
                            val progress = (1f - until.toFloat() / due.rhythm).coerceIn(0f, 1f)
                            Spacer(Modifier.size(6.dp))
                            LinearWavyProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                                color = if (due.state == DueState.DUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                dueLine(until),
                                style = MaterialTheme.typography.titleSmallEmphasized,
                                color = if (due.state == DueState.DUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Spacer(Modifier.size(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { entryTarget = EntryTarget.New(TypeIds.MEET) }) { Text("Kontakt eintragen") }
                        OutlinedButton(onClick = { meetingDialog = true }) { Text("Treffen planen") }
                    }
                }
            }

            // Planned meetings
            val meetings = data.meetings.filter { it.contactId == c.id && !it.resolved }.sortedBy { it.start }
            if (meetings.isNotEmpty()) {
                item(key = "meetings") {
                    SectionCard(title = "Geplante Treffen", icon = Icons.Rounded.Event) {
                        meetings.forEach { m -> MeetingRow(m) }
                    }
                }
            }

            // Topics
            item(key = "topics") {
                SectionCard(title = "Nächstes Mal ansprechen", icon = Icons.Rounded.Checklist) {
                    Checklist(
                        items = c.topics,
                        placeholder = "Thema hinzufügen",
                        doneLabel = "erledigt",
                        onAdd = { text -> edit { it.copy(topics = it.topics + CheckItem(newId(), text, createdAt = now())) } },
                        onToggle = { item -> edit { it.copy(topics = it.topics.map { t -> if (t.id == item.id) t.copy(done = !t.done) else t }) } },
                        onDelete = { item -> edit { it.copy(topics = it.topics.filterNot { t -> t.id == item.id }) } },
                        onClearDone = { edit { it.copy(topics = it.topics.filterNot { t -> t.done }) } },
                    )
                }
            }

            // Contact details
            if (c.phones.isNotEmpty() || c.emails.isNotEmpty() || c.address.isNotBlank()) {
                item(key = "contact") {
                    SectionCard(title = "Kontakt", icon = Icons.Rounded.Person) {
                        c.phones.forEachIndexed { i, p ->
                            InfoRow(
                                label = p.label + if (i == c.defaultPhone && c.phones.size > 1) " · Standard" else "",
                                value = p.value,
                                onClick = { ContactActions.call(ctx, c, p.value, data.settings) },
                                trailing = {
                                    IconButton(onClick = { ContactActions.sms(ctx, c, p.value) }) { Icon(Icons.Rounded.Sms, "SMS") }
                                },
                            )
                        }
                        c.emails.forEach { e ->
                            InfoRow(label = e.label, value = e.value, onClick = { ContactActions.email(ctx, c, e.value) })
                        }
                        if (c.address.isNotBlank()) {
                            InfoRow(label = "Adresse", value = c.address, onClick = { ContactActions.maps(ctx, c.address) })
                        }
                    }
                }
            }

            // Dates
            item(key = "dates") {
                SectionCard(
                    title = "Geburtstag & Jahrestage",
                    icon = Icons.Rounded.Cake,
                    action = {
                        IconButton(onClick = { anniversaryDialog = true }) { Icon(Icons.Rounded.Add, "Jahrestag hinzufügen") }
                    },
                ) {
                    val b = c.birthday
                    if (b != null) {
                        val days = Logic.daysUntil(b, todayDate)
                        val age = Logic.turningAge(b, todayDate)
                        InfoRow(
                            label = "Geburtstag · " + (if (days == 0L) "heute! 🎂" else Fmt.until(days)) + (age?.let { " · wird $it" } ?: ""),
                            value = Fmt.dayOfYear(b),
                            onClick = { birthdayDialog = true },
                        )
                    } else {
                        TextButton(onClick = { birthdayDialog = true }) { Text("Geburtstag eintragen") }
                    }
                    c.anniversaries.sortedBy { Logic.daysUntil(it.date, todayDate) }.forEach { a ->
                        val days = Logic.daysUntil(a.date, todayDate)
                        val years = a.date.year?.let { Logic.nextOccurrence(a.date, todayDate).year - it }
                        InfoRow(
                            label = a.label + " · " + (if (days == 0L) "heute" else Fmt.until(days)) + (years?.takeIf { it > 0 }?.let { " · $it Jahre" } ?: ""),
                            value = Fmt.dayOfYear(a.date),
                            trailing = {
                                IconButton(onClick = { edit { it.copy(anniversaries = it.anniversaries.filterNot { x -> x.id == a.id }) } }) {
                                    Icon(Icons.Rounded.Close, "Entfernen", Modifier.size(18.dp))
                                }
                            },
                        )
                    }
                }
            }

            // Relations
            item(key = "relations") {
                val rels = data.relations.filter { it.fromId == c.id || it.toId == c.id }
                SectionCard(
                    title = "Beziehungen",
                    icon = Icons.Rounded.Link,
                    action = { IconButton(onClick = { relationDialog = true }) { Icon(Icons.Rounded.Add, "Beziehung hinzufügen") } },
                ) {
                    if (rels.isEmpty()) {
                        Text(
                            "Verknüpfe Partner, Familie oder Kolleg:innen – die Gegenseite wird automatisch eingetragen.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    rels.forEach { r ->
                        val outgoing = r.fromId == c.id
                        val otherId = if (outgoing) r.toId else r.fromId
                        val other = idx.contacts[otherId]
                        if (other != null && other.deletedAt == null) {
                            val kind = Defaults.relationKind(r.kind)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(other, contactColor(other, idx), 36.dp)
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f)) {
                                    InfoRow(
                                        label = if (outgoing) kind.backward else kind.forward,
                                        value = Logic.displayName(other, lastFirst),
                                        onClick = { nav.open(Route.Detail(other.id)) },
                                    )
                                }
                                IconButton(onClick = { OrbitRepository.edit { d -> Mutations.removeRelation(d, r.id) } }) {
                                    Icon(Icons.Rounded.Close, "Entfernen", Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Gifts
            item(key = "gifts") {
                SectionCard(title = "Geschenkideen", icon = Icons.Rounded.CardGiftcard) {
                    Checklist(
                        items = c.gifts,
                        placeholder = "Idee notieren",
                        doneLabel = "verschenkt",
                        onAdd = { text -> edit { it.copy(gifts = it.gifts + CheckItem(newId(), text, createdAt = now())) } },
                        onToggle = { item -> edit { it.copy(gifts = it.gifts.map { g -> if (g.id == item.id) g.copy(done = !g.done) else g }) } },
                        onDelete = { item -> edit { it.copy(gifts = it.gifts.filterNot { g -> g.id == item.id }) } },
                        onClearDone = { edit { it.copy(gifts = it.gifts.filterNot { g -> g.done }) } },
                    )
                }
            }

            // About
            if (c.howWeMet.isNotBlank() || c.notes.isNotBlank()) {
                item(key = "about") {
                    SectionCard(title = "Über ${c.firstName.ifBlank { "diese Person" }}", icon = Icons.Rounded.Notes) {
                        if (c.howWeMet.isNotBlank()) InfoRow(label = "Wie kennengelernt", value = c.howWeMet)
                        if (c.notes.isNotBlank()) InfoRow(label = "Notizen", value = c.notes)
                    }
                }
            }

            // History
            item(key = "history-header") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 28.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(10.dp))
                    Text("Verlauf", style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.weight(1f))
                    Text(
                        "${c.entries.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = { entryTarget = EntryTarget.New(TypeIds.MEET) }) { Icon(Icons.Rounded.Add, "Eintrag hinzufügen") }
                }
            }
            if (c.entries.isEmpty()) {
                item(key = "history-empty") {
                    Text(
                        "Noch nichts eingetragen. Anrufe und SMS erscheinen hier automatisch.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 28.dp),
                    )
                }
            }
            val visible = if (showAllHistory) c.entries else c.entries.take(30)
            items(visible, key = { "e-${it.id}" }) { e ->
                EntryRow(e, idx.types[e.typeId], onClick = { entryTarget = EntryTarget.Existing(e) })
            }
            if (!showAllHistory && c.entries.size > 30) {
                item(key = "history-more") {
                    TextButton(onClick = { showAllHistory = true }, modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text("Alle ${c.entries.size} Einträge anzeigen")
                    }
                }
            }
        }
    }

    // Sheets & dialogs
    when (val target = entryTarget) {
        is EntryTarget.New -> EntrySheet(
            title = "Kontakt mit ${c.firstName.ifBlank { Logic.displayName(c, lastFirst) }}",
            types = data.entryTypes,
            initial = null,
            defaultType = target.typeId,
            onSave = { e -> OrbitRepository.edit { d -> Mutations.addEntry(d, c.id, e) } },
            onDismiss = { entryTarget = null },
        )
        is EntryTarget.Existing -> EntrySheet(
            title = "Eintrag bearbeiten",
            types = data.entryTypes,
            initial = target.entry,
            onSave = { e -> OrbitRepository.edit { d -> Mutations.updateEntry(d, c.id, e) } },
            onDelete = { OrbitRepository.edit { d -> Mutations.deleteEntry(d, c.id, target.entry.id) } },
            onDismiss = { entryTarget = null },
        )
        null -> Unit
    }

    chooser?.let { which ->
        val values = if (which == Chooser.Mail) c.emails else c.phones
        ValueChooserDialog(
            title = when (which) {
                Chooser.Call -> "Welche Nummer anrufen?"
                Chooser.Sms -> "SMS an welche Nummer?"
                Chooser.WhatsApp -> "WhatsApp an welche Nummer?"
                Chooser.Mail -> "An welche Adresse?"
            },
            values = values,
            defaultIndex = if (which == Chooser.Mail) null else c.defaultPhone,
            onDismiss = { chooser = null },
            onSetDefault = if (which == Chooser.Mail) null else { i -> edit { it.copy(defaultPhone = i) } },
            onPick = { value ->
                when (which) {
                    Chooser.Call -> ContactActions.call(ctx, c, value, data.settings)
                    Chooser.Sms -> ContactActions.sms(ctx, c, value)
                    Chooser.WhatsApp -> ContactActions.whatsapp(ctx, c, value)
                    Chooser.Mail -> ContactActions.email(ctx, c, value)
                }
            },
        )
    }

    if (rhythmDialog) {
        RhythmDialog(
            current = c.rhythmDays,
            groupRhythm = c.groupIds.mapNotNull { idx.groups[it]?.rhythmDays }.minOrNull(),
            onDismiss = { rhythmDialog = false },
            onPick = { days -> edit { it.copy(rhythmDays = days) } },
        )
    }

    if (meetingDialog) {
        MeetingDialog(
            name = c.firstName.ifBlank { Logic.displayName(c, lastFirst) },
            onDismiss = { meetingDialog = false },
            onPlan = { draft ->
                val start = draft.start.atZone(zone()).toInstant().toEpochMilli()
                val end = start + draft.durationMin * 60_000L
                val meeting = Meeting(newId(), c.id, start, end, draft.place)
                OrbitRepository.edit { d -> Mutations.upsertMeeting(d, meeting) }
                val intent = Intent(Intent.ACTION_INSERT)
                    .setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
                    .putExtra(CalendarContract.Events.TITLE, "Treffen mit ${Logic.displayName(c, false)}")
                    .putExtra(CalendarContract.Events.EVENT_LOCATION, draft.place)
                try {
                    AppLock.allowExternal()
                    ctx.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(ctx, "Keine Kalender-App gefunden – Treffen ist trotzdem gespeichert", Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    if (relationDialog) {
        RelationDialog(
            contact = c,
            candidates = Logic.active(data),
            idx = idx,
            lastFirst = lastFirst,
            onDismiss = { relationDialog = false },
            onPick = { otherId, kind -> OrbitRepository.edit { d -> Mutations.addRelation(d, c.id, otherId, kind) } },
        )
    }

    if (anniversaryDialog) {
        AnniversaryDialog(
            onDismiss = { anniversaryDialog = false },
            onPick = { label, date -> edit { it.copy(anniversaries = it.anniversaries + Anniversary(newId(), label, date)) } },
        )
    }

    if (birthdayDialog) {
        DayOfYearDialog(
            initial = c.birthday,
            onDismiss = { birthdayDialog = false },
            onPick = { d -> edit { it.copy(birthday = d) } },
        )
    }

    if (confirmTrash) {
        ConfirmDialog(
            title = "In den Papierkorb?",
            text = "${Logic.displayName(c, lastFirst)} bleibt 30 Tage im Papierkorb und kann dort wiederhergestellt werden.",
            confirm = "Löschen",
            onConfirm = {
                OrbitRepository.edit { d -> Mutations.trash(d, c.id) }
                nav.back()
            },
            onDismiss = { confirmTrash = false },
        )
    }
}

@Composable
private fun MeetingRow(m: Meeting) {
    val past = m.end < now()
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            Fmt.dateTime(m.start) + if (m.place.isNotBlank()) " · ${m.place}" else "",
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (past) {
                Text("Hat es stattgefunden?", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { OrbitRepository.edit { d -> Mutations.confirmMeeting(d, m.id, true) } }) { Text("Ja") }
                TextButton(onClick = { OrbitRepository.edit { d -> Mutations.confirmMeeting(d, m.id, false) } }) { Text("Nein") }
            } else {
                Text(
                    Fmt.until(ChronoUnit.DAYS.between(today(), java.time.Instant.ofEpochMilli(m.start).atZone(zone()).toLocalDate())),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    OrbitRepository.edit { d -> d.copy(meetings = d.meetings.filterNot { it.id == m.id }) }
                }) { Text("Absagen") }
            }
        }
    }
}
