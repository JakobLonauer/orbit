package io.github.thelony97.orbit.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import io.github.thelony97.orbit.actions.ShareContact
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.data.now
import io.github.thelony97.orbit.ui.components.sharedContact
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.drawBehind
import io.github.thelony97.orbit.core.designsystem.Motion
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.rounded.Settings
import io.github.thelony97.orbit.update.UpdateState
import io.github.thelony97.orbit.update.Updates
import androidx.compose.runtime.collectAsState
import io.github.thelony97.orbit.ui.components.OrbButton
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.actions.ContactActions
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.ContactKind
import io.github.thelony97.orbit.data.Defaults
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.data.Mutations
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.TypeIds
import io.github.thelony97.orbit.domain.Audience
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.domain.ListQuery
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.SortMode
import io.github.thelony97.orbit.domain.StatusFilter
import io.github.thelony97.orbit.domain.toLocalDate
import io.github.thelony97.orbit.domain.today
import io.github.thelony97.orbit.security.AppLock
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.PhoneImport
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.Session
import io.github.thelony97.orbit.ui.components.BadgeOrb
import io.github.thelony97.orbit.ui.components.KindMark
import io.github.thelony97.orbit.ui.components.entryIcon
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.Banner
import io.github.thelony97.orbit.ui.components.EmptyHint
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.SpaceSearchField
import io.github.thelony97.orbit.ui.components.UpdateBanner
import io.github.thelony97.orbit.ui.components.contactColor
import io.github.thelony97.orbit.ui.components.glass
import io.github.thelony97.orbit.ui.components.glow
import io.github.thelony97.orbit.ui.home.BottomBarSpace
import io.github.thelony97.orbit.ui.sheets.EntrySheet
import io.github.thelony97.orbit.ui.theme.Space
import io.github.thelony97.orbit.ui.components.Undo
import io.github.thelony97.orbit.util.Fmt
import kotlinx.coroutines.launch
import java.time.temporal.ChronoUnit

