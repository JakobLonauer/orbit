package io.github.thelony97.orbit.ui.verlauf

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.designsystem.Motion
import io.github.thelony97.orbit.core.domain.AtFormat
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.Entry
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.domain.Audience
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.toLocalDate
import io.github.thelony97.orbit.domain.today
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.Session
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.EmptyHint
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.SpaceSearchField
import io.github.thelony97.orbit.ui.components.contactColor
import io.github.thelony97.orbit.ui.components.entryIcon
import io.github.thelony97.orbit.ui.home.BottomBarSpace
import io.github.thelony97.orbit.ui.theme.Space
import io.github.thelony97.orbit.util.Fmt
import java.time.LocalDate

/** A Verlauf line: an entry of a contact, or calls the filter silenced (PLAN 9 B8). */
internal sealed interface VerlaufLine {
    val at: Long
}

/** One Verlauf row with its contact. */
internal data class VerlaufItem(val contact: Contact, val entry: Entry) : VerlaufLine {
    override val at: Long get() = entry.at
}

/** Calls of one number the filter silenced on one day: one thin coral trace with the rules. */
internal data class SilencedTrace(val number: String, override val at: Long, val count: Int, val rules: List<String>) : VerlaufLine

/** Silenced calls merged per number and day, newest first. */
internal fun silencedTraces(rows: List<io.github.thelony97.orbit.call.Silenced.Row>): List<SilencedTrace> =
    rows.groupBy { it.number to it.at.toLocalDate() }
        .map { (_, g) -> SilencedTrace(g.first().number, g.maxOf { it.at }, g.size, g.map { it.rule }.distinct()) }
        .sortedByDescending { it.at }

/**
 * All entries of all visible contacts, newest first (PLAN 3.4). Archived contacts never appear,
 * organisations only when shown; talks with hidden members appear as „Gespräch · <Org>“
 * without name or note.
 */
internal fun verlaufRows(data: OrbitData, showOrgs: Boolean, typeId: String?, query: String): List<VerlaufItem> {
    val q = Logic.fold(query.trim())
    return data.contacts.asSequence()
        .filter { it.deletedAt == null && !it.archived && (showOrgs || !it.isOrg || it.isMe) }
        .flatMap { c -> c.entries.asSequence().map { VerlaufItem(c, it) } }
        .filter { typeId == null || it.entry.typeId == typeId }
        .filter { r ->
            if (q.isEmpty()) return@filter true
            val hay = buildString {
                append(Logic.displayName(r.contact, false)).append(' ')
                if (r.entry.memberId == null) {
                    append(r.entry.note).append(' ')
                    r.entry.messages.forEach { append(it.text).append(' ') }
                }
            }
            Logic.fold(hay).let { h -> q.split(' ').filter { it.isNotBlank() }.all { it in h } }
        }
        .sortedByDescending { it.entry.at }
        .toList()
}

/** "Heute", "Gestern", "Mo., 12. Okt." this year, else with year. */
internal fun dayHeader(d: LocalDate, today: LocalDate): String = when {
    d == today -> "Heute"
    d == today.minusDays(1) -> "Gestern"
    d.year == today.year -> AtFormat.weekdayDate(d)
    else -> "${AtFormat.weekdayDate(d)} ${d.year}"
}

