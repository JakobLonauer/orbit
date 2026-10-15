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
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.foundation.text.input.clearText
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
import io.github.thelony97.orbit.ui.Nav
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
    var seriesSheet by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    BackHandler(enabled = selection != null) { selection = null }
    val lastFirst = data.settings.sortByLastName
    val typed = search.text.toString()
    var landing by remember { mutableStateOf(false) }
    val lineScope = rememberCoroutineScope()
    // PLAN 9 E1 Orbit-Zeile: fixed patterns become chips; with a person + kind/time Enter creates.
    val line = remember(typed, data) {
        io.github.thelony97.orbit.core.domain.OrbitLine.parse(typed, data.contacts, data.entryTypes, java.time.LocalDateTime.now(io.github.thelony97.orbit.domain.zone()))
    }
    // What the list searches: the person of a capture, otherwise the text without the operators.
    val query = if (line.capture) "" else line.rest
    val lineFilter: (io.github.thelony97.orbit.data.Contact) -> Boolean = { c ->
        (if (line.capture) c.id == line.personId else true) && io.github.thelony97.orbit.core.domain.OrbitLine.accepts(c, line.filters, data.groups)
    }
    val quickPull = remember { androidx.compose.animation.core.Animatable(0f) }
    var drawer by remember { mutableStateOf(false) }
    val iso = remember { io.github.thelony97.orbit.domain.Phones.countryIso(ctx) }
    // Built once per data change, not per keystroke.
    val numberMatcher = remember(data.contacts) { io.github.thelony97.orbit.domain.PhoneMatcher(data.contacts, iso) }
    val belongsTo = remember(query, numberMatcher) {
        if (query.count { it.isDigit() } < 6 || query.any { it.isLetter() }) null
        else numberMatcher.matchCall(query)
            ?.takeIf { !it.archived && it.via != io.github.thelony97.orbit.core.domain.Matcher.Via.NUMBER }
            ?.let { m -> data.contacts.firstOrNull { it.id == m.contactId && it.deletedAt == null && !it.archived } }
    }
    val todayDate = today()
    val sort = SortMode.of(data.settings.listSort)
    val reversed = data.settings.listSortReversed
    val audience = Session.audience
    val status = Session.status
    val groups = Session.groups.intersect(idx.groups.keys)

    val sections = remember(data, line, audience, status, groups) {
        val list = ListQuery.filter(data, idx, audience, status, groups, query, todayDate).filter(lineFilter)
        val exact = ListQuery.sections(list, idx, sort, reversed, lastFirst, todayDate)
        // PLAN 9 A11: names that only sound alike (Maier → Mayr) come after the exact hits.
        val alike = if (line.capture) emptyList() else ListQuery.soundsLike(data, idx, audience, status, groups, query).filter(lineFilter).sortedBy { Logic.sortKey(it, lastFirst) }
        if (alike.isEmpty()) exact else exact + io.github.thelony97.orbit.domain.ListSection("Klingt ähnlich", alike)
    }
    val hiddenOrgHits = remember(data, line, audience, status, groups) {
        if (audience != Audience.PEOPLE || query.isBlank()) 0
        else ListQuery.filter(data, idx, Audience.ORGS, status, groups, query, todayDate).size
    }
    val suggestions = remember(data) { data.unknownNumbers.count { !it.dismissed && it.calls + it.sms >= 2 } }
    // Heute (PLAN 9 C1): ghosts and cards for today; empty = no row at all.
    val todayCards = io.github.thelony97.orbit.ui.today.rememberTodayCards(data)
    val me = remember(data) { Logic.me(data) }
    val total = remember(data, audience) { Logic.others(data).count { !it.archived && audience.accepts(it) } }
    // Nobody in the Orbit yet (whatever the filter): no counter, no search, no filter (W5/W6).
    val nobody = remember(data) { Logic.others(data).none { !it.archived } }
    val defaultView = status == StatusFilter.ALL && groups.isEmpty() && typed.isEmpty()
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
                onMessage = { if (sel.isNotEmpty()) seriesSheet = true },
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
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(11.dp).glow(Space.Orange, 10.dp, alpha = 0.8f).background(Space.Orange, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text("Orbit", style = MaterialTheme.typography.headlineSmallEmphasized, color = Space.Text)
            if (!nobody) {
                Spacer(Modifier.width(8.dp))
                Text("$total", style = MaterialTheme.typography.labelLarge, color = Space.TextFaint)
            }
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

        // Schnellzugriff (PLAN 3.7): pinned portals, only while there are some – organisations' too.
        val quick = remember(data.contacts) { io.github.thelony97.orbit.core.domain.QuickLinks.pinned(data.contacts) { Logic.displayName(it, false) } }
        val linkCtx = androidx.compose.ui.platform.LocalContext.current
        if (quick.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    // Pulling the list down stretches the row towards the drawer (W8).
                    .graphicsLayer { translationY = quickPull.value * 0.35f }
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                quick.forEach { q ->
                    io.github.thelony97.orbit.ui.detail.QuickLinkChip(
                        q.link,
                        onClick = { io.github.thelony97.orbit.actions.ContactActions.openLink(linkCtx, q.link) },
                        onLongClick = { nav.open(Route.Detail(q.ownerId)) },
                    )
                }
            }
            // Schublade (PLAN 1.3): all pinned links in large tiles; pulling down opens, pushing up closes.
            AnimatedVisibility(drawer) {
                io.github.thelony97.orbit.ui.detail.QuickDrawer(
                    quick.map { it.link to it.ownerId },
                    onOpen = { l -> io.github.thelony97.orbit.actions.ContactActions.openLink(linkCtx, l) },
                    onOwner = { id -> nav.open(Route.Detail(id)) },
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }

        if (!nobody) Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpaceSearchField(
                state = search,
                placeholder = if (audience == Audience.ORGS) "Organisationen durchsuchen …" else "Suchen in Namen, Notizen, Verlauf …",
                leading = Icons.Rounded.Search,
                modifier = Modifier.weight(1f),
                onSubmit = {
                    // PLAN 9 E24: „Event Hochzeit Tom“ starts the Event-Modus for today.
                    line.event?.let { ev ->
                        startEvent(ev)
                        haptics.perform(io.github.thelony97.orbit.core.designsystem.Haptic.Confirm)
                        search.clearText()
                    }
                    val landed = if (line.capture && !landing) commitLine(line, data) else null
                    if (landed != null) {
                        // Moment: the chips drop into the row, which flares; Confirm on the landing.
                        landing = true
                        Session.flash = landed
                        lineScope.launch {
                            kotlinx.coroutines.delay(Motion.MEDIUM_MS.toLong())
                            haptics.perform(io.github.thelony97.orbit.core.designsystem.Haptic.Confirm)
                            kotlinx.coroutines.delay(Motion.MEDIUM_MS.toLong())
                            search.clearText()
                            landing = false
                            // Let the row's flare run out before the flag goes (it would cancel the fade).
                            kotlinx.coroutines.delay(Motion.MAX_MS.toLong())
                            Session.flash = null
                        }
                    }
                },
            )
            BadgeOrb(Icons.Rounded.Tune, "Filter & Sortierung", activeCount, { filterSheet = true })
        }

        activeEvent(data)?.let { ev -> EventBar(ev, data) }
        AnimatedVisibility(line.chips.isNotEmpty()) {
            OrbitLineChips(
                line,
                // Tap on a chip takes its words out of the line again.
                onClick = { chip ->
                    val rest = Regex(Regex.escape(chip.text), RegexOption.IGNORE_CASE).replaceFirst(typed, "").replace(Regex("\\s+"), " ").trim()
                    if (rest != typed.trim()) {
                        haptics.perform(Haptic.Tick)
                        search.setTextAndPlaceCursorAtEnd(if (rest.isEmpty()) "" else "$rest ")
                    }
                },
                landing = landing,
                personColor = line.personId?.let { id -> data.contacts.firstOrNull { it.id == id } }?.let { contactColor(it, idx) },
                // PLAN 9 E15: a search with operators can be kept as a Smart-Gruppe.
                onKeep = if (!line.capture && !line.filters.isEmpty && data.settings.smartGroups.none { it.query == typed.trim() }) {
                    {
                        val q = typed.trim()
                        OrbitRepository.editSettings { it.copy(smartGroups = it.smartGroups + io.github.thelony97.orbit.core.model.SmartGroup(io.github.thelony97.orbit.data.newId(), q, q)) }
                        haptics.perform(Haptic.Confirm)
                    }
                } else null,
            )
        }

        // Feedback bar: pinned groups + whatever is active. Invisible when there is nothing to show.
        val showBar = pinned.isNotEmpty() || activeCount > 0 || data.settings.smartGroups.isNotEmpty()
        AnimatedVisibility(showBar) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                // PLAN 9 E15: Smart-Gruppen – a tap puts the saved line into the search (again = off).
                data.settings.smartGroups.forEach { sg ->
                    val on = typed.trim() == sg.query
                    SpaceChip(sg.name, on, {
                        if (on) search.clearText() else search.edit { replace(0, length, sg.query) }
                    }, leading = Icons.Rounded.BookmarkAdded, accent = Space.Violet, trailing = if (on) Icons.Rounded.Close else null)
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        val listState = rememberLazyListState()
        val railScope = rememberCoroutineScope()
        // Overscroll at the top of the list feeds the drawer pull (only with pinned links).
        val pullConnection = remember(quick.isNotEmpty()) {
            object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                    if (quick.isEmpty() || source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) return Offset.Zero
                    if (available.y > 0f) {
                        railScope.launch { quickPull.snapTo((quickPull.value + available.y * 0.5f).coerceAtMost(240f)) }
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }

                override fun onPreScroll(available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                    // Any push up closes the drawer again.
                    if (available.y < 0f && drawer) drawer = false
                    // Pushing up while pulled takes the pull back first.
                    if (available.y < 0f && quickPull.value > 0f) {
                        val take = maxOf(available.y, -quickPull.value * 2f)
                        railScope.launch { quickPull.snapTo((quickPull.value + take * 0.5f).coerceAtLeast(0f)) }
                        return Offset(0f, take)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                    if (quickPull.value > 0f) {
                        if (quickPull.value > 90f) drawer = true
                        quickPull.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 400f))
                    }
                    return androidx.compose.ui.unit.Velocity.Zero
                }
            }
        }
        // Index of each letter's header: must follow the item order below exactly.
        val showToday = todayCards.isNotEmpty() && defaultView && selection == null
        // PLAN 9 D1: Meine Dienste under Heute.
        val services = remember(data) { io.github.thelony97.orbit.ui.services.myServices(data) }
        val showServices = services.isNotEmpty() && defaultView && selection == null
        val letterIndex = remember(sections, suggestions, defaultView, me, audience, showToday, showServices) {
            var i = 1 // update banner
            if (showToday) i++
            if (showServices) i++
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
        val showRail = sort == SortMode.NAME && typed.isEmpty() && letterIndex.size >= 6
        // The rail's room is reserved inside rows and headers for the whole name sort, so typing
        // never shifts them (W2); cards and banners keep the full 16-dp gutter.
        val railPad = if (sort == SortMode.NAME) 18.dp else 0.dp
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
            modifier = Modifier.fillMaxSize().nestedScroll(pullConnection),
            contentPadding = PaddingValues(bottom = BottomBarSpace + 72.dp),
        ) {
            item(key = "update") { UpdateBanner(Modifier.animateItem()) }
            // Every item moves when something above it comes or goes – nothing snaps (W2).
            if (showToday) item(key = "today") {
                io.github.thelony97.orbit.ui.today.TodayRow(todayCards, nav, Modifier.animateItem().padding(bottom = io.github.thelony97.orbit.core.designsystem.Spacing.XS))
            }
            if (showServices) item(key = "services") {
                io.github.thelony97.orbit.ui.services.MyServicesRow(services, data, nav, Modifier.animateItem().padding(bottom = io.github.thelony97.orbit.core.designsystem.Spacing.XS))
            }
            // „gehört zu Arzt Müller“ (PLAN 1.3): a pasted Durchwahl or a number in an
            // organisation's range – a hidden member shows only as the organisation.
            belongsTo?.let { org ->
                item(key = "belongs") {
                    Box(Modifier.animateItem()) { Banner(
                        icon = Icons.Rounded.Business,
                        color = Space.Blue,
                        text = "gehört zu " + Logic.displayName(org, false),
                        action = "Öffnen",
                        onClick = { nav.open(Route.Detail(org.id)) },
                    ) }
                }
            }
            if (suggestions > 0 && defaultView) {
                item(key = "suggestions") {
                    Box(Modifier.animateItem()) {
                        Banner(
                            icon = Icons.Rounded.PersonAdd,
                            color = Space.Teal,
                            text = if (suggestions == 1) "1 unbekannte Nummer taucht öfter auf" else "$suggestions unbekannte Nummern tauchen öfter auf",
                            onClick = { if (selection == null) nav.open(Route.Suggestions) },
                        )
                    }
                }
            }
            if (me != null && defaultView && audience != Audience.ORGS) {
                item(key = "me") {
                    Box(Modifier.animateItem()) { MeCard(me, idx, onOpen = { if (selection == null) nav.open(Route.Detail(me.id)) }) }
                }
            }

            if (sections.all { it.contacts.isEmpty() }) {
                item(key = "empty") {
                    // W6/W7: one line, one action – no instructions.
                    Box(Modifier.animateItem()) { when {
                        typed.isNotEmpty() -> EmptyHint("Kein Treffer", "", action = "Suche leeren" to { search.clearText() })
                        audience == Audience.ORGS && status == StatusFilter.ALL && groups.isEmpty() ->
                            EmptyHint("Keine Organisationen", "", action = "Organisation anlegen" to {
                                nav.open(Route.Edit(prefill = OrbitRepository.json.encodeToString(Contact.serializer(), Contact(id = io.github.thelony97.orbit.data.newId(), kind = io.github.thelony97.orbit.data.ContactKind.ORG))))
                            })
                        !defaultView -> EmptyHint("Niemand in diesem Filter", "", action = "Filter zurücksetzen" to {
                            Session.status = StatusFilter.ALL
                            Session.groups = emptySet()
                            Session.audience = Audience.PEOPLE
                        })
                        else -> EmptyHint("Dein Orbit ist leer", "", action = "Kontakt anlegen" to { nav.open(Route.Edit()) })
                    } }
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
                            .padding(start = 16.dp, end = 16.dp + railPad, top = 8.dp, bottom = 4.dp),
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
                        endInset = railPad,
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
                    Box(Modifier.animateItem()) {
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
    if (seriesSheet) {
        SeriesSheet(data, selection.orEmpty(), onDismiss = { seriesSheet = false })
    }
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
    onMessage: () -> Unit,
    onArchive: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
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
        // PLAN 9 E6: Serien-Nachricht.
        OrbButton(Icons.Rounded.Sms, "Nachricht", onMessage, size = 38.dp, enabled = count > 0)
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
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .glass(RoundedCornerShape(20.dp), tint = Space.Panel, border = Space.Orange.copy(alpha = 0.30f))
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
                if (rels > 0) "Das bist du · $rels " + if (rels == 1) "Beziehung" else "Beziehungen" else "Das bist du",
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
    endInset: androidx.compose.ui.unit.Dp = 0.dp,
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
    // PLAN 9 E1: the chips of the Orbit-Zeile land on this row – it flares up and fades.
    val flashing = Session.flash == contact.id
    androidx.compose.runtime.LaunchedEffect(flashing) {
        if (flashing) {
            afterglow.animateTo(1f, tween(Motion.FAST_MS))
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
        // PLAN 9 E20: photos and PDFs from another app land in this contact's folder.
        modifier = modifier.acceptFiles(contact.id),
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
                    if (picked > 0f) {
                        // Inset band with the row radius, like the swipe background (W9).
                        val inset = 8.dp.toPx()
                        drawRoundRect(
                            Space.Violet.copy(alpha = 0.14f * picked),
                            topLeft = androidx.compose.ui.geometry.Offset(inset, 2.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 4.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                        )
                    }
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
                .padding(start = 16.dp, end = 16.dp + endInset, top = 7.dp, bottom = 7.dp),
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
                val job = if (contact.isOrg) io.github.thelony97.orbit.core.domain.Jobs.latest(contact.entries) else null
                val sub = when {
                    // PLAN 9 D5: a craft business shows its last job – „Therme 05/2025 · 340 € · ★★★★“.
                    job != null -> listOf(contact.trade, io.github.thelony97.orbit.core.domain.Jobs.line(job, io.github.thelony97.orbit.domain.zone()))
                        .filter { it.isNotBlank() }.joinToString(" · ")
                    last != null -> buildString {
                        append(idx.types[last.typeId]?.name ?: "Eintrag")
                        when (last.incoming) {
                            true -> append(" · eingehend")
                            false -> append(" · ausgehend")
                            null -> Unit
                        }
                        last.durationSec?.let { append(" · ").append(Fmt.duration(it)) }
                    }
                    contact.isOrg -> listOf(contact.trade.ifBlank { Defaults.orgCategory(contact.orgCategory).label }, contact.position).filter { it.isNotBlank() }.joinToString(" · ")
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
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Mehrere auf einmal (PLAN 3.14): Orbit's own list of the phone book.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) nav.open(Route.PhoneImport)
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
                    nav.open(Route.PhoneImport)
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