@Composable
fun ContactListScreen(data: OrbitData, idx: Index, nav: Nav, initialSelection: Set<String>? = null) {
    val ctx = LocalContext.current
    val search = rememberTextFieldState()
    var quickEntry by remember { mutableStateOf<Contact?>(null) }
    var preview by remember { mutableStateOf<PreviewState?>(null) }
    var filterSheet by remember { mutableStateOf(false) }
    // Mehrfachauswahl (PLAN 3.2): null = normal list, otherwise the selected contact ids.
    var selection by remember { mutableStateOf(initialSelection) }
    var groupDialog by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    BackHandler(enabled = selection != null) { selection = null }
    val lastFirst = data.settings.sortByLastName
    val query = search.text.toString()
    val todayDate = today()
    val sort = SortMode.of(data.settings.listSort)
    val reversed = data.settings.listSortReversed
    val audience = Session.audience
    val status = Session.status
    val groups = Session.groups.intersect(idx.groups.keys)

    val sections = remember(data, query, audience, status, groups) {
        val list = ListQuery.filter(data, idx, audience, status, groups, query, todayDate)
        ListQuery.sections(list, idx, sort, reversed, lastFirst, todayDate)
    }
    val hiddenOrgHits = remember(data, query, audience, status, groups) {
        if (audience != Audience.PEOPLE || query.isBlank()) 0
        else ListQuery.filter(data, idx, Audience.ORGS, status, groups, query, todayDate).size
    }
    val suggestions = remember(data) { data.unknownNumbers.count { !it.dismissed && it.calls + it.sms >= 2 } }
    val me = remember(data) { Logic.me(data) }
    val total = remember(data, audience) { Logic.others(data).count { !it.archived && audience.accepts(it) } }
    val defaultView = status == StatusFilter.ALL && groups.isEmpty() && query.isEmpty()
    val pinned = data.groups.filter { it.pinned }
    val activeCount = activeFilterCount(data)

    val visible = remember(sections) { sections.flatMap { it.contacts } }
    // Search or filter changed while selecting: only what is still visible stays selected,
    // so the count and every bulk action mean the same contacts.
    LaunchedEffect(visible) {
        val sel = selection ?: return@LaunchedEffect
        val ids = visible.mapTo(HashSet()) { it.id }
        if (!ids.containsAll(sel)) selection = sel.filterTo(HashSet()) { it in ids }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        AnimatedContent(
            targetState = selection != null,
            transitionSpec = { fadeIn(tween(Motion.FAST_MS)) togetherWith fadeOut(tween(Motion.FAST_MS)) },
            label = "header",
        ) { selecting ->
        if (selecting) {
            val sel = selection.orEmpty()
            val chosen = visible.filter { it.id in sel }
            SelectionBar(
                count = sel.size,
                allSelected = visible.isNotEmpty() && visible.all { it.id in sel },
                onClose = { selection = null },
                onAll = { selection = if (visible.all { it.id in sel }) emptySet() else visible.map { it.id }.toSet() },
                onGroup = { if (sel.isNotEmpty()) groupDialog = true },
                onArchive = {
                    val ids = chosen.filter { !it.isMe && !it.archived }.map { it.id }.toSet()
                    if (ids.isNotEmpty()) {
                        val at = now()
                        OrbitRepository.edit { d -> d.copy(contacts = d.contacts.map { if (it.id in ids) it.copy(archived = true, archivedAt = at, updatedAt = at) else it }) }
                        Undo.offer(if (ids.size == 1) "1 Kontakt archiviert" else "${ids.size} Kontakte archiviert") {
                            OrbitRepository.edit { d -> d.copy(contacts = d.contacts.map { if (it.id in ids) it.copy(archived = false, archivedAt = null) else it }) }
                        }
                        haptics.perform(Haptic.Confirm)
                    }
                    selection = null
                },
                onShare = { ShareContact.vcards(ctx, chosen) },
            )
        } else {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(11.dp).glow(Space.Orange, 10.dp, alpha = 0.8f).background(Space.Orange, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text("Orbit", style = MaterialTheme.typography.headlineSmallEmphasized, color = Space.Text)
            Spacer(Modifier.width(8.dp))
            Text("$total", style = MaterialTheme.typography.labelLarge, color = Space.TextFaint)
            Spacer(Modifier.weight(1f))
            // Settings live behind the gear (PLAN 3.1); a dot marks an available update.
            val update by Updates.state.collectAsState()
            Box {
                OrbButton(Icons.Rounded.Settings, "Einstellungen", { nav.open(Route.Settings) }, size = 38.dp)
                if (update is UpdateState.Available) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(9.dp)
                            .glow(Space.Coral, 6.dp)
                            .background(Space.Coral, CircleShape),
                    )
                }
            }
        }
        }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpaceSearchField(
                state = search,
                placeholder = if (audience == Audience.ORGS) "Organisationen durchsuchen …" else "Suchen in Namen, Notizen, Verlauf …",
                leading = Icons.Rounded.Search,
                modifier = Modifier.weight(1f),
            )
            BadgeOrb(Icons.Rounded.Tune, "Filter & Sortierung", activeCount, { filterSheet = true })
        }

        // Feedback bar: pinned groups + whatever is active. Invisible when there is nothing to show.
        val showBar = pinned.isNotEmpty() || activeCount > 0
        AnimatedVisibility(showBar) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (sort != SortMode.NAME || reversed) {
                    SpaceChip(sort.label(reversed), true, { filterSheet = true }, leading = Icons.AutoMirrored.Rounded.Sort, accent = Space.Teal)
                }
                if (audience != Audience.PEOPLE) {
                    SpaceChip(audience.label, true, { Session.audience = Audience.PEOPLE }, leading = Icons.Rounded.Business, accent = Space.Blue, trailing = Icons.Rounded.Close)
                }
                if (status != StatusFilter.ALL) {
                    SpaceChip(
                        status.label, true, { Session.status = StatusFilter.ALL },
                        accent = if (status == StatusFilter.FAVORITES) Space.Orange else Space.Violet,
                        trailing = Icons.Rounded.Close,
                    )
                }
                pinned.forEach { g ->
                    SpaceChip(g.name, g.id in groups, { Session.toggleGroup(g.id) }, dot = Color(g.color), accent = Color(g.color))
                }
                data.groups.filter { !it.pinned && it.id in groups }.forEach { g ->
                    SpaceChip(g.name, true, { Session.toggleGroup(g.id) }, dot = Color(g.color), accent = Color(g.color), trailing = Icons.Rounded.Close)
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        val listState = rememberLazyListState()
        val railScope = rememberCoroutineScope()
        // Index of each letter's header: must follow the item order below exactly.
        val letterIndex = remember(sections, suggestions, defaultView, me, audience) {
            var i = 1 // update banner
            if (suggestions > 0 && defaultView) i++
            if (me != null && defaultView && audience != Audience.ORGS) i++
            if (sections.all { it.contacts.isEmpty() }) i++
            val out = LinkedHashMap<String, Int>()
            sections.forEach { sec ->
                if (sec.contacts.isEmpty()) return@forEach
                out[sec.title] = i
                i += 1 + sec.contacts.size
            }
            out
        }
        // A–Z only where it means something: name order, no search, enough sections.
        val showRail = sort == SortMode.NAME && query.isEmpty() && letterIndex.size >= 6
        // Only the header pinned at the top gets a backdrop; the others sit on the open space
        // (otherwise every section draws a dark strip across the violet sky).
        val pinnedHeader by remember(letterIndex) {
            derivedStateOf {
                val first = listState.firstVisibleItemIndex
                val e = letterIndex.entries.lastOrNull { it.value <= first }
                // Stuck = content scrolls beneath it; derivedState only changes when that flips (W8).
                if (e != null && (first > e.value || listState.firstVisibleItemScrollOffset > 0)) e.key else null
            }
        }
        Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = BottomBarSpace + 72.dp, end = if (showRail) 18.dp else 0.dp),
        ) {
            item(key = "update") { UpdateBanner() }
            if (suggestions > 0 && defaultView) {
                item(key = "suggestions") {
                    Banner(
                        icon = Icons.Rounded.PersonAdd,
                        color = Space.Teal,
                        text = if (suggestions == 1) "1 unbekannte Nummer taucht öfter auf" else "$suggestions unbekannte Nummern tauchen öfter auf",
                        onClick = { if (selection == null) nav.open(Route.Suggestions) },
                    )
                }
            }
            if (me != null && defaultView && audience != Audience.ORGS) {
                item(key = "me") {
                    MeCard(me, idx, onOpen = { if (selection == null) nav.open(Route.Detail(me.id)) })
                }
            }

            if (sections.all { it.contacts.isEmpty() }) {
                item(key = "empty") {
                    when {
                        query.isNotEmpty() -> EmptyHint("Nichts gefunden", "Für „$query“ gibt es keinen Treffer.")
                        audience == Audience.ORGS && status == StatusFilter.ALL && groups.isEmpty() ->
                            EmptyHint("Keine Organisationen", "Lege Firma, Praxis oder Amt über + → „Neue Organisation“ an.")
                        !defaultView -> EmptyHint("Leer", "In diesem Filter ist niemand.")
                        else -> EmptyHint("Dein Orbit ist leer", "", action = "Kontakt anlegen" to { nav.open(Route.Edit()) })
                    }
                }
            }

            sections.forEach { section ->
                if (section.contacts.isEmpty()) return@forEach
                stickyHeader(key = "h-${section.title}") {
                    val backdrop by animateFloatAsState(
                        if (pinnedHeader == section.title) 1f else 0f,
                        tween(Motion.FAST_MS),
                        label = "headerBackdrop",
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                if (backdrop > 0f) {
                                    drawRect(Brush.verticalGradient(listOf(Space.Void.copy(alpha = 0.92f * backdrop), Space.Void.copy(alpha = 0.7f * backdrop))))
                                }
                            }
                            .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(section.title, style = MaterialTheme.typography.labelLargeEmphasized, color = Space.Violet)
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f).height(1.dp).background(Space.Line))
                        if (sort != SortMode.NAME) {
                            Spacer(Modifier.width(8.dp))
                            Text("${section.contacts.size}", style = MaterialTheme.typography.labelMedium, color = Space.TextFaint)
                        }
                    }
                }
                items(section.contacts, key = { c -> "${section.title}-${c.id}" }) { c ->
                    ContactRow(
                        contact = c,
                        idx = idx,
                        sort = sort,
                        lastFirst = lastFirst,
                        onOpen = { nav.open(Route.Detail(c.id)) },
                        onCall = { ContactActions.call(ctx, c, null, data.settings) },
                        onQuickEntry = { quickEntry = c },
                        modifier = Modifier.animateItem(),
                        query = query,
                        selecting = selection != null,
                        selected = selection?.contains(c.id) == true,
                        onToggle = {
                            val sel = selection.orEmpty()
                            selection = if (c.id in sel) sel - c.id else sel + c.id
                            haptics.perform(Haptic.Tick)
                        },
                        onPreview = { pc -> PreviewState(pc).also { preview = it } },
                        onPreviewEnd = { st, released ->
                            val action = if (released) st.hovered() else null
                            preview = null
                            when (action) {
                                PreviewAction.CALL -> ContactActions.call(ctx, st.contact, null, data.settings)
                                PreviewAction.SMS -> ContactActions.sms(ctx, st.contact, null)
                                PreviewAction.LOG -> quickEntry = st.contact
                                PreviewAction.SELECT -> {
                                    selection = setOf(st.contact.id)
                                    haptics.perform(Haptic.Confirm)
                                }
                                PreviewAction.OPEN -> nav.open(Route.Detail(st.contact.id))
                                null -> Unit
                            }
                        },
                    )
                }
            }

            if (hiddenOrgHits > 0) {
                item(key = "org-hint") {
                    Banner(
                        icon = Icons.Rounded.Business,
                        color = Space.Blue,
                        text = if (hiddenOrgHits == 1) "1 Treffer bei Organisationen" else "$hiddenOrgHits Treffer bei Organisationen",
                        action = "Anzeigen",
                        onClick = { Session.audience = Audience.ALL },
                    )
                }
            }
        }
        if (showRail) {
            AlphabetRail(
                letters = letterIndex.keys.toList(),
                onLetter = { l -> letterIndex[l]?.let { railScope.launch { listState.scrollToItem(it) } } },
                modifier = Modifier.align(Alignment.CenterEnd).padding(bottom = BottomBarSpace),
            )
        }
        }
    }

    preview?.let { PreviewCard(it, idx) }

    quickEntry?.let { c ->
        EntrySheet(
            title = "Kontakt mit ${Logic.shortName(c)}",
            types = data.entryTypes,
            initial = null,
            defaultType = if (c.isOrg) TypeIds.CALL else TypeIds.MEET,
            onSave = { e ->
                OrbitRepository.edit { d -> Mutations.addEntry(d, c.id, e) }
                // Swiped by mistake? One tap takes it back (PLAN 1.2).
                Undo.offer("Eingetragen bei ${Logic.shortName(c)}") { OrbitRepository.edit { d -> Mutations.deleteEntry(d, c.id, e.id) } }
            },
            onDismiss = { quickEntry = null },
        )
    }
    if (filterSheet) FilterSheet(data, onDismiss = { filterSheet = false })
    if (groupDialog) {
        GroupAssignDialog(
            data = data,
            ids = selection.orEmpty(),
            onDismiss = { groupDialog = false },
        )
    }
}

