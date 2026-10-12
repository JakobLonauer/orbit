package io.github.thelony97.orbit.ui.detail

import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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
import io.github.thelony97.orbit.domain.toLocalDate
import io.github.thelony97.orbit.domain.today
import io.github.thelony97.orbit.domain.zone
import io.github.thelony97.orbit.security.AppLock
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.Session
import io.github.thelony97.orbit.ui.components.KindMark
import io.github.thelony97.orbit.geo.Geo
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.ConfirmDialog
import io.github.thelony97.orbit.ui.components.GhostButton
import io.github.thelony97.orbit.ui.components.GlassCard
import io.github.thelony97.orbit.ui.components.GlowBar
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.OrbButton
import io.github.thelony97.orbit.ui.components.Pill
import io.github.thelony97.orbit.ui.components.SectionCard
import io.github.thelony97.orbit.ui.components.SectionTitle
import io.github.thelony97.orbit.ui.components.SpaceBackground
import io.github.thelony97.orbit.ui.components.Stat
import io.github.thelony97.orbit.ui.components.contactColor
import io.github.thelony97.orbit.ui.sheets.DayOfYearDialog
import io.github.thelony97.orbit.ui.sheets.EntrySheet
import io.github.thelony97.orbit.ui.theme.Space
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
private fun SmallIconAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Icon(
        icon, description,
        tint = Space.Violet,
        modifier = Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onClick).padding(5.dp),
    )
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
    val isDue = due.state == DueState.DUE

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

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    SpaceBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = top + 6.dp, bottom = bottom + 32.dp),
        ) {
            // Top bar
            item(key = "bar") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OrbButton(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", nav.back, size = 38.dp)
                    Spacer(Modifier.weight(1f))
                    if (!c.isMe) {
                        OrbButton(
                            if (c.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Favorit",
                            { edit { it.copy(favorite = !it.favorite) } },
                            size = 38.dp,
                            accent = if (c.favorite) Space.Orange else null,
                        )
                    }
                    OrbButton(Icons.Rounded.Edit, "Bearbeiten", { nav.open(Route.Edit(id = c.id)) }, size = 38.dp)
                    if (!c.isMe) Box {
                        OrbButton(Icons.Rounded.MoreVert, "Mehr", { menu = true }, size = 38.dp)
                        DropdownMenu(
                            expanded = menu,
                            onDismissRequest = { menu = false },
                            containerColor = Space.PanelHigh,
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (c.paused) "Erinnerungen fortsetzen" else "Erinnerungen pausieren") },
                                leadingIcon = { Icon(if (c.paused) Icons.Rounded.PlayCircle else Icons.Rounded.PauseCircle, null) },
                                onClick = { menu = false; edit { it.copy(paused = !it.paused) } },
                            )
                            DropdownMenuItem(
                                text = { Text("Um eine Woche verschieben") },
                                leadingIcon = { Icon(Icons.Rounded.Snooze, null) },
                                onClick = { menu = false; edit { it.copy(snoozedUntil = todayDate.plusDays(7).startMillis()) } },
                            )
                            DropdownMenuItem(
                                text = { Text(if (c.archived) "Aus dem Archiv holen" else "Archivieren") },
                                leadingIcon = { Icon(if (c.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, null) },
                                onClick = { menu = false; edit { it.copy(archived = !it.archived) } },
                            )
                            HorizontalDivider(color = Space.Line)
                            DropdownMenuItem(
                                text = { Text("In den Papierkorb", color = Space.Coral) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Space.Coral) },
                                onClick = { menu = false; confirmTrash = true },
                            )
                        }
                    }
                }
            }

            // Hero
            item(key = "hero") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(c, color, 72.dp, shape = MaterialShapes.Cookie9Sided.toShape(), glowColor = if (isDue) Space.Coral else color)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            Logic.displayName(c, false),
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                            color = Space.Text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val job = if (c.isOrg) c.position else listOf(c.position, c.company).filter { it.isNotBlank() }.joinToString(" · ")
                        if (job.isNotBlank()) {
                            Text(job, style = MaterialTheme.typography.bodySmall, color = Space.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        val groups = c.groupIds.mapNotNull { idx.groups[it] }
                        if (groups.isNotEmpty() || c.paused || c.archived || c.isOrg || c.isMe) {
                            FlowRow(
                                Modifier.padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                if (c.isOrg) Pill("Organisation · ${Defaults.orgCategory(c.orgCategory).label}", Space.Blue)
                                if (c.isMe) Pill("Das bist du", Space.Orange)
                                groups.forEach { g -> Pill(g.name, Color(g.color)) }
                                if (c.paused) Pill("pausiert", Space.TextDim)
                                if (c.archived) Pill("archiviert", Space.TextDim)
                            }
                        }
                    }
                }
            }

            // Quick actions
            if (!c.isMe) item(key = "actions") {
                val number = ContactActions.defaultNumber(c)
                val email = c.emails.firstOrNull { it.value.isNotBlank() }?.value
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ActionOrb(
                        Icons.Rounded.Call, "Anrufen", number != null,
                        onClick = { ContactActions.call(ctx, c, null, data.settings) },
                        onLongClick = { chooser = Chooser.Call },
                        accent = Space.Teal,
                    )
                    ActionOrb(Icons.Rounded.Sms, "SMS", number != null, onClick = { ContactActions.sms(ctx, c, null) }, onLongClick = { chooser = Chooser.Sms })
                    ActionOrb(Icons.Rounded.ChatBubble, "WhatsApp", number != null, onClick = { ContactActions.whatsapp(ctx, c, null) }, onLongClick = { chooser = Chooser.WhatsApp })
                    ActionOrb(
                        Icons.Rounded.Email, "E-Mail", email != null,
                        onClick = { if (c.emails.count { it.value.isNotBlank() } > 1) chooser = Chooser.Mail else ContactActions.email(ctx, c, null) },
                    )
                    ActionOrb(Icons.Rounded.Map, "Karte", c.address.isNotBlank(), onClick = { ContactActions.maps(ctx, c.address) })
                }
            }

            // Status strip
            if (!c.isMe) item(key = "status") {
                GlassCard(Modifier.padding(top = 8.dp), padding = 14.dp) {
                    val last = c.entries.maxByOrNull { it.at }
                    val lastDays = last?.let { ChronoUnit.DAYS.between(it.at.toLocalDate(), todayDate) }
                    val until = due.dueDate?.let { ChronoUnit.DAYS.between(todayDate, it) }
                    Row(Modifier.fillMaxWidth()) {
                        Stat(lastDays?.let { Fmt.short(it) } ?: "–", "zuletzt")
                        Stat(due.rhythm?.let { "${it} T" } ?: "–", "Rhythmus", onClick = { rhythmDialog = true })
                        Stat(
                            when {
                                c.archived -> "Archiv"
                                c.paused -> "Pause"
                                until != null -> dueLine(until)
                                else -> "–"
                            },
                            "fällig",
                            color = if (isDue) Space.Coral else Space.Text,
                        )
                    }
                    if (due.rhythm != null && until != null && !c.paused && !c.archived) {
                        Spacer(Modifier.size(10.dp))
                        GlowBar((1f - until.toFloat() / due.rhythm).coerceIn(0f, 1f), if (isDue) Space.Coral else Space.Teal)
                    }
                    val subline = when {
                        last != null -> "${idx.types[last.typeId]?.name ?: "Kontakt"} ${Fmt.ago(lastDays ?: 0)} · Rhythmus antippen zum Ändern"
                        else -> "Noch kein Kontakt eingetragen"
                    }
                    Text(subline, style = MaterialTheme.typography.labelMedium, color = Space.TextFaint, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlowButton("Eintragen", { entryTarget = EntryTarget.New(TypeIds.MEET) }, icon = Icons.Rounded.EditNote)
                        GhostButton("Treffen planen", { meetingDialog = true }, icon = Icons.Rounded.Event)
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
            if (!c.isMe) item(key = "topics") {
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
            if (c.phones.isNotEmpty() || c.emails.isNotEmpty() || c.address.isNotBlank() || c.website.isNotBlank()) {
                item(key = "contact") {
                    SectionCard(title = "Kontakt", icon = if (c.isOrg) Icons.Rounded.Business else Icons.Rounded.Person) {
                        c.phones.forEachIndexed { i, p ->
                            InfoRow(
                                label = p.label + if (i == c.defaultPhone && c.phones.size > 1) " · Standard" else "",
                                value = p.value,
                                onClick = { ContactActions.call(ctx, c, p.value, data.settings) },
                                trailing = { SmallIconAction(Icons.Rounded.Sms, "SMS") { ContactActions.sms(ctx, c, p.value) } },
                            )
                        }
                        c.emails.forEach { e ->
                            InfoRow(label = e.label, value = e.value, onClick = { ContactActions.email(ctx, c, e.value) })
                        }
                        if (c.address.isNotBlank()) {
                            val mePoint = if (c.isMe) null else Logic.me(data)?.let { Geo.point(it) }
                            val here = Geo.point(c)
                            val distance = if (mePoint != null && here != null) " · ${Geo.formatDistance(Geo.distanceKm(mePoint, here))} von dir" else ""
                            InfoRow(
                                label = "Adresse$distance",
                                value = c.address,
                                onClick = { ContactActions.maps(ctx, c.address) },
                                trailing = {
                                    SmallIconAction(Icons.Rounded.Map, "Auf der Orbit-Karte zeigen") {
                                        Session.mapFocus = c.id
                                        nav.home(3)
                                    }
                                },
                            )
                        }
                        if (c.website.isNotBlank()) {
                            InfoRow(label = "Webseite", value = c.website, onClick = { ContactActions.web(ctx, c.website) })
                        }
                    }
                }
            }

            // Mails from this contact
            if (c.emails.any { it.value.contains('@') } && !c.isMe) {
                item(key = "mails") {
                    MailSection(c, onSetup = { nav.open(Route.MailAccounts) })
                }
            }

            // Dates
            if (!c.isOrg) item(key = "dates") {
                SectionCard(
                    title = "Geburtstag & Jahrestage",
                    icon = Icons.Rounded.Cake,
                    action = { SmallIconAction(Icons.Rounded.Add, "Jahrestag hinzufügen") { anniversaryDialog = true } },
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
                        InfoRow(label = "Tippen zum Eintragen", value = "Kein Geburtstag", onClick = { birthdayDialog = true })
                    }
                    c.anniversaries.sortedBy { Logic.daysUntil(it.date, todayDate) }.forEach { a ->
                        val days = Logic.daysUntil(a.date, todayDate)
                        val years = a.date.year?.let { Logic.nextOccurrence(a.date, todayDate).year - it }
                        InfoRow(
                            label = a.label + " · " + (if (days == 0L) "heute" else Fmt.until(days)) + (years?.takeIf { it > 0 }?.let { " · $it Jahre" } ?: ""),
                            value = Fmt.dayOfYear(a.date),
                            trailing = {
                                SmallIconAction(Icons.Rounded.Close, "Entfernen") {
                                    edit { it.copy(anniversaries = it.anniversaries.filterNot { x -> x.id == a.id }) }
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
                    action = {
                        Row {
                            if (rels.isNotEmpty()) {
                                SmallIconAction(Icons.Rounded.Hub, "Im Netz zeigen") {
                                    Session.networkRoot = if (c.isMe) null else c.id
                                    nav.home(2)
                                }
                            }
                            SmallIconAction(Icons.Rounded.Add, "Beziehung hinzufügen") { relationDialog = true }
                        }
                    },
                ) {
                    if (rels.isEmpty()) {
                        Text(
                            when {
                                c.isOrg -> "Wer arbeitet hier, wer ist hier Patient:in oder Mitglied? Verknüpfe Menschen mit dieser Organisation."
                                c.isMe -> "Wer gehört zu dir? Partner:in, Eltern, Geschwister, Arbeitgeber … – daraus entsteht dein Netz."
                                else -> "Partner, Familie, Kolleg:innen verknüpfen – die Gegenseite entsteht automatisch."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Space.TextFaint,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    rels.forEach { r ->
                        val outgoing = r.fromId == c.id
                        val other = idx.contacts[if (outgoing) r.toId else r.fromId]
                        if (other != null && other.deletedAt == null) {
                            val kind = Defaults.relationKind(r.kind)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { nav.open(Route.Detail(other.id)) }
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(other, contactColor(other, idx), 30.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(Logic.displayName(other, lastFirst), style = MaterialTheme.typography.bodyMedium, color = Space.Text)
                                        if (other.isOrg) {
                                            Spacer(Modifier.width(5.dp))
                                            KindMark(other, size = 12.dp)
                                        }
                                    }
                                    Text(if (outgoing) kind.backward else kind.forward, style = MaterialTheme.typography.labelMedium, color = Space.TextFaint)
                                }
                                SmallIconAction(Icons.Rounded.Close, "Entfernen") {
                                    OrbitRepository.edit { d -> Mutations.removeRelation(d, r.id) }
                                }
                            }
                        }
                    }
                }
            }

            // Gifts
            if (!c.isOrg && !c.isMe) item(key = "gifts") {
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
                    SectionCard(title = if (c.isOrg) "Notizen" else "Über ${c.firstName.ifBlank { "diese Person" }}", icon = Icons.AutoMirrored.Rounded.Notes) {
                        if (c.howWeMet.isNotBlank()) InfoRow(label = "Wie kennengelernt", value = c.howWeMet)
                        if (c.notes.isNotBlank()) InfoRow(label = "Notizen", value = c.notes)
                    }
                }
            }

            // Photo folder
            item(key = "gallery") { GallerySection(c) }

            // History
            if (!c.isMe) item(key = "history-header") {
                SectionTitle(
                    "Verlauf · ${c.entries.size}",
                    Icons.Rounded.History,
                    modifier = Modifier.padding(start = 20.dp, end = 14.dp, top = 16.dp, bottom = 6.dp),
                    action = { SmallIconAction(Icons.Rounded.Add, "Eintrag hinzufügen") { entryTarget = EntryTarget.New(TypeIds.MEET) } },
                )
            }
            if (c.entries.isEmpty() && !c.isMe) {
                item(key = "history-empty") {
                    Text(
                        "Noch nichts eingetragen. Anrufe und SMS erscheinen hier automatisch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Space.TextFaint,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            val visible = if (showAllHistory) c.entries else c.entries.take(30)
            itemsIndexed(visible, key = { _, e -> "e-${e.id}" }) { i, e ->
                EntryRow(e, idx.types[e.typeId], last = i == visible.lastIndex, onClick = { entryTarget = EntryTarget.Existing(e) })
            }
            if (!showAllHistory && c.entries.size > 30) {
                item(key = "history-more") {
                    Text(
                        "Alle ${c.entries.size} Einträge anzeigen",
                        style = MaterialTheme.typography.labelLarge,
                        color = Space.Violet,
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable { showAllHistory = true }
                            .padding(6.dp),
                    )
                }
            }
        }
    }

    // Sheets & dialogs
    when (val target = entryTarget) {
        is EntryTarget.New -> EntrySheet(
            title = "Kontakt mit ${Logic.shortName(c)}",
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
            groupRhythm = if (c.isOrg) null else c.groupIds.mapNotNull { idx.groups[it]?.rhythmDays }.minOrNull(),
            onDismiss = { rhythmDialog = false },
            onPick = { days -> edit { it.copy(rhythmDays = days) } },
        )
    }

    if (meetingDialog) {
        MeetingDialog(
            name = Logic.shortName(c),
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
            candidates = Logic.active(data).filterNot { it.archived && !it.isMe },
            idx = idx,
            lastFirst = lastFirst,
            onDismiss = { relationDialog = false },
            onPick = { otherId, kind, reversed ->
                OrbitRepository.edit { d ->
                    if (reversed) Mutations.addRelation(d, otherId, c.id, kind) else Mutations.addRelation(d, c.id, otherId, kind)
                }
            },
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
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                Fmt.dateTime(m.start) + if (m.place.isNotBlank()) " · ${m.place}" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = Space.Text,
            )
            Text(
                if (past) "Hat es stattgefunden?"
                else Fmt.until(ChronoUnit.DAYS.between(today(), m.start.toLocalDate())),
                style = MaterialTheme.typography.labelMedium,
                color = if (past) Space.Orange else Space.TextFaint,
            )
        }
        if (past) {
            Text(
                "Ja", style = MaterialTheme.typography.labelLargeEmphasized, color = Space.Teal,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                    OrbitRepository.edit { d -> Mutations.confirmMeeting(d, m.id, true) }
                }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Text(
                "Nein", style = MaterialTheme.typography.labelLargeEmphasized, color = Space.TextDim,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                    OrbitRepository.edit { d -> Mutations.confirmMeeting(d, m.id, false) }
                }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        } else {
            Text(
                "Absagen", style = MaterialTheme.typography.labelLarge, color = Space.Coral,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                    OrbitRepository.edit { d -> d.copy(meetings = d.meetings.filterNot { it.id == m.id }) }
                }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
