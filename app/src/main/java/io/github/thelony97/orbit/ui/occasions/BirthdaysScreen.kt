package io.github.thelony97.orbit.ui.occasions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.PushPin
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
    var sort by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(io.github.thelony97.orbit.core.domain.OccasionList.Sort.NEXT) }
    var reversed by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var within by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    var round by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Jahresrad (PLAN 1.3): every visible occasion of the coming year as a dot in its contact's colour.
    val upcoming = remember(data, todayDate) {
        io.github.thelony97.orbit.core.domain.UpcomingIndex.build(data.contacts, todayDate) { Visibility.inLists(it, data.settings.showOrgs) }
    }
    val hasPinned = upcoming.any { it.occasion.pinned }
    val shown = if (reiter == 1 && !hasPinned) 0 else reiter
    val occGroups = remember(data, todayDate, shown, sort, reversed, within, round) {
        if (shown == 0) emptyList()
        else io.github.thelony97.orbit.core.domain.OccasionList.build(
            data.contacts, todayDate, { Visibility.inLists(it, data.settings.showOrgs) },
            io.github.thelony97.orbit.core.domain.OccasionList.Filter(withinDays = within, onlyRound = round, pinnedOnly = shown == 1),
            sort, reversed, name = { id -> byId[id]?.let { Logic.displayName(it, lastFirst) }.orEmpty() },
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
                for (r in g.rows) {
                    if (byId[r.contactId] == null) continue
                    if (r.daysUntil >= 0) map.putIfAbsent(r.date.monthValue, head)
                    i++
                }
            }
        } else {
            var i = 3 + if (shown == 2) 1 else 0
            for (g in occGroups) {
                val head = i
                if (g.title.isNotEmpty()) i++
                for (u in g.rows) {
                    if (byId[u.ownerId] == null) continue
                    map.putIfAbsent(u.date.monthValue, if (g.title.isNotEmpty()) head else i)
                    i++
                }
            }
        }
        map
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = BottomBarSpace + 16.dp),
    ) {
        item(key = "header") {
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp).height(38.dp), contentAlignment = Alignment.CenterStart) {
                Text("Anlässe", style = MaterialTheme.typography.headlineSmallEmphasized, color = Space.Text)
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
            if (shown == 2) item(key = "controls") {
                // Sortierung (tap: next one, arrow: reverse) and quick filters (PLAN 3.5).
                // One line that scrolls sideways, like the kind chips in the Verlauf.
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val sorts = io.github.thelony97.orbit.core.domain.OccasionList.Sort.entries
                    io.github.thelony97.orbit.ui.components.SpaceChip(sort.label, true, { sort = sorts[(sort.ordinal + 1) % sorts.size] })
                    io.github.thelony97.orbit.ui.components.SpaceChip(if (reversed) "↑" else "↓", reversed, { reversed = !reversed })
                    listOf(7, 30, 90).forEach { d -> io.github.thelony97.orbit.ui.components.SpaceChip("$d Tage", within == d, { within = if (within == d) null else d }) }
                    io.github.thelony97.orbit.ui.components.SpaceChip("Nur runde", round, { round = !round })
                }
            }
            if (occGroups.isEmpty()) item(key = "occ-empty") {
                Text(
                    "Keine Anlässe",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Space.TextFaint,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
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
                        OccasionRow(u, owner, u.withId?.let { byId[it] }, idx, lastFirst, Modifier.animateItem()) { nav.open(Route.Detail(owner.id)) }
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
                        // Gratulieren on the day itself; it ticks „Gratuliert“ on its way (PLAN 3.5).
                        onCongratulate = if (r.daysUntil == 0L && io.github.thelony97.orbit.actions.ContactActions.defaultNumber(c) != null) {
                            {
                                val opened = io.github.thelony97.orbit.actions.ContactActions.congratulate(ctx, c, "Alles Gute zum Geburtstag, ${c.firstName.ifBlank { Logic.displayName(c, false) }}!")
                                if (opened && tracking && !BirthdayTab.congratulated(c, r.occasion.id, r.date.year)) {
                                    io.github.thelony97.orbit.data.OrbitRepository.edit { d ->
                                        io.github.thelony97.orbit.data.Mutations.toggleCongrats(d, c.id, r.occasion.id, r.date.year)
                                    }
                                }
                            }
                        } else null,
                        gift = if (giftHere) BirthdayTab.gift(c, r.occasion.id, r.date.year) else null,
                        giftEnabled = giftHere,
                        coGivers = coGivers.filter { it.id != c.id },
                        onGift = { what, with ->
                            haptics.perform(io.github.thelony97.orbit.core.designsystem.Haptic.Confirm)
                            // The gift is dated on the birthday itself (the date chip's default).
                            val at = r.date.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
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
    gift: io.github.thelony97.orbit.core.model.HistoryEntry? = null,
    giftEnabled: Boolean = false,
    coGivers: List<Contact> = emptyList(),
    onGift: (String, List<String>) -> Unit = { _, _ -> },
    onCongratulate: (() -> Unit)? = null,
    onOpen: () -> Unit,
) {
    var editing by androidx.compose.runtime.saveable.rememberSaveable(r.contactId) { androidx.compose.runtime.mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
    val today = r.daysUntil == 0L
    val past = r.daysUntil < 0
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(Corners.Row)
            .clickable(onClick = onOpen)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateTile(r.date, today, past)
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
            io.github.thelony97.orbit.ui.components.CheckDot(tick.first, tick.second)
        }
    }
    // „Was?“ opens in the row, Enter saves and closes (PLAN 3.5: no dialog).
    androidx.compose.animation.AnimatedVisibility(editing) {
        GiftLine(gift, coGivers, lastFirst) { what, with ->
            onGift(what, with)
            editing = false
        }
    }
    }
}

/** Day and month on a small tile; today glows in the sun's colour. */
@Composable
private fun DateTile(date: LocalDate, today: Boolean, past: Boolean) {
    val accent = if (today) Space.Orange else Space.Violet
    Column(
        Modifier
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
private fun GiftLine(gift: io.github.thelony97.orbit.core.model.HistoryEntry?, coGivers: List<Contact>, lastFirst: Boolean, onSave: (String, List<String>) -> Unit) {
    val what = androidx.compose.foundation.text.input.rememberTextFieldState(gift?.note.orEmpty())
    var with by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(gift?.withIds.orEmpty()) }
    Column(Modifier.fillMaxWidth().padding(start = 72.dp, end = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.OutlinedTextField(
            state = what,
            placeholder = { Text("Was?") },
            lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            ),
            onKeyboardAction = { onSave(what.text.toString(), with) },
            shape = Corners.Row,
            modifier = Modifier.fillMaxWidth(),
        )
        if (coGivers.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                coGivers.forEach { p ->
                    val on = p.id in with
                    io.github.thelony97.orbit.ui.components.SpaceChip(Logic.displayName(p, lastFirst), on, { with = if (on) with - p.id else with + p.id })
                }
            }
        }
    }
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
    onOpen: () -> Unit,
) {
    val today = u.daysUntil == 0L
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(Corners.Row)
            .clickable(onClick = onOpen)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateTile(u.date, today, past = false)
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
        Text(relativeDay(u.daysUntil), style = MaterialTheme.typography.labelMedium, color = if (today) Space.Orange else Space.TextDim)
        if (u.occasion.pinned) {
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Rounded.PushPin, "Angepinnt", tint = Space.Coral, modifier = Modifier.size(16.dp),
            )
        }
    }
}