/** Replaces the header while selecting: count, all, and the three bulk actions (PLAN 3.2). */
@Composable
private fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onAll: () -> Unit,
    onGroup: () -> Unit,
    onArchive: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OrbButton(Icons.Rounded.Close, "Auswahl beenden", onClose, size = 38.dp)
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically(tween(Motion.FAST_MS)) { if (up) it else -it } + fadeIn(tween(Motion.FAST_MS))) togetherWith
                    (slideOutVertically(tween(Motion.FAST_MS)) { if (up) -it else it } + fadeOut(tween(Motion.FAST_MS)))
            },
            label = "count",
            modifier = Modifier.weight(1f),
        ) { n ->
            Text(
                if (n == 1) "1 ausgewählt" else "$n ausgewählt",
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = Space.Text,
                maxLines = 1,
            )
        }
        SpaceChip("Alle", allSelected, onAll)
        OrbButton(Icons.AutoMirrored.Rounded.Label, "Gruppe", onGroup, size = 38.dp, enabled = count > 0)
        OrbButton(Icons.Rounded.Archive, "Archivieren", onArchive, size = 38.dp, enabled = count > 0)
        OrbButton(Icons.Rounded.Share, "Exportieren", onShare, size = 38.dp, enabled = count > 0)
    }
}

