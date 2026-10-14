package io.github.thelony97.orbit.ui.occasions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.designsystem.Corners
import io.github.thelony97.orbit.core.domain.BirthdayTab
import io.github.thelony97.orbit.core.domain.Visibility
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.today
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.contactColor
import io.github.thelony97.orbit.ui.components.glow
import io.github.thelony97.orbit.ui.components.sharedContact
import io.github.thelony97.orbit.ui.home.BottomBarSpace
import io.github.thelony97.orbit.ui.theme.Space
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * Reiter „Geburtstage“ des Anlässe-Tabs (PLAN 3.5), erste Fassung: alle Menschen mit Geburtstag
 * ab heute in Abschnitten, Datumskachel mit Wochentag, „wird 31“, Bezug zu heute, „Gratuliert“
 * per Tipp. Jahresrad, Geschenk und die Eingabe für „ohne Geburtstag“ folgen.
 */
@Composable
fun BirthdaysScreen(data: OrbitData, idx: Index, nav: Nav, todayDate: LocalDate = today(), initialReiter: Int = 0) {
    val lastFirst = data.settings.sortByLastName
    val result = remember(data, todayDate) {
        BirthdayTab.build(data.contacts, todayDate, visible = { Visibility.inLists(it, showOrgs = false) }, name = { Logic.displayName(it, lastFirst) })
    }
    val byId = remember(data) { data.contacts.associateBy { it.id } }
    // „Gratuliert“ abhaken (PLAN 3.5), unless switched off in the settings.
    val tracking = data.settings.giftTracking
    val haptics = io.github.thelony97.orbit.core.designsystem.rememberHaptics()
    val coGivers = remember(data) { BirthdayTab.coGivers(data.contacts).mapNotNull { byId[it] } }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var filling by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Reiter (PLAN 3.5): Geburtstage (Standard) · Angepinnt (only when something is pinned, W5) · Alle Anlässe.
    var reiter by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(initialReiter) }
    // Filter & Sortierung for „Angepinnt“ and „Alle“, behind one button like the contact list (PLAN 3.5).
    var viewState by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(OccasionView()) }
    var filterSheet by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Jahresrad (PLAN 1.3): every visible occasion of the coming year as a dot in its contact's colour.
    val upcoming = remember(data, todayDate) {
        io.github.thelony97.orbit.core.domain.UpcomingIndex.build(data.contacts, todayDate) { Visibility.inLists(it, data.settings.showOrgs) }
    }
    // Everything an Anlass filter could show (organisations included), for the sheet's chips and „Angepinnt“.
    val everything = remember(data, todayDate) {
        io.github.thelony97.orbit.core.domain.UpcomingIndex.build(data.contacts, todayDate) { Visibility.inLists(it, true) }
    }
    // A group deleted meanwhile filters nothing any more.
    val view = viewState.let { v -> if (v.groupId != null && data.groups.none { it.id == v.groupId }) v.copy(groupId = null) else v }
    val hasPinned = everything.any { it.occasion.pinned && (view.orgs || data.settings.showOrgs || byId[it.ownerId]?.isOrg != true) }
    val shown = if (reiter == 1 && !hasPinned) 0 else reiter
    val occGroups = remember(data, todayDate, shown, view) {
        if (shown == 0) emptyList()
        else io.github.thelony97.orbit.core.domain.OccasionList.build(
            data.contacts, todayDate, { Visibility.inLists(it, view.orgs || data.settings.showOrgs) },
            // Both Reiter share the filter; its badge shows on the button above them.
            view.filter(pinnedOnly = shown == 1), view.sort, view.reversed,
            name = { id -> byId[id]?.let { Logic.displayName(it, lastFirst) }.orEmpty() },
        )
    }
    val dots = remember(upcoming, idx) { upcoming.mapNotNull { u -> byId[u.ownerId]?.let { RingDot(u.date, contactColor(it, idx)) } } }
    val ringTitle = "${todayDate.dayOfMonth}. ${BirthdayTab.MONTHS[todayDate.monthValue - 1]}"
    val ringSub = remember(upcoming, todayDate) {
        val todays = upcoming.filter { it.daysUntil == 0L }
        when {
            todays.size == 1 -> byId[todays.single().ownerId]?.let { "Heute: " + Logic.shortName(it) }.orEmpty()
            todays.size > 1 -> "Heute: ${todays.size} Anlässe"
            else -> upcoming.firstOrNull()?.let { u -> byId[u.ownerId]?.let { Logic.shortName(it) + " · " + relativeDay(u.daysUntil) } }.orEmpty()
        }
    }
    // Index of the first list item of each month, for „Monat antippen springt in die Liste“.
    // Mirrors the order the LazyColumn below emits its items in (header, ring, then the groups).
    val monthIndex = remember(result, byId, occGroups, shown) {
        val map = HashMap<Int, Int>()
        if (shown == 0) {
            var i = 3
            for (g in result.groups) {
                val head = i
                i++
                var first = true
                for (r in g.rows) {
                    if (byId[r.contactId] == null) continue
                    // The header for a group's first row, the row itself further down (Monat genau).
                    if (r.daysUntil >= 0) map.putIfAbsent(r.date.monthValue, if (first) head else i)
                    first = false
                    i++
                }
            }
        } else {
            var i = 3
            for (g in occGroups) {
                val head = i
                if (g.title.isNotEmpty()) i++
                var first = true
                for (u in g.rows) {
                    if (byId[u.ownerId] == null) continue
                    map.putIfAbsent(u.date.monthValue, if (first && g.title.isNotEmpty()) head else i)
                    first = false
                    i++
                }
            }
        }
        map
    }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = BottomBarSpace + 16.dp),
    ) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp).height(42.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Anlässe", style = MaterialTheme.typography.headlineSmallEmphasized, color = Space.Text, modifier = Modifier.weight(1f))
                // Only where it filters something (W5): the Geburtstage Reiter has no filter.
                if (shown != 0) {
                    io.github.thelony97.orbit.ui.components.BadgeOrb(
                        androidx.compose.material.icons.Icons.Rounded.Tune, "Filter & Sortierung", view.activeCount(), { filterSheet = true },
                    )
                }
            }
        }
        item(key = "ring") {
            YearRingView(dots, todayDate, ringTitle, ringSub, onMonth = { m ->
                // No birthday that month: the next month that has one.
                val target = (0 until 12).map { ((m - 1 + it) % 12) + 1 }.firstNotNullOfOrNull { monthIndex[it] }
                if (target != null) scope.launch { listState.animateScrollToItem(target) }
            })
        }
        item(key = "reiter") {
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                io.github.thelony97.orbit.ui.components.SpaceChip("Geburtstage", shown == 0, { reiter = 0 }, accent = Space.Coral)
                if (hasPinned) io.github.thelony97.orbit.ui.components.SpaceChip("Angepinnt", shown == 1, { reiter = 1 }, accent = Space.Coral)
                io.github.thelony97.orbit.ui.components.SpaceChip("Alle Anlässe", shown == 2, { reiter = 2 }, accent = Space.Coral)
            }
        }
        if (shown != 0) {
            if (occGroups.isEmpty()) item(key = "occ-empty") {
                // W6: one way on – drop the quick filters if they emptied the list.
                val filtered = view.filter(false) != io.github.thelony97.orbit.core.domain.OccasionList.Filter()
                io.github.thelony97.orbit.ui.components.EmptyHint(
                    "Keine Anlässe", "",
                    action = if (filtered) "Alle zeigen" to { viewState = view.copy(kinds = emptySet(), groupId = null, withinDays = null, onlyRound = false) } else null,
                )
            }
            occGroups.forEach { g ->
                if (g.title.isNotEmpty()) item(key = "og-${g.title}") {
                    Text(
                        g.title,
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = if (g.title == "Heute") Space.Orange else Space.TextDim,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                g.rows.forEach { u ->
                    val owner = byId[u.ownerId] ?: return@forEach
                    item(key = "o-${u.occasion.id}") {
                        OccasionRow(
                            u, owner, u.withId?.let { byId[it] }, idx, lastFirst, Modifier.animateItem(),
                            // Langdruck pinnt (PLAN 3.5); the occasion stays stored at its owner.
                            settings = data.settings,
                            onPin = {
                                haptics.perform(io.github.thelony97.orbit.core.designsystem.Haptic.Confirm)
                                io.github.thelony97.orbit.data.OrbitRepository.edit { d ->
                                    io.github.thelony97.orbit.data.Mutations.upsertOccasion(d, owner.id, u.occasion.copy(pinned = !u.occasion.pinned))
                                }
                            },
                        ) { nav.open(Route.Detail(owner.id)) }
                    }
                }
            }
        }
        if (shown == 0) result.groups.forEach { g ->
            item(key = "s-${g.section}-${g.title}") {
                Text(
                    g.title,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = if (g.section == BirthdayTab.Section.TODAY) Space.Orange else Space.TextDim,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            g.rows.forEach { r ->
                val c = byId[r.contactId] ?: return@forEach
                item(key = "b-${r.contactId}") {
                    // Only where it is the day or just was – not for birthdays months away.
                    val tick = if (tracking && r.daysUntil in -7L..0L) {
                        BirthdayTab.congratulated(c, r.occasion.id, r.date.year) to {
                            haptics.perform(io.github.thelony97.orbit.core.designsystem.Haptic.Confirm)
                            io.github.thelony97.orbit.data.OrbitRepository.edit { d ->
                                io.github.thelony97.orbit.data.Mutations.toggleCongrats(d, c.id, r.occasion.id, r.date.year)
                            }
                        }
                    } else null
                    val giftHere = tracking && r.daysUntil in -7L..0L
                    BirthdayRow(
                        r, c, idx, lastFirst, Modifier.animateItem(), tick,
                        onSwipeTick = if (tick != null && !tick.first) {
                            {
                                haptics.perform(io.github.thelony97.orbit.core.designsystem.Haptic.Confirm)
                                io.github.thelony97.orbit.data.OrbitRepository.edit { d ->
                                    io.github.thelony97.orbit.data.Mutations.markCongrats(d, c.id, r.occasion.id, r.date.year)
                                }
                                io.github.thelony97.orbit.ui.components.Undo.offer("Gratuliert · " + Logic.shortName(c)) {
                                    io.github.thelony97.orbit.data.OrbitRepository.edit { d ->
                                        val now = d.contacts.firstOrNull { it.id == c.id }
                                        if (now != null && BirthdayTab.congratulated(now, r.occasion.id, r.date.year)) {
                                            io.github.thelony97.orbit.data.Mutations.toggleCongrats(d, c.id, r.occasion.id, r.date.year)
                                        } else d
                                    }
                                }
                            }
                        } else null,
                        // Gratulieren on the day itself; it ticks „Gratuliert“ on its way (PLAN 3.5).
                        onCongratulate = if (r.daysUntil == 0L && io.github.thelony97.orbit.actions.ContactActions.defaultNumber(c) != null) {
                            {
                                val opened = io.github.thelony97.orbit.actions.ContactActions.congratulate(ctx, c, io.github.thelony97.orbit.actions.ContactActions.greeting(c))
                                if (opened && tracking) {
                                    // Marks, never toggles: a double tap cannot take the tick away again.
                                    io.github.thelony97.orbit.data.OrbitRepository.edit { d ->
                                        io.github.thelony97.orbit.data.Mutations.markCongrats(d, c.id, r.occasion.id, r.date.year)
                                    }
                                }
                            }
                        } else null,
                        gift = if (giftHere) BirthdayTab.gift(c, r.occasion.id, r.date.year) else null,
                        giftEnabled = giftHere,
                        coGivers = coGivers.filter { it.id != c.id },
                        onGift = { what, with, day ->
                            haptics.perform(io.github.thelony97.orbit.core.designsystem.Haptic.Confirm)
                            // Dated by the date chip (the birthday itself unless changed).
                            val at = day.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                            io.github.thelony97.orbit.data.OrbitRepository.edit { d ->
                                io.github.thelony97.orbit.data.Mutations.setGift(d, c.id, r.occasion.id, r.date.year, what, with, at)
                            }
                        },
                    ) { nav.open(Route.Detail(c.id)) }
                }
            }
        }
        if (shown == 0 && result.withoutBirthday > 0) {
            item(key = "without") {
                Text(
                    if (result.withoutBirthday == 1) "1 Kontakt ohne Geburtstag" else "${result.withoutBirthday} Kontakte ohne Geburtstag",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (filling) Space.TextDim else Space.TextFaint,
                    modifier = Modifier
                        .padding(start = 8.dp, end = 8.dp, top = 16.dp)
                        .clip(Corners.Row)
                        .clickable { filling = !filling }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            // Eingabe in der Zeile (PLAN 3.5): type the day, Enter – the person moves up into the list.
            if (filling) {
                result.without.forEach { id ->
                    val c = byId[id] ?: return@forEach
                    item(key = "w-$id") {
                        MissingBirthdayRow(c, lastFirst, todayDate, Modifier.animateItem())
                    }
                }
            }
        }
    }
    if (filterSheet) {
        OccasionFilterSheet(
            view, data.groups,
            kinds = everything.map { it.occasion.kind }.distinct(),
            hasOrgs = everything.any { byId[it.ownerId]?.isOrg == true },
            onChange = { viewState = it }, onDismiss = { filterSheet = false },
        )
    }
    // Sternenregen: a birthday today, once per day.
    val birthdayToday = result.groups.any { it.section == BirthdayTab.Section.TODAY }
    StarRain(play = birthdayToday && io.github.thelony97.orbit.ui.Session.starRainDay != todayDate.toEpochDay()) {
        io.github.thelony97.orbit.ui.Session.starRainDay = todayDate.toEpochDay()
    }
    }
}

private val WEEKDAYS = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")
private val SHORT_MONTHS = listOf("JÄN", "FEB", "MÄR", "APR", "MAI", "JUN", "JUL", "AUG", "SEP", "OKT", "NOV", "DEZ")

/** „heute“, „morgen“, „gestern“, „in 5 Tagen“, „vor 3 Tagen“. */
fun relativeDay(days: Long): String = when {
    days == 0L -> "heute"
    days == 1L -> "morgen"
    days == -1L -> "gestern"
    days < 0 -> "vor ${-days} Tagen"
    else -> "in $days Tagen"
}

@Composable
private fun BirthdayRow(
    r: BirthdayTab.Row,
    c: Contact,
    idx: Index,
    lastFirst: Boolean,
    modifier: Modifier = Modifier,
    /** Congratulated already, and the tap that ticks it; null = no tick for this row. */
    tick: Pair<Boolean, () -> Unit>? = null,
    /** The swipe that ticks „Gratuliert“; null while it is ticked already or there is no tick. */
    onSwipeTick: (() -> Unit)? = null,
    gift: io.github.thelony97.orbit.core.model.HistoryEntry? = null,
    giftEnabled: Boolean = false,
    coGivers: List<Contact> = emptyList(),
    onGift: (String, List<String>, LocalDate) -> Unit = { _, _, _ -> },
    onCongratulate: (() -> Unit)? = null,
    onOpen: () -> Unit,
) {
    var editing by androidx.compose.runtime.saveable.rememberSaveable(r.contactId) { androidx.compose.runtime.mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
    val today = r.daysUntil == 0L
    val past = r.daysUntil < 0
    // Wisch nach rechts = Gratuliert (PLAN 3.5): resists, ticks, springs back. Only ever ticks
    // (with Undo); taking it back stays on the tick itself.
    val swipe = androidx.compose.material3.rememberSwipeToDismissBoxState()
    val swipeScope = androidx.compose.runtime.rememberCoroutineScope()
    val currentSwipe by androidx.compose.runtime.rememberUpdatedState(onSwipeTick)
    androidx.compose.material3.SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = onSwipeTick != null,
        enableDismissFromEndToStart = false,
        gesturesEnabled = onSwipeTick != null,
        onDismiss = { value ->
            if (value == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd) currentSwipe?.invoke()
            swipeScope.launch { swipe.reset() }
        },
        backgroundContent = {
            if (swipe.dismissDirection == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                        .background(Space.Orange.copy(alpha = 0.18f), Corners.Row)
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Check, null, tint = Space.Orange, modifier = Modifier.size(20.dp))
                }
            }
        },
    ) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(Corners.Row)
            .clickable(onClick = onOpen)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateTile(r.date, today, past, Modifier.sharedContact("occ-${r.occasion.id}", text = true))
        Spacer(Modifier.width(12.dp))
        Avatar(c, contactColor(c, idx), 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                Logic.displayName(c, lastFirst),
                style = MaterialTheme.typography.bodyLarge,
                color = if (past) Space.TextDim else Space.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // The weekday only where it helps: „heute/morgen/gestern“ on the right says it already.
                listOfNotNull(
                    WEEKDAYS[r.date.dayOfWeek.value - 1].takeIf { kotlin.math.abs(r.daysUntil) > 1 },
                    r.turning?.let { if (past) "$it geworden" else "wird $it" },
                    gift?.note?.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = Space.TextFaint,
            )
        }
        // Today the section says „Heute“; the place of „heute“ holds the one thing to do (W6).
        if (onCongratulate != null) {
            io.github.thelony97.orbit.ui.components.GlowButton("Gratulieren", onCongratulate, color = Space.Orange)
        } else {
            Text(
                relativeDay(r.daysUntil),
                style = MaterialTheme.typography.labelMedium,
                color = if (today) Space.Orange else Space.TextDim,
            )
        }
        if (giftEnabled) {
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Rounded.CardGiftcard,
                "Geschenk",
                tint = if (gift != null) Space.Orange else Space.TextDim,
                modifier = Modifier.size(36.dp).clip(Corners.Pill).clickable { editing = !editing }.padding(8.dp),
            )
        }
        if (tick != null) {
            Spacer(Modifier.width(4.dp))
            val spark = rememberSpark(tick.first)
            Box(Modifier.starSpark(spark)) {
                io.github.thelony97.orbit.ui.components.CheckDot(tick.first, tick.second)
            }
        }
    }
    }
    // „Was?“ opens in the row, Enter saves and closes (PLAN 3.5: no dialog).
    androidx.compose.animation.AnimatedVisibility(editing) {
        GiftLine(gift, coGivers, lastFirst, r.date, ideas = c.gifts.filterNot { it.done }, onIdea = { idea, with, day ->
            onGift(idea.text, with, day)
            io.github.thelony97.orbit.data.OrbitRepository.edit { d -> io.github.thelony97.orbit.data.Mutations.giveIdea(d, c.id, idea.id) }
            editing = false
        }) { what, with, day ->
            onGift(what, with, day)
            editing = false
        }
    }
    }
}

