package io.github.thelony97.orbit.ui.list

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
import io.github.thelony97.orbit.util.Fmt
import kotlinx.coroutines.launch
import java.time.temporal.ChronoUnit

@Composable
fun ContactListScreen(data: OrbitData, idx: Index, nav: Nav) {
    val ctx = LocalContext.current
    val search = rememberTextFieldState()
    var quickEntry by remember { mutableStateOf<Contact?>(null) }
    var filterSheet by remember { mutableStateOf(false) }
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

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
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
                        onClick = { nav.open(Route.Suggestions) },
                    )
                }
            }
            if (me != null && defaultView && audience != Audience.ORGS) {
                item(key = "me") {
                    MeCard(me, idx, onOpen = { nav.open(Route.Detail(me.id)) })
                }
            }

            if (sections.all { it.contacts.isEmpty() }) {
                item(key = "empty") {
                    when {
                        query.isNotEmpty() -> EmptyHint("Nichts gefunden", "Für „$query“ gibt es keinen Treffer.")
                        audience == Audience.ORGS && status == StatusFilter.ALL && groups.isEmpty() ->
                            EmptyHint("Keine Organisationen", "Lege Firma, Praxis oder Amt über + → „Neue Organisation“ an.")
                        !defaultView -> EmptyHint("Leer", "In diesem Filter ist niemand.")
                        else -> EmptyHint("Dein Orbit ist leer", "Tippe auf + und hol die ersten Menschen in deinen Orbit.")
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

    quickEntry?.let { c ->
        EntrySheet(
            title = "Kontakt mit ${Logic.shortName(c)}",
            types = data.entryTypes,
            initial = null,
            defaultType = if (c.isOrg) TypeIds.CALL else TypeIds.MEET,
            onSave = { e -> OrbitRepository.edit { d -> Mutations.addEntry(d, c.id, e) } },
            onDismiss = { quickEntry = null },
        )
    }
    if (filterSheet) FilterSheet(data, onDismiss = { filterSheet = false })
}

@Composable
private fun Banner(icon: ImageVector, color: Color, text: String, onClick: () -> Unit, action: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .glass(RoundedCornerShape(16.dp), tint = Space.Panel, border = color.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Space.Text, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(action, style = MaterialTheme.typography.labelLarge, color = color)
        } else {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Space.TextDim)
        }
    }
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
) {
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
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 18.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(contact, color, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Logic.displayName(contact, lastFirst),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = Space.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
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