/**
 * Groups for the selected contacts: a check when all have it, a dash when some do. Tapping adds
 * the group to everyone (or removes it when all already have it); one undo when it closes.
 */
@Composable
private fun GroupAssignDialog(data: OrbitData, ids: Set<String>, onDismiss: () -> Unit) {
    val chosen = data.contacts.filter { it.id in ids }
    // One undo for everything changed while the dialog was open (the bar sits behind it).
    val before = remember { chosen.associate { it.id to it.groupIds } }
    fun close() {
        val changed = chosen.any { c -> before[c.id] != c.groupIds }
        if (changed) {
            Undo.offer("Gruppen geändert") {
                OrbitRepository.edit { d -> d.copy(contacts = d.contacts.map { c -> before[c.id]?.let { c.copy(groupIds = it) } ?: c }) }
            }
        }
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = { close() },
        containerColor = Space.PanelHigh,
        title = { Text(if (chosen.size == 1) "Gruppe für 1 Kontakt" else "Gruppe für ${chosen.size} Kontakte") },
        text = {
            Column {
                if (data.groups.isEmpty()) {
                    Text("Noch keine Gruppen", style = MaterialTheme.typography.bodyMedium, color = Space.TextDim)
                }
                data.groups.forEach { g ->
                    val have = chosen.count { g.id in it.groupIds }
                    val all = have == chosen.size && have > 0
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                OrbitRepository.edit { d ->
                                    d.copy(contacts = d.contacts.map { c ->
                                        if (c.id !in ids) c
                                        else if (all) c.copy(groupIds = c.groupIds - g.id, updatedAt = now())
                                        else if (g.id in c.groupIds) c
                                        else c.copy(groupIds = c.groupIds + g.id, updatedAt = now())
                                    })
                                }
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(10.dp).background(Color(g.color), CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Text(g.name, style = MaterialTheme.typography.bodyLarge, color = Space.Text, modifier = Modifier.weight(1f))
                        when {
                            all -> Icon(Icons.Rounded.Check, "alle", tint = Color(g.color))
                            have > 0 -> Icon(Icons.Rounded.Remove, "einige", tint = Space.TextDim)
                            else -> Unit
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { close() }) { Text("Fertig") } },
    )
}

/** "Meine Karte" – the user's own entry, always on top. */
@Composable
private fun MeCard(me: Contact, idx: Index, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .glass(RoundedCornerShape(18.dp), tint = Space.Panel, border = Space.Orange.copy(alpha = 0.30f))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(me, Space.Orange, 38.dp, glowColor = Space.Orange)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(Logic.displayName(me, false), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = Space.Text)
            val rels = idx.data.relations.count { it.fromId == me.id || it.toId == me.id }
            Text(
                if (rels > 0) "Das bist du · $rels Beziehungen" else "Das bist du",
                style = MaterialTheme.typography.labelMedium,
                color = Space.TextFaint,
            )
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Space.TextDim)
    }
}

@Composable
private fun ContactRow(
    contact: Contact,
    idx: Index,
    sort: SortMode,
    lastFirst: Boolean,
    onOpen: () -> Unit,
    onCall: () -> Unit,
    onQuickEntry: () -> Unit,
    modifier: Modifier = Modifier,
    query: String = "",
    onPreview: (Contact) -> PreviewState = { PreviewState(it) },
    onPreviewEnd: (PreviewState, Boolean) -> Unit = { _, _ -> },
    selecting: Boolean = false,
    selected: Boolean = false,
    onToggle: () -> Unit = {},
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var previewState by remember { mutableStateOf<PreviewState?>(null) }
    // Coming back from this contact: a short afterglow on its row.
    val afterglow = remember { androidx.compose.animation.core.Animatable(if (Session.lastOpened == contact.id) 1f else 0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (afterglow.value > 0f) {
            Session.lastOpened = null
            kotlinx.coroutines.delay(Motion.MEDIUM_MS.toLong())
            afterglow.animateTo(0f, tween(Motion.MAX_MS))
        }
    }
    val scope = rememberCoroutineScope()
    val state = rememberSwipeToDismissBoxState()
    val hit = remember(contact, query) { if (query.isBlank()) null else matchSentence(contact, query) }
    val todayDate = today()
    val color = contactColor(contact, idx)
    val last = if (sort == SortMode.RECENT) ListQuery.lastEntry(contact) else null
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = contact.phones.isNotEmpty(),
        // While selecting, a swipe must not call anyone or open a sheet.
        gesturesEnabled = !selecting,
        onDismiss = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onCall()
                SwipeToDismissBoxValue.EndToStart -> onQuickEntry()
                else -> Unit
            }
            scope.launch { state.reset() }
        },
        backgroundContent = {
            val direction = state.dismissDirection
            val (tint, icon, alignment) = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Triple(Space.Teal, Icons.Rounded.Call, Alignment.CenterStart)
                SwipeToDismissBoxValue.EndToStart -> Triple(Space.Violet, Icons.Rounded.EditNote, Alignment.CenterEnd)
                else -> Triple(Color.Transparent, null, Alignment.Center)
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 2.dp)
                    // Color.Transparent.copy(alpha) is translucent BLACK: it drew a dark band behind
                    // every row since 1.0.7. Only paint while actually swiping.
                    .then(if (icon != null) Modifier.background(tint.copy(alpha = 0.18f), RoundedCornerShape(16.dp)) else Modifier)
                    .padding(horizontal = 18.dp),
                contentAlignment = alignment,
            ) {
                if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
        },
    ) {
        val picked by animateFloatAsState(if (selected) 1f else 0f, tween(Motion.FAST_MS), label = "picked")
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (afterglow.value > 0f) drawRect(color.copy(alpha = 0.16f * afterglow.value))
                    if (picked > 0f) drawRect(Space.Violet.copy(alpha = 0.14f * picked))
                }
                .onGloballyPositioned { origin = it.positionOnScreen() }
                // Long press opens the preview card; the same finger picks an action (PLAN 3.2).
                .pointerInput(contact.id, selecting) {
                    if (selecting) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { pos -> previewState = onPreview(contact).also { it.pointer = origin + pos } },
                        onDrag = { change, _ ->
                            change.consume()
                            previewState?.pointer = origin + change.position
                        },
                        onDragEnd = { previewState?.let { onPreviewEnd(it, true) }; previewState = null },
                        onDragCancel = { previewState?.let { onPreviewEnd(it, false) }; previewState = null },
                    )
                }
                .clickable {
                    if (selecting) {
                        onToggle()
                    } else {
                        Session.lastOpened = contact.id
                        onOpen()
                    }
                }
                .padding(horizontal = 18.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Selected: the avatar turns over to a check (front fades as the back grows in).
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Avatar(
                    contact, color, 36.dp,
                    modifier = Modifier
                        .sharedContact("avatar-${contact.id}")
                        .graphicsLayer { alpha = 1f - picked; rotationY = 180f * picked.coerceAtMost(0.5f) },
                )
                if (picked > 0f) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .graphicsLayer { alpha = picked; rotationY = 180f * (1f - picked).coerceAtMost(0.5f) }
                            .background(Space.Violet, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Check, null, tint = Space.Void, modifier = Modifier.size(20.dp)) }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Logic.displayName(contact, lastFirst),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = Space.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).sharedContact("name-${contact.id}", text = true),
                    )
                    if (contact.isOrg) {
                        Spacer(Modifier.width(6.dp))
                        KindMark(contact)
                    }
                    if (contact.favorite) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.Star, "Favorit", tint = Space.Orange, modifier = Modifier.size(13.dp))
                    }
                }
                val sub = when {
                    last != null -> buildString {
                        append(idx.types[last.typeId]?.name ?: "Eintrag")
                        when (last.incoming) {
                            true -> append(" · eingehend")
                            false -> append(" · ausgehend")
                            null -> Unit
                        }
                        last.durationSec?.let { append(" · ").append(Fmt.duration(it)) }
                    }
                    contact.isOrg -> listOf(Defaults.orgCategory(contact.orgCategory).label, contact.position).filter { it.isNotBlank() }.joinToString(" · ")
                    else -> null
                }
                if (hit != null) {
                    // The sentence the search found, match highlighted (PLAN 1.4).
                    Text(
                        buildAnnotatedString {
                            append(hit.text.substring(0, hit.start))
                            withStyle(SpanStyle(color = Space.Violet, fontWeight = FontWeight.SemiBold)) { append(hit.text.substring(hit.start, hit.end)) }
                            append(hit.text.substring(hit.end))
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Space.TextDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (sub != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (last != null) {
                            val dirIcon = when (last.incoming) {
                                true -> Icons.AutoMirrored.Rounded.CallReceived
                                false -> Icons.AutoMirrored.Rounded.CallMade
                                null -> entryIcon(idx.types[last.typeId]?.icon ?: "star")
                            }
                            Icon(dirIcon, null, tint = if (last.incoming == true) Space.Teal else Space.TextFaint, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(sub, style = MaterialTheme.typography.labelMedium, color = Space.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            val trailing = when (sort) {
                SortMode.RECENT -> last?.let { e ->
                    val d = ChronoUnit.DAYS.between(e.at.toLocalDate(), todayDate)
                    if (d == 0L) Fmt.time(e.at) else Fmt.short(d)
                } ?: "–"
                SortMode.ADDED -> contact.createdAt.takeIf { it > 0 }?.let { Fmt.shortDate(it.toLocalDate()) } ?: "–"
                SortMode.FREQUENT -> "${ListQuery.frequency(contact, todayDate)}×"
                SortMode.NAME -> Logic.daysSinceLastContact(contact, todayDate)?.let { Fmt.short(it) } ?: ""
            }
            Text(trailing, style = MaterialTheme.typography.labelLarge, color = Space.TextFaint)
        }
    }
}

@Composable
fun ContactFabMenu(nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            scope.launch {
                val contact = PhoneImport.read(ctx, uri) ?: return@launch
                nav.open(Route.Edit(prefill = OrbitRepository.json.encodeToString(Contact.serializer(), contact)))
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            AppLock.allowExternal()
            picker.launch(null)
        }
    }

    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = { expanded = it },
                modifier = Modifier.glow(Space.Violet, 20.dp, RoundedCornerShape(18.dp), 0.55f),
                containerColor = { progress -> lerp(Space.Violet, Space.PanelHighest, progress) },
            ) {
                val progress = checkedProgress
                Icon(
                    if (progress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add,
                    contentDescription = "Kontakt hinzufügen",
                    modifier = Modifier.animateIcon(
                        checkedProgress = { checkedProgress },
                        color = { p -> lerp(Space.Void, Space.Text, p) },
                    ),
                )
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = {
                expanded = false
                nav.open(Route.Edit())
            },
            text = { Text("Neuer Kontakt") },
            icon = { Icon(Icons.Rounded.PersonAdd, null) },
            containerColor = Space.PanelHigh,
            contentColor = Space.Text,
        )
        FloatingActionButtonMenuItem(
            onClick = {
                expanded = false
                nav.open(Route.Edit(prefill = OrbitRepository.json.encodeToString(Contact.serializer(), Contact(id = newId(), kind = ContactKind.ORG))))
            },
            text = { Text("Neue Organisation") },
            icon = { Icon(Icons.Rounded.Business, null) },
            containerColor = Space.PanelHigh,
            contentColor = Space.Text,
        )
        FloatingActionButtonMenuItem(
            onClick = {
                expanded = false
                if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                    AppLock.allowExternal()
                    picker.launch(null)
                } else {
                    permission.launch(Manifest.permission.READ_CONTACTS)
                }
            },
            text = { Text("Aus Handy übernehmen") },
            icon = { Icon(Icons.Rounded.Contacts, null) },
            containerColor = Space.PanelHigh,
            contentColor = Space.Text,
        )
    }
}
