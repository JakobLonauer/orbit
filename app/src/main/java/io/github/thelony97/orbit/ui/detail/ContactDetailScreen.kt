package io.github.thelony97.orbit.ui.detail

import io.github.thelony97.orbit.core.domain.DateMath
import io.github.thelony97.orbit.ui.components.Banner
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import io.github.thelony97.orbit.ui.components.rememberPhoto
import io.github.thelony97.orbit.ui.components.rememberPhotoAccent
import io.github.thelony97.orbit.ui.components.sharedContact
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.rounded.Search
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.SpaceSearchField
import io.github.thelony97.orbit.ui.components.entryIcon
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
import androidx.compose.material.icons.rounded.Forum
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
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sms
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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.thelony97.orbit.actions.ContactActions
import io.github.thelony97.orbit.core.domain.migration.MigrateV1
import io.github.thelony97.orbit.core.model.OccasionKind
import io.github.thelony97.orbit.core.model.CustomField
import io.github.thelony97.orbit.core.model.Link as LinkTile
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import io.github.thelony97.orbit.actions.ShareContact
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
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.domain.Logic
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
import io.github.thelony97.orbit.ui.components.CountStat
import io.github.thelony97.orbit.ui.components.Undo
import io.github.thelony97.orbit.ui.components.GlassCard
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

private enum class NoteTarget { Notes, Pinned }

/** One relation as seen from this contact: the other side and the label in this direction. */
private data class RelView(val id: String, val other: Contact, val label: String)

@Composable
private fun RelationRow(r: RelView, idx: Index, lastFirst: Boolean, onOpen: () -> Unit, trailing: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Organisations keep their square tile (Avatar), people are round.
        Avatar(r.other, contactColor(r.other, idx), 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(Logic.displayName(r.other, lastFirst), style = MaterialTheme.typography.bodyMedium, color = Space.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(r.label, style = MaterialTheme.typography.labelMedium, color = Space.TextFaint)
        }
        trailing()
    }
}

private sealed interface EntryTarget {
    data class New(val typeId: String) : EntryTarget
    data class Existing(val entry: Entry) : EntryTarget
}