/** Day and month on a small tile; today glows in the sun's colour. */
@Composable
private fun DateTile(date: LocalDate, today: Boolean, past: Boolean, modifier: Modifier = Modifier) {
    val accent = if (today) Space.Orange else Space.Violet
    // Geteilter Übergang Datumskachel → Anlass (PLAN W3): the tile flies to the date on the contact page.
    Column(
        modifier
            .size(44.dp)
            .then(if (today) Modifier.glow(Space.Orange, 12.dp, Corners.Row, 0.5f) else Modifier)
            .clip(Corners.Row)
            .background(if (today) Space.Orange.copy(alpha = 0.18f) else Space.PanelHigh.copy(alpha = if (past) 0.5f else 1f))
            .border(1.dp, if (today) Space.Orange.copy(alpha = 0.6f) else Space.Line, Corners.Row),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = if (past) Space.TextDim else Space.Text,
        )
        Text(SHORT_MONTHS[date.monthValue - 1], style = MaterialTheme.typography.labelSmall, color = if (past) Space.TextFaint else accent.copy(alpha = 0.9f))
    }
}

@Composable
private fun GiftLine(
    gift: io.github.thelony97.orbit.core.model.HistoryEntry?,
    coGivers: List<Contact>,
    lastFirst: Boolean,
    /** The date chip's default: the birthday itself. */
    birthday: LocalDate,
    ideas: List<io.github.thelony97.orbit.core.model.CheckItem> = emptyList(),
    onIdea: (io.github.thelony97.orbit.core.model.CheckItem, List<String>, LocalDate) -> Unit = { _, _, _ -> },
    onSave: (String, List<String>, LocalDate) -> Unit,
) {
    val what = androidx.compose.foundation.text.input.rememberTextFieldState(gift?.note.orEmpty())
    var with by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(gift?.withIds.orEmpty()) }
    var day by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableLongStateOf(
            (gift?.at?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() } ?: birthday).toEpochDay(),
        )
    }
    var picking by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(day)
    Column(Modifier.fillMaxWidth().padding(start = 72.dp, end = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.OutlinedTextField(
            state = what,
            placeholder = { Text("Was?") },
            lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            ),
            onKeyboardAction = { onSave(what.text.toString(), with, date) },
            shape = Corners.Row,
            modifier = Modifier.fillMaxWidth(),
        )
        // The person's open gift ideas: one tap = given this year (and ticked off as an idea).
        if (ideas.isNotEmpty() && gift == null) {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ideas.take(6).forEach { idea ->
                    io.github.thelony97.orbit.ui.components.SpaceChip(idea.text, false, { onIdea(idea, with, date) }, leading = androidx.compose.material.icons.Icons.Rounded.CardGiftcard, accent = Space.Orange)
                }
            }
        }
        // Mitschenkende and the date chip (PLAN 3.5), one row.
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            io.github.thelony97.orbit.ui.components.SpaceChip(
                io.github.thelony97.orbit.util.Fmt.shortDate(date), date != birthday, { picking = true },
                leading = androidx.compose.material.icons.Icons.Rounded.CalendarMonth,
            )
            coGivers.forEach { p ->
                val on = p.id in with
                io.github.thelony97.orbit.ui.components.SpaceChip(Logic.displayName(p, lastFirst), on, { with = if (on) with - p.id else with + p.id })
            }
        }
    }
    if (picking) io.github.thelony97.orbit.ui.sheets.DateDialog(date, onDismiss = { picking = false }, onPick = { day = it.toEpochDay() })
}