@Composable
fun VerlaufScreen(data: OrbitData, idx: Index, nav: Nav) {
    val search = rememberTextFieldState()
    val query = search.text.toString()
    var typeFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val showOrgs = Session.audience != Audience.PEOPLE
    val todayDate = today()
    val rows = remember(data, showOrgs, typeFilter, query) { verlaufRows(data, showOrgs, typeFilter, query) }
    // Erster Start asks nothing up front (PLAN 1.3): the empty Verlauf asks for what fills it.
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var permissionTick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    // Back from the system settings: look again.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        permissionTick++
        onPauseOrDispose { }
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    val missing = remember(permissionTick) { io.github.thelony97.orbit.ui.Permissions.core.filterNot { io.github.thelony97.orbit.ui.Permissions.granted(ctx, it.permission) } }
    val permissions = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) {
        permissionTick++
        asked = true
        io.github.thelony97.orbit.sync.Sync.requestNow(ctx)
    }
    // Kinds that actually occur, most used first (W5: only what applies).
    val kinds = remember(data) {
        data.contacts.asSequence().filter { !it.archived && it.deletedAt == null }
            .flatMap { c -> c.entries.asSequence() }.groupingBy { it.typeId }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }
    }
    // Entries newer than the moment this tab opened glide in with a short glow (signature moment).
    val openedAt = remember { System.currentTimeMillis() - 60_000 }
    // PLAN 9 B3: the Rückruf-Stapel, at the top of the Verlauf while someone waits.
    val callbacks by androidx.compose.runtime.produceState(0, permissionTick, data.settings.dismissedGhosts) {
        value = io.github.thelony97.orbit.call.Callbacks.load(ctx, data).size
    }
    // PLAN 9 B8: calls the filter silenced – thin coral traces between the day's rows.
    val silenced by androidx.compose.runtime.produceState(emptyList<SilencedTrace>(), permissionTick) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { silencedTraces(io.github.thelony97.orbit.call.Silenced.all(ctx)) }
    }
    val lines: List<VerlaufLine> = remember(rows, silenced, query, typeFilter) {
        if (query.isNotEmpty() || typeFilter != null || rows.isEmpty()) rows else (rows + silenced).sortedByDescending { it.at }
    }
    val iso = remember { io.github.thelony97.orbit.domain.Phones.countryIso(ctx) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = BottomBarSpace + 16.dp),
    ) {
        item(key = "header") {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)) {
                // Same 38-dp title row and gap as Kontakte: the search bar stays put on a tab switch (W2).
                Box(Modifier.height(38.dp), contentAlignment = Alignment.CenterStart) {
                    Text("Verlauf", style = MaterialTheme.typography.headlineSmallEmphasized, color = Space.Text)
                }
                // W5: nothing to search yet – the empty state below is the one thing on screen.
                if (rows.isNotEmpty() || query.isNotEmpty() || typeFilter != null) {
                    Spacer(Modifier.height(10.dp))
                    SpaceSearchField(search, "Im Verlauf suchen", Modifier.fillMaxWidth(), leading = Icons.Rounded.Search)
                }
            }
        }
        if (callbacks > 0 && query.isEmpty()) {
            item(key = "callbacks") {
                Box(Modifier.animateItem().padding(top = 8.dp)) {
                    io.github.thelony97.orbit.ui.components.Banner(
                        icon = Icons.Rounded.Call,
                        color = Space.Violet,
                        text = if (callbacks == 1) "1 Rückruf" else "$callbacks Rückrufe",
                        onClick = { nav.open(io.github.thelony97.orbit.ui.Route.Callbacks) },
                        action = "Öffnen",
                    )
                }
            }
        }
        // PLAN 9 E8: all open topics as a stack.
        val topics = io.github.thelony97.orbit.ui.callbacks.openTopics(data).size
        if (topics > 0 && query.isEmpty()) {
            item(key = "topics") {
                Box(Modifier.animateItem().padding(top = 8.dp)) {
                    io.github.thelony97.orbit.ui.components.Banner(
                        icon = Icons.Rounded.Checklist,
                        color = Space.Violet,
                        text = if (topics == 1) "1 offenes Thema" else "$topics offene Themen",
                        onClick = { nav.open(io.github.thelony97.orbit.ui.Route.Topics) },
                        action = "Öffnen",
                    )
                }
            }
        }
        if (kinds.size > 1) {
            item(key = "kinds") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SpaceChip("Alle", typeFilter == null, { typeFilter = null })
                    kinds.forEach { t ->
                        val type = idx.types[t]
                        SpaceChip(type?.name ?: t, typeFilter == t, { typeFilter = if (typeFilter == t) null else t }, leading = entryIcon(type?.icon ?: "star"))
                    }
                }
            }
        }
        if (rows.isEmpty()) {
            item(key = "empty") {
                when {
                    query.isNotEmpty() -> EmptyHint("Nichts gefunden", "", action = "Suche leeren" to { search.clearText() })
                    typeFilter != null -> EmptyHint("Keine Einträge dieser Art", "", action = "Alle zeigen" to { typeFilter = null })
                    missing.isNotEmpty() && !asked -> EmptyHint("Noch kein Verlauf", "", action = "Anrufe und SMS mitschreiben" to {
                        permissions.launch(missing.map { it.permission }.toTypedArray())
                    })
                    missing.isNotEmpty() -> EmptyHint("Noch kein Verlauf", "", action = "Berechtigungen ansehen" to { nav.open(io.github.thelony97.orbit.ui.Route.Settings) })
                    else -> EmptyHint("Noch kein Verlauf", "", action = "Zu den Kontakten" to { nav.home(io.github.thelony97.orbit.ui.home.HomeTab.CONTACTS) })
                }
            }
        }
        var lastDay: LocalDate? = null
        lines.forEach { line ->
            val day = line.at.toLocalDate()
            if (day != lastDay) {
                lastDay = day
                item(key = "d-$day") {
                    Text(
                        dayHeader(day, todayDate),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = if (day == todayDate) Space.Teal else Space.TextDim,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
            }
            when (line) {
                is VerlaufItem -> item(key = "e-${line.contact.id}-${line.entry.id}") {
                    VerlaufRow(line, idx, fresh = line.entry.at > openedAt, onOpen = { nav.open(Route.Detail(line.contact.id)) }, modifier = Modifier.animateItem())
                }
                is SilencedTrace -> item(key = "s-${line.number}-${line.at}") {
                    SilencedRow(line, iso, Modifier.animateItem())
                }
            }
        }
    }
}