@Composable
private fun SmallIconAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    // One rule for trailing icons: actions are violet, removing is quiet (W9).
    val remove = icon == Icons.Rounded.Close
    Icon(
        icon, description,
        tint = if (remove) Space.TextFaint else Space.Violet,
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

    var menu by remember { mutableStateOf(false) }
    var entryTarget by remember { mutableStateOf<EntryTarget?>(null) }
    var chooser by remember { mutableStateOf<Chooser?>(null) }
    var meetingDialog by remember { mutableStateOf(false) }
    var relationDialog by remember { mutableStateOf(false) }
    // Anlass bearbeiten: owner id + the occasion (null = a new one at this contact).
    var occasionEdit by remember { mutableStateOf<Pair<String, io.github.thelony97.orbit.data.Anniversary?>?>(null) }
    var birthdayDialog by remember { mutableStateOf(false) }
    var showAllHistory by remember { mutableStateOf(false) }
    var linkDialog by remember { mutableStateOf(false) }
    var qrDialog by remember { mutableStateOf(false) }
    var fieldDialog by remember { mutableStateOf(false) }
    var noteDialog by remember { mutableStateOf<NoteTarget?>(null) }
    // Sections opened from the add row while still empty (topics, gifts).
    var opened by remember { mutableStateOf(setOf<String>()) }
    val gallery = rememberGalleryImport(c.id)
    val drawnEntries = remember { HashSet<String>() }
    var historyType by remember { mutableStateOf<String?>(null) }
    val historySearch = rememberTextFieldState()

    fun edit(transform: (Contact) -> Contact) = OrbitRepository.editContact(c.id, transform)

    /** Removes a relation with an undo that brings back exactly that one. */
    fun removeRelation(r: RelView) {
        val original = data.relations.firstOrNull { it.id == r.id } ?: return
        OrbitRepository.edit { d -> Mutations.removeRelation(d, r.id) }
        Undo.offer("${Logic.shortName(r.other)} entfernt") {
            OrbitRepository.edit { d -> if (d.relations.any { it.id == original.id }) d else d.copy(relations = d.relations + original) }
        }
    }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val listState = rememberLazyListState()
    val photo = rememberPhoto(c.photo)
    // Kopf in der Farbe der Person (PLAN 1.3): palette from the photo; "Ich" stays the sun,
    // organisations stay blue. Glides over from the normal colour once it is known.
    val photoAccent = rememberPhotoAccent(c.photo, photo)
    val head by androidx.compose.animation.animateColorAsState(
        // Archived people are a distant grey star until they come back (PLAN 1.3 Archiv).
        if (c.archived) Space.TextFaint else if (c.isOrg || c.isMe) color else photoAccent ?: color,
        androidx.compose.animation.core.tween(io.github.thelony97.orbit.core.designsystem.Motion.EMPHASIZED_MS),
        label = "head",
    )
    SpaceBackground {
        // Signature (PLAN 1.3): the head glows in the person's colour, the profile picture lies
        // blurred behind it; both fade out as the page scrolls (read in the draw phase only).
        Box(
            Modifier
                .fillMaxWidth()
                .height(380.dp)
                .graphicsLayer {
                    val scrolled = if (listState.firstVisibleItemIndex > 0) 1f else (listState.firstVisibleItemScrollOffset / 600f).coerceIn(0f, 1f)
                    alpha = 1f - scrolled
                },
        ) {
            if (photo != null) {
                androidx.compose.foundation.Image(
                    photo, null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.matchParentSize().blur(48.dp).graphicsLayer { alpha = 0.32f },
                )
            }
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(listOf(head.copy(alpha = 0.30f), head.copy(alpha = 0.08f), head.copy(alpha = 0f))),
                ),
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = top + 6.dp, bottom = bottom + 32.dp),
        ) {
            // Top bar
            item(key = "bar") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OrbButton(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", nav.back, size = 38.dp)
                    Spacer(Modifier.weight(1f))
                    if (!c.isMe && !c.archived) {
                        OrbButton(
                            if (c.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Favorit",
                            { edit { it.copy(favorite = !it.favorite) } },
                            size = 38.dp,
                            accent = if (c.favorite) Space.Orange else null,
                        )
                    }
                    OrbButton(Icons.Rounded.Edit, "Bearbeiten", { nav.open(Route.Edit(id = c.id)) }, size = 38.dp)
                    Box {
                        OrbButton(Icons.Rounded.MoreVert, "Mehr", { menu = true }, size = 38.dp)
                        DropdownMenu(
                            expanded = menu,
                            onDismissRequest = { menu = false },
                            containerColor = Space.PanelHigh,
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            // PLAN 3.3: share as vCard / QR – also your own card.
                            DropdownMenuItem(
                                text = { Text("QR-Code") },
                                leadingIcon = { Icon(Icons.Rounded.QrCode2, null) },
                                onClick = { menu = false; qrDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Als vCard teilen") },
                                leadingIcon = { Icon(Icons.Rounded.Share, null) },
                                onClick = { menu = false; ShareContact.vcard(ctx, c) },
                            )
                            if (!c.isMe) HorizontalDivider(color = Space.Line)
                            if (!c.isMe) DropdownMenuItem(
                                text = { Text(if (c.archived) "Aus dem Archiv holen" else "Archivieren") },
                                leadingIcon = { Icon(if (c.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, null) },
                                onClick = {
                                    menu = false
                                    val wasArchived = c.archived
                                    edit { it.copy(archived = !wasArchived, archivedAt = if (wasArchived) null else now()) }
                                    if (!wasArchived) {
                                        Undo.offer("${Logic.shortName(c)} archiviert") { edit { it.copy(archived = false, archivedAt = null) } }
                                        nav.back()
                                    }
                                },
                            )
                            if (!c.isMe) HorizontalDivider(color = Space.Line)
                            if (!c.isMe) DropdownMenuItem(
                                text = { Text("In den Papierkorb", color = Space.Coral) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Space.Coral) },
                                onClick = {
                                    // Rückgängig statt Nachfragen (PLAN 1.2); the trash keeps it 30 days anyway.
                                    menu = false
                                    OrbitRepository.edit { d -> Mutations.trash(d, c.id) }
                                    Undo.offer("${Logic.shortName(c)} im Papierkorb") { OrbitRepository.edit { d -> Mutations.restore(d, c.id) } }
                                    nav.back()
                                },
                            )
                        }
                    }
                }
            }

            // Hero
            item(key = "hero") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(c, head, 72.dp, modifier = Modifier.sharedContact("avatar-${c.id}"), shape = MaterialShapes.Cookie9Sided.toShape(), glowColor = head)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        // Long names shrink cleanly instead of being cut (PLAN 1.4).
                        val nameStyle = MaterialTheme.typography.headlineSmallEmphasized
                        androidx.compose.foundation.text.BasicText(
                            Logic.displayName(c, false),
                            modifier = Modifier.sharedContact("name-${c.id}", text = true),
                            // Relative line height so shrinking also tightens the lines.
                            style = nameStyle.copy(color = Space.Text, lineHeight = 1.18.em),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                                minFontSize = 13.sp,
                                maxFontSize = nameStyle.fontSize,
                            ),
                        )
                        val job = if (c.isOrg) c.position else listOf(c.position, c.company).filter { it.isNotBlank() }.joinToString(" · ")
                        // Age in the head (PLAN 3.3), only when the birth year is known.
                        val age = c.birthday?.let { b ->
                            b.year?.let { y -> java.time.Period.between(DateMath.inYear(b, y), todayDate).years.takeIf { it in 0..130 } }
                        }?.let { "$it Jahre" }
                        val line = listOfNotNull(job.takeIf { it.isNotBlank() }, age).joinToString(" · ")
                        // PLAN 3.3: the work field leads to the organisation when it is one of yours.
                        val employer = remember(c.company, data.contacts) {
                            if (c.isOrg || c.company.isBlank()) null
                            else Logic.fold(c.company).let { key -> data.contacts.firstOrNull { it.isOrg && it.deletedAt == null && !it.archived && Logic.fold(it.company) == key } }
                        }
                        if (line.isNotBlank()) {
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (employer != null) Space.Violet else Space.TextDim,
                                // Long roles and companies get a second line instead of being cut (PLAN 1.4).
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = if (employer != null) Modifier.clip(RoundedCornerShape(6.dp)).clickable { nav.open(Route.Detail(employer.id)) } else Modifier,
                            )
                        }
                        val groups = c.groupIds.mapNotNull { idx.groups[it] }
                        if (groups.isNotEmpty() || c.isOrg || c.isMe) {
                            FlowRow(
                                Modifier.padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                if (c.isOrg) Pill("Organisation · ${Defaults.orgCategory(c.orgCategory).label}", Space.Blue)
                                if (c.isMe) Pill("Das bist du", Space.Orange)
                                groups.forEach { g -> Pill(g.name, Color(g.color)) }
                            }
                        }
                    }
                }
            }

            // Archived: how long, and one tap to bring them back (PLAN 3.11).
            if (c.archived) item(key = "archived") {
                val since = ChronoUnit.DAYS.between(((c.archivedAt ?: c.updatedAt).takeIf { it > 0 } ?: now()).toLocalDate(), todayDate)
                Banner(
                    icon = Icons.Rounded.Unarchive,
                    color = Space.TextDim,
                    text = if (since <= 0) "Seit heute im Archiv" else "Seit " + Fmt.ago(since).removePrefix("vor ") + " im Archiv",
                    action = "Zurückholen",
                    onClick = { edit { it.copy(archived = false, archivedAt = null) } },
                )
            }

            // PLAN 3.11: „Was du weißt“ – pinned note, latest notes, relations and occasions at a
            // glance; a pure summary of what the page already holds.
            if (c.archived) {
                val notes = c.entries.filter { it.note.isNotBlank() && it.memberId == null }.take(2)
                val people = data.relations.mapNotNull { r ->
                    val otherId = if (r.fromId == c.id) r.toId else if (r.toId == c.id) r.fromId else null
                    otherId?.let { idx.contacts[it] }?.takeIf { it.deletedAt == null }?.let { Logic.shortName(it) }
                }
                val next = c.dates.minByOrNull { Logic.daysUntil(it.date, todayDate) }
                if (c.pinnedNote.isNotBlank() || notes.isNotEmpty() || people.isNotEmpty() || next != null) item(key = "known") {
                    SectionCard(title = "Was du weißt", icon = Icons.AutoMirrored.Rounded.Notes) {
                        if (c.pinnedNote.isNotBlank()) InfoRow(label = "Angepinnt", value = c.pinnedNote)
                        notes.forEach { e ->
                            InfoRow(label = (idx.types[e.typeId]?.name ?: "Eintrag") + " · " + Fmt.ago(ChronoUnit.DAYS.between(e.at.toLocalDate(), todayDate)), value = e.note)
                        }
                        if (people.isNotEmpty()) InfoRow(label = "Beziehungen", value = people.joinToString(" · "))
                        next?.let { a -> InfoRow(label = a.title, value = Fmt.dayOfYear(a.date)) }
                    }
                }
            }

            // Quick actions
            val number = ContactActions.defaultNumber(c)
            val email = c.emails.firstOrNull { it.value.isNotBlank() }?.value
            // W5: the row only exists when at least one action applies.
            if (!c.isMe && (number != null || email != null || c.address.isNotBlank())) item(key = "actions") {
                Row(
                    // 7 + (64 − 46) / 2 = 16: the first orb sits on the gutter.
                    Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 4.dp),
                    // Fixed slots instead of label-wide columns: the orbs keep one pitch (W9).
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (number != null) {
                        ActionOrb(
                            Icons.Rounded.Call, "Anrufen", true,
                            onClick = { ContactActions.call(ctx, c, null, data.settings) },
                            onLongClick = { chooser = Chooser.Call },
                            accent = Space.Teal,
                        )
                    }
                    // Only what applies (W5): no greyed-out orbs.
                    if (number != null) {
                        ActionOrb(Icons.Rounded.Sms, "SMS", true, onClick = { ContactActions.sms(ctx, c, null) }, onLongClick = { chooser = Chooser.Sms })
                        ActionOrb(Icons.Rounded.Forum, "WhatsApp", true, onClick = { ContactActions.whatsapp(ctx, c, null) }, onLongClick = { chooser = Chooser.WhatsApp })
                    }
                    if (email != null) {
                        ActionOrb(
                            Icons.Rounded.Email, "E-Mail", true,
                            onClick = { if (c.emails.count { it.value.isNotBlank() } > 1) chooser = Chooser.Mail else ContactActions.email(ctx, c, null) },
                        )
                    }
                    if (c.address.isNotBlank()) {
                        ActionOrb(Icons.Rounded.Map, "Karte", true, onClick = { ContactActions.maps(ctx, c.address) })
                    }
                }
            }

            // Status strip
            if (!c.isMe) item(key = "status") {
                GlassCard(Modifier.padding(top = 8.dp), padding = 16.dp) {
                    // Last contact per kind (PLAN 3.4): facts only, no due dates, and only kinds
                    // that happened – no dashes for nothing (W5).
                    val perType = lastPerType(c).filter { it.second != null }
                    if (perType.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                            perType.forEach { (typeId, at) ->
                                val days = at?.let { ChronoUnit.DAYS.between(it.toLocalDate(), todayDate).toInt() }
                                CountStat(days, idx.types[typeId]?.name ?: "Kontakt") { d -> Fmt.short(d.toLong()) }
                            }
                            repeat(3 - perType.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Archived: Zurückholen is the one thing to do – no logging, no planning (PLAN 3.11).
                        if (!c.archived) {
                            GlowButton("Eintragen", { entryTarget = EntryTarget.New(TypeIds.MEET) }, icon = Icons.Rounded.EditNote)
                            GhostButton("Treffen planen", { meetingDialog = true }, icon = Icons.Rounded.Event)
                        }
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

            // Contact details, links and own fields (PLAN 3.3)
            if (c.phones.isNotEmpty() || c.emails.isNotEmpty() || c.address.isNotBlank() || c.links.isNotEmpty() || c.fields.isNotEmpty()) {
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
                                        nav.home(io.github.thelony97.orbit.ui.home.HomeTab.KARTE)
                                    }
                                },
                            )
                        }
                        c.links.forEach { l ->
                            InfoRow(
                                label = l.title.ifBlank { "Link" } + if (l.note.isNotBlank()) " · ${l.note}" else "",
                                value = l.url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/'),
                                onClick = { ContactActions.web(ctx, l.url) },
                            )
                        }
                        c.fields.forEach { f ->
                            val label = f.label.ifBlank { "Feld" }
                            InfoRow(
                                label = label,
                                value = f.value,
                                trailing = { CopyButton(f.value, label) },
                            )
                        }
                    }
                }
            }

            // Mails from this contact
            if (c.emails.any { it.value.contains('@') } && !c.isMe) {
                item(key = "mails") {
                    MailSection(c)
                }
            }

            // Pinned note (also shown on incoming calls)
            if (c.pinnedNote.isNotBlank()) item(key = "pinned") {
                GlassCard(Modifier.padding(top = 8.dp), padding = 16.dp, border = Space.Orange.copy(alpha = 0.45f)) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { noteDialog = NoteTarget.Pinned },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.PushPin, null, tint = Space.Orange, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(c.pinnedNote, style = MaterialTheme.typography.bodyMedium, color = Space.Text)
                    }
                }
            }

            // Topics – only when there are some or "+ Thema" opened the section (W5)
            if (!c.isMe && (c.topics.isNotEmpty() || "topics" in opened)) item(key = "topics") {
                SectionCard(title = "Nächstes Mal ansprechen", icon = Icons.Rounded.Checklist) {
                    Checklist(
                        items = c.topics,
                        placeholder = "Thema hinzufügen",
                        doneLabel = "erledigt",
                        onAdd = { text -> edit { it.copy(topics = it.topics + CheckItem(newId(), text, createdAt = now())) } },
                        onToggle = { item -> edit { it.copy(topics = it.topics.map { t -> if (t.id == item.id) t.copy(done = !t.done) else t }) } },
                        onDelete = { item -> edit { it.copy(topics = it.topics.filterNot { t -> t.id == item.id }) } },
                        onClearDone = { edit { it.copy(topics = it.topics.filterNot { t -> t.done }) } },
                        autoFocus = c.topics.isEmpty() && "topics" in opened,
                        onDate = { item, day -> edit { it.copy(topics = it.topics.map { t -> if (t.id == item.id) t.copy(remindOn = day?.toString()) else t }) } },
                    )
                }
            }

            // About / notes – tap to edit right here
            if (c.howWeMet.isNotBlank() || c.notes.isNotBlank()) {
                item(key = "about") {
                    SectionCard(title = if (c.isOrg) "Notizen" else "Über ${c.firstName.ifBlank { "diese Person" }}", icon = Icons.AutoMirrored.Rounded.Notes) {
                        if (c.howWeMet.isNotBlank()) InfoRow(label = "Wie kennengelernt", value = c.howWeMet)
                        // The caption only distinguishes notes from "wie kennengelernt"; alone it says nothing.
                        if (c.notes.isNotBlank()) InfoRow(label = if (c.howWeMet.isNotBlank()) "Notizen" else "", value = c.notes, onClick = { noteDialog = NoteTarget.Notes })
                    }
                }
            }

            // Gifts
            if (!c.isOrg && !c.isMe && (c.gifts.isNotEmpty() || "gifts" in opened)) item(key = "gifts") {
                SectionCard(title = "Geschenkideen", icon = Icons.Rounded.CardGiftcard) {
                    Checklist(
                        items = c.gifts,
                        placeholder = "Idee notieren",
                        doneLabel = "verschenkt",
                        onAdd = { text -> edit { it.copy(gifts = it.gifts + CheckItem(newId(), text, createdAt = now())) } },
                        onToggle = { item -> edit { it.copy(gifts = it.gifts.map { g -> if (g.id == item.id) g.copy(done = !g.done) else g }) } },
                        onDelete = { item -> edit { it.copy(gifts = it.gifts.filterNot { g -> g.id == item.id }) } },
                        onClearDone = { edit { it.copy(gifts = it.gifts.filterNot { g -> g.done }) } },
                        autoFocus = c.gifts.isEmpty() && "gifts" in opened,
                    )
                }
            }

            // Photo folder
            if (c.gallery.isNotEmpty() || gallery.importing) item(key = "gallery") { GallerySection(c, gallery) }

            // Archived people never show up in someone else's relations (PLAN 3.11).
            val rels = data.relations.mapNotNull { r ->
                val outgoing = r.fromId == c.id
                val otherId = if (outgoing) r.toId else if (r.toId == c.id) r.fromId else return@mapNotNull null
                val other = idx.contacts[otherId]?.takeIf { it.deletedAt == null && (c.archived || !it.archived) } ?: return@mapNotNull null
                val kind = Defaults.relationKind(r.kind)
                RelView(r.id, other, if (outgoing) kind.backward else kind.forward)
            }
            // Zuordnungen schlicht (PLAN 3.3): organisations as square tiles with a call button,
            // kept apart from the human relations below. Full design comes with R4.
            val orgRels = rels.filter { it.other.isOrg }
            val humanRels = rels.filterNot { it.other.isOrg }
            if (orgRels.isNotEmpty()) item(key = "orgs") {
                SectionCard(title = "Organisationen", icon = Icons.Rounded.Business) {
                    orgRels.forEach { r ->
                        RelationRow(
                            r, idx, lastFirst,
                            onOpen = { nav.open(Route.Detail(r.other.id)) },
                            trailing = {
                                if (ContactActions.defaultNumber(r.other) != null) {
                                    SmallIconAction(Icons.Rounded.Call, "${Logic.shortName(r.other)} anrufen") {
                                        ContactActions.call(ctx, r.other, null, data.settings)
                                    }
                                }
                                SmallIconAction(Icons.Rounded.Close, "Entfernen") {
                                    removeRelation(r)
                                }
                            },
                        )
                    }
                }
            }
            if (humanRels.isNotEmpty()) item(key = "relations") {
                SectionCard(
                    title = "Beziehungen",
                    icon = Icons.Rounded.Link,
                    action = {
                        Row {
                            SmallIconAction(Icons.Rounded.Hub, "Im Netz zeigen") {
                                Session.networkRoot = if (c.isMe) null else c.id
                                nav.home(io.github.thelony97.orbit.ui.home.HomeTab.NETZ)
                            }
                            SmallIconAction(Icons.Rounded.Add, "Beziehung hinzufügen") { relationDialog = true }
                        }
                    },
                ) {
                    humanRels.forEach { r ->
                        RelationRow(
                            r, idx, lastFirst,
                            onOpen = { nav.open(Route.Detail(r.other.id)) },
                            trailing = {
                                SmallIconAction(Icons.Rounded.Close, "Entfernen") {
                                    removeRelation(r)
                                }
                            },
                        )
                    }
                }
            }

            // Anlässe (birthday and the others), soonest first; hidden while there are none.
            // Shared ones are stored at the other contact and show here too (PLAN 3.5).
            val sharedIn = data.contacts
                .filter { it.id != c.id && it.deletedAt == null && !it.archived }
                .flatMap { o -> o.dates.filter { it.sharedWith == c.id }.map { o to it } }
            if (c.dates.isNotEmpty() || sharedIn.isNotEmpty()) item(key = "dates") {
                SectionCard(
                    title = "Anlässe",
                    icon = Icons.Rounded.Cake,
                    action = { SmallIconAction(Icons.Rounded.Add, "Anlass hinzufügen") { occasionEdit = c.id to null } },
                ) {
                    val b = c.birthday
                    if (b != null) {
                        val days = Logic.daysUntil(b, todayDate)
                        val age = Logic.turningAge(b, todayDate)
                        InfoRow(
                            label = "Geburtstag · " + if (days == 0L) (age?.let { "wird heute $it" } ?: "heute") + " 🎂" else Fmt.until(days) + weekdayHint(days, b, todayDate) + (age?.let { " · wird $it" } ?: ""),
                            value = Fmt.dayOfYear(b),
                            // The full dialog: date, reminders, pin, note (PLAN 3.5).
                            onClick = { occasionEdit = c.id to c.dates.firstOrNull { it.kind == io.github.thelony97.orbit.core.model.OccasionKind.BIRTHDAY } },
                        )
                    }
                    // Geschenk-Historie (PLAN 3.5), only once something was given.
                    val gifts = io.github.thelony97.orbit.core.domain.BirthdayTab.giftHistory(c) { id ->
                        idx.contacts[id]?.takeIf { it.deletedAt == null && !it.archived }?.let { Logic.shortName(it) }
                    }
                    if (gifts.isNotEmpty()) {
                        Text(
                            "Geschenke · $gifts",
                            style = MaterialTheme.typography.labelMedium,
                            color = Space.TextFaint,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                    c.anniversaries.sortedBy { Logic.daysUntil(it.date, todayDate) }.forEach { a ->
                        val days = Logic.daysUntil(a.date, todayDate)
                        val years = a.date.year?.let { Logic.nextOccurrence(a.date, todayDate).year - it }
                        val partner = a.sharedWith?.let { idx.contacts[it] }?.takeIf { it.deletedAt == null && !it.archived }
                        InfoRow(
                            label = a.title + " · " + (if (days == 0L) "heute" else Fmt.until(days) + weekdayHint(days, a.date, todayDate)) + (years?.takeIf { it > 0 }?.let { " · $it Jahre" } ?: "") +
                                (partner?.let { " · mit " + Logic.shortName(it) } ?: "") + (if (a.pinned) " · 📌" else ""),
                            value = Fmt.dayOfYear(a.date),
                            onClick = { occasionEdit = c.id to a },
                            trailing = {
                                SmallIconAction(Icons.Rounded.Close, "Entfernen") {
                                    edit { it.copy(dates = it.dates.filterNot { x -> x.id == a.id }) }
                                    Undo.offer("${a.title} entfernt") { edit { it.copy(dates = it.dates + a) } }
                                }
                            },
                        )
                    }
                    sharedIn.sortedBy { Logic.daysUntil(it.second.date, todayDate) }.forEach { (owner, a) ->
                        val days = Logic.daysUntil(a.date, todayDate)
                        InfoRow(
                            label = a.title + " · " + (if (days == 0L) "heute" else Fmt.until(days)) + " · mit " + Logic.shortName(owner),
                            value = Fmt.dayOfYear(a.date),
                            onClick = { occasionEdit = owner.id to a },
                        )
                    }
                }
            }

            // "+ Anlass · + Link · + Foto · + Notiz" – only for what the page does not show yet.
            if (!c.archived) item(key = "add") {
                AddRow(
                    buildList {
                        if (c.dates.isEmpty() && sharedIn.isEmpty()) add(AddItem("Anlass") { occasionEdit = c.id to null })
                        add(AddItem("Link") { linkDialog = true })
                        if (c.gallery.isEmpty() && !gallery.importing) add(AddItem("Foto") { gallery.launch() })
                        if (c.notes.isBlank()) add(AddItem("Notiz") { noteDialog = NoteTarget.Notes })
                        if (!c.isMe && !c.archived && c.topics.isEmpty() && "topics" !in opened) add(AddItem("Thema") { opened = opened + "topics" })
                        if (humanRels.isEmpty()) add(AddItem("Beziehung") { relationDialog = true })
                        if (!c.isOrg && !c.isMe && !c.archived && c.gifts.isEmpty() && "gifts" !in opened) add(AddItem("Geschenkidee") { opened = opened + "gifts" })
                        add(AddItem("Feld") { fieldDialog = true })
                    },
                )
            }

            // History
            if (!c.isMe) item(key = "history-header") {
                SectionTitle(
                    "Verlauf · ${c.entries.size}",
                    Icons.Rounded.History,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
                    action = if (c.archived) null else { { SmallIconAction(Icons.Rounded.Add, "Eintrag hinzufügen") { entryTarget = EntryTarget.New(TypeIds.MEET) } } },
                )
            }
            if (c.entries.isEmpty() && !c.isMe) {
                item(key = "history-empty") {
                    Text(
                        "Noch kein Eintrag",
                        style = MaterialTheme.typography.bodySmall,
                        color = Space.TextFaint,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            // PLAN 3.3: chips per kind with its last date, plus a search only in this Verlauf.
            val kinds = c.entries.groupBy { it.typeId }.mapValues { (_, l) -> l.maxOf { it.at } }
                .entries.sortedByDescending { it.value }
            if (kinds.size > 1 || c.entries.size > 10) {
                item(key = "history-filter") {
                    Column(Modifier.padding(bottom = 6.dp)) {
                        if (kinds.size > 1) {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SpaceChip("Alle", historyType == null, { historyType = null })
                                kinds.forEach { (t, at) ->
                                    val days = ChronoUnit.DAYS.between(at.toLocalDate(), todayDate)
                                    SpaceChip(
                                        "${idx.types[t]?.name ?: t} · ${Fmt.short(days)}",
                                        historyType == t,
                                        { historyType = if (historyType == t) null else t },
                                        leading = entryIcon(idx.types[t]?.icon ?: "star"),
                                    )
                                }
                            }
                        }
                        if (c.entries.size > 10) {
                            SpaceSearchField(
                                historySearch, "Im Verlauf von ${Logic.shortName(c)} suchen",
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                leading = Icons.Rounded.Search,
                            )
                        }
                    }
                }
            }
            val hq = Logic.fold(historySearch.text.toString().trim())
            val filtered = c.entries.filter { e ->
                (historyType == null || e.typeId == historyType) &&
                    (hq.isEmpty() || Logic.fold(e.note + " " + e.messages.joinToString(" ") { it.text }).contains(hq))
            }
            val visible = if (showAllHistory || hq.isNotEmpty() || historyType != null) filtered else filtered.take(30)
            itemsIndexed(visible, key = { _, e -> "e-${e.id}" }) { i, e ->
                // Each node draws in once per visit, not again when scrolling back up.
                val first = drawnEntries.add(e.id)
                EntryRow(e, idx.types[e.typeId], last = i == visible.lastIndex, onClick = { entryTarget = EntryTarget.Existing(e) }, drawIn = first)
            }
            if (visible.size < filtered.size) {
                item(key = "history-more") {
                    Text(
                        "Alle ${filtered.size} Einträge anzeigen",
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
            onDelete = {
                // Keep the live copy (sync may have merged new SMS into it since the sheet opened).
                val id = target.entry.id
                var gone: Entry? = null
                OrbitRepository.edit { d ->
                    gone = d.contacts.firstOrNull { it.id == c.id }?.entries?.firstOrNull { it.id == id }
                    Mutations.deleteEntry(d, c.id, id)
                }
                Undo.offer("Eintrag gelöscht") {
                    val e = gone ?: return@offer
                    OrbitRepository.edit { d ->
                        if (d.contacts.any { it.id == c.id && it.entries.any { x -> x.id == e.id } }) d else Mutations.addEntry(d, c.id, e)
                    }
                }
            },
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

    occasionEdit?.let { (ownerId, initial) ->
        val owner = idx.contacts[ownerId]
        OccasionDialog(
            initial = initial,
            people = data.contacts.filter { !it.isOrg && !it.isMe && it.deletedAt == null && !it.archived && it.id != ownerId },
            allowBirthday = owner != null && !owner.isOrg && owner.birthday == null,
            lastFirst = data.settings.sortByLastName,
            onDismiss = { occasionEdit = null },
            greetingFor = owner?.takeIf { !it.isOrg && !it.isMe },
            onGreeting = { g -> OrbitRepository.editContact(ownerId) { it.copy(greeting = g) } },
            onSave = { o -> OrbitRepository.edit { d -> io.github.thelony97.orbit.data.Mutations.upsertOccasion(d, ownerId, o) } },
        )
    }

    if (qrDialog) QrDialog(c, onDismiss = { qrDialog = false })

    if (linkDialog) {
        LinkDialog(
            onDismiss = { linkDialog = false },
            onSave = { title, url ->
                val full = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
                edit { it.copy(links = it.links + LinkTile(newId(), title, full)) }
            },
        )
    }

    if (fieldDialog) {
        FieldDialog(
            suggestions = if (c.isOrg) listOf("Kundennummer", "Vertragsnummer", "Mitgliedsnummer") else listOf("Kundennummer", "Versicherungsnummer", "Matrikelnummer"),
            onDismiss = { fieldDialog = false },
            onSave = { label, value -> edit { it.copy(fields = it.fields + CustomField(newId(), label, value)) } },
        )
    }

    noteDialog?.let { target ->
        NoteDialog(
            title = if (target == NoteTarget.Pinned) "Angepinnt" else "Notiz",
            initial = if (target == NoteTarget.Pinned) c.pinnedNote else c.notes,
            onDismiss = { noteDialog = null },
            onSave = { text -> edit { if (target == NoteTarget.Pinned) it.copy(pinnedNote = text) else it.copy(notes = text) } },
        )
    }

    if (birthdayDialog) {
        DayOfYearDialog(
            initial = c.birthday,
            onDismiss = { birthdayDialog = false },
            onPick = { d -> edit { it.withBirthday(d, ::newId) } },
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

/**
 * Three slots of "zuletzt" per kind, in a fixed order so columns never swap between contacts
 * (W2): Treffen, Anruf, Nachricht first, then other kinds this contact actually has.
 */
/** PLAN 1.4: „in 12 Tagen – ein Donnerstag“, only when it helps planning (within two months, not tomorrow). */
internal fun weekdayHint(days: Long, day: io.github.thelony97.orbit.data.DayOfYear, today: java.time.LocalDate): String {
    if (days < 2 || days > 60) return ""
    val date = Logic.nextOccurrence(day, today)
    return " – ein " + io.github.thelony97.orbit.core.domain.AtFormat.weekday(date)
}

internal fun lastPerType(c: Contact): List<Pair<String, Long?>> {
    val latest = c.entries.groupBy { it.typeId }.mapValues { (_, l) -> l.maxOf { it.at } }
    val order = listOf(TypeIds.MEET, TypeIds.CALL, TypeIds.MESSAGE, TypeIds.VIDEO, TypeIds.EMAIL, TypeIds.EVENT)
    val used = order.filter { it in latest } + latest.keys.filter { it !in order }.sorted()
    val slots = (used + order).distinct().take(3)
    return order.filter { it in slots }.plus(slots.filter { it !in order }).map { it to latest[it] }
}