@Composable
private fun MissingBirthdayRow(c: Contact, lastFirst: Boolean, todayDate: LocalDate, modifier: Modifier = Modifier) {
    val text = androidx.compose.foundation.text.input.rememberTextFieldState()
    val haptics = io.github.thelony97.orbit.core.designsystem.rememberHaptics()
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Logic.displayName(c, lastFirst),
            style = MaterialTheme.typography.bodyLarge,
            color = Space.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        androidx.compose.material3.OutlinedTextField(
            state = text,
            placeholder = { Text("TT.MM.JJJJ") },
            lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            ),
            onKeyboardAction = {
                val day = BirthdayTab.parseDay(text.text.toString(), todayDate)
                if (day == null) {
                    haptics.perform(io.github.thelony97.orbit.core.designsystem.Haptic.Reject)
                } else {
                    haptics.perform(io.github.thelony97.orbit.core.designsystem.Haptic.Confirm)
                    io.github.thelony97.orbit.data.OrbitRepository.editContact(c.id) { it.withBirthday(day) { io.github.thelony97.orbit.data.newId() } }
                }
            },
            shape = Corners.Row,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            modifier = Modifier.width(150.dp),
        )
    }
}

/** A row in „Angepinnt“/„Alle“: tile, name (both for a shared one), what it is, countdown, pin. */
@Composable
private fun OccasionRow(
    u: io.github.thelony97.orbit.core.domain.Upcoming,
    owner: Contact,
    with: Contact?,
    idx: Index,
    lastFirst: Boolean,
    modifier: Modifier = Modifier,
    settings: io.github.thelony97.orbit.data.Settings? = null,
    onPin: () -> Unit = {},
    onOpen: () -> Unit,
) {
    val today = u.daysUntil == 0L
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // Heute hervorgehoben mit Anrufen/WhatsApp (PLAN 3.5) – only with a number to reach.
    val reachable = today && settings != null && io.github.thelony97.orbit.actions.ContactActions.defaultNumber(owner) != null
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(Corners.Row)
            .combinedClickable(onClick = onOpen, onLongClick = onPin)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateTile(u.date, today, past = false, Modifier.sharedContact("occ-${u.occasion.id}", text = true))
        Spacer(Modifier.width(12.dp))
        Avatar(owner, contactColor(owner, idx), 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(Logic.displayName(owner, lastFirst), with?.let { Logic.shortName(it) }).joinToString(" & "),
                style = MaterialTheme.typography.bodyLarge,
                color = Space.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(io.github.thelony97.orbit.core.domain.OccasionList.line(u), style = MaterialTheme.typography.labelMedium, color = Space.TextFaint, maxLines = 1)
        }
        if (reachable) {
            io.github.thelony97.orbit.ui.components.OrbButton(
                androidx.compose.material.icons.Icons.Rounded.Call, "Anrufen",
                { io.github.thelony97.orbit.actions.ContactActions.call(ctx, owner, null, settings!!) },
                size = 36.dp, accent = Space.Teal,
            )
            Spacer(Modifier.width(8.dp))
            io.github.thelony97.orbit.ui.components.OrbButton(
                androidx.compose.material.icons.Icons.AutoMirrored.Rounded.Chat, "WhatsApp",
                { io.github.thelony97.orbit.actions.ContactActions.whatsapp(ctx, owner, null) },
                size = 36.dp, accent = Space.Orange,
            )
        } else {
            Text(relativeDay(u.daysUntil), style = MaterialTheme.typography.labelMedium, color = if (today) Space.Orange else Space.TextDim)
        }
        if (u.occasion.pinned) {
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Rounded.PushPin, "Angepinnt", tint = Space.Coral, modifier = Modifier.size(16.dp),
            )
        }
    }
}