/** PLAN 9 B8: a silenced call – a thin coral trace where a face would be, the rule as a stamp. */
@Composable
private fun SilencedRow(t: SilencedTrace, iso: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(36.dp).height(22.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(2.dp).height(22.dp).background(Space.Coral.copy(alpha = 0.6f), RoundedCornerShape(1.dp)))
        }
        Spacer(Modifier.width(12.dp))
        val number = if (t.number.any(Char::isDigit)) io.github.thelony97.orbit.domain.Phones.pretty(t.number, iso) else t.number
        Text(
            number + if (t.count > 1) " · ${t.count}×" else "",
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = Space.TextFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        t.rules.forEach { rule ->
            Spacer(Modifier.width(6.dp))
            Text(
                rule,
                style = MaterialTheme.typography.labelSmall,
                color = Space.Coral,
                modifier = Modifier
                    .border(1.dp, Space.Coral.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(Fmt.time(t.at), style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = Space.TextFaint)
    }
}

@Composable
private fun VerlaufRow(r: VerlaufItem, idx: Index, fresh: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = r.contact
    val e = r.entry
    val color = contactColor(c, idx)
    val glow = remember { Animatable(if (fresh) 1f else 0f) }
    LaunchedEffect(fresh) { if (fresh) glow.animateTo(0f, tween(Motion.MAX_MS, delayMillis = Motion.MEDIUM_MS)) }
    val member = e.memberId != null
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .drawBehind { if (glow.value > 0f) drawRect(color.copy(alpha = 0.18f * glow.value)) }
            .clickable(onClick = onOpen)
            // 8 + 8 = the 16-dp gutter for the avatar; the highlight keeps 8 dp to the edge.
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(c, color, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (member) "Gespräch · ${Logic.displayName(c, false)}" else Logic.displayName(c, false),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = Space.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Name takes the row, the time sits in one right-aligned column (W9).
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(Fmt.time(e.at), style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = Space.TextFaint)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Same type icon on every row; calls add their direction as a small arrow.
                Icon(entryIcon(idx.types[e.typeId]?.icon ?: "star"), null, tint = Space.TextFaint, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                val kind = if (e.mails.isNotEmpty()) io.github.thelony97.orbit.core.domain.MailBundle.title(e) else idx.types[e.typeId]?.name ?: "Eintrag"
                Text(
                    kind + (e.durationSec?.let { " · " + Fmt.duration(it) } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = Space.TextDim,
                )
                e.incoming?.let { incoming ->
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        if (incoming) Icons.AutoMirrored.Rounded.CallReceived else Icons.AutoMirrored.Rounded.CallMade,
                        if (incoming) "eingehend" else "ausgehend",
                        tint = if (incoming) Space.Teal else Space.TextFaint,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            // Never the content of a hidden member talk (PLAN 3.4).
            val text = if (member) "" else e.note.ifBlank { e.messages.lastOrNull()?.text ?: e.mails.firstOrNull()?.subject.orEmpty() }
            if (text.isNotBlank()) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = Space.TextFaint,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
