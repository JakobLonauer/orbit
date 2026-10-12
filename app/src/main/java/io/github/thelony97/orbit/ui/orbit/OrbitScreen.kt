package io.github.thelony97.orbit.ui.orbit

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.actions.ContactActions
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.Mutations
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.TypeIds
import io.github.thelony97.orbit.domain.Audience
import io.github.thelony97.orbit.domain.DueInfo
import io.github.thelony97.orbit.domain.DueState
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.today
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.Session
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.EmptyHint
import io.github.thelony97.orbit.ui.components.GlassCard
import io.github.thelony97.orbit.ui.components.GlowBar
import io.github.thelony97.orbit.ui.components.KindMark
import io.github.thelony97.orbit.ui.components.OrbButton
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.Stat
import io.github.thelony97.orbit.ui.components.contactColor
import io.github.thelony97.orbit.ui.home.BottomBarSpace
import io.github.thelony97.orbit.ui.sheets.EntrySheet
import io.github.thelony97.orbit.ui.theme.Space
import io.github.thelony97.orbit.util.Fmt
import java.time.temporal.ChronoUnit

private data class Item(val contact: Contact, val info: DueInfo)

private data class Model(
    val birthdays: List<Pair<Contact, Long>>,
    val overdue: List<Item>,
    val week: List<Item>,
    val later: List<Item>,
    val none: List<Item>,
)

/** The orbit as a list: who is due, who comes next, who has no rhythm. */
@Composable
fun OrbitScreen(data: OrbitData, idx: Index, nav: Nav) {
    val ctx = LocalContext.current
    val todayDate = today()
    val audience = Session.audience
    var quickEntry by remember { mutableStateOf<Contact?>(null) }
    var showNone by rememberSaveable { mutableStateOf(false) }

    val model = remember(data, audience) {
        val people = Logic.others(data).filter { !it.archived && audience.accepts(it) }
        val items = people.map { Item(it, Logic.due(it, idx, todayDate)) }
        fun until(i: Item) = i.info.dueDate?.let { ChronoUnit.DAYS.between(todayDate, it) } ?: Long.MAX_VALUE
        Model(
            birthdays = people.filter { !it.isOrg }
                .mapNotNull { c -> c.birthday?.let { c to Logic.daysUntil(it, todayDate) } }
                .filter { it.second <= 14 }
                .sortedBy { it.second },
            overdue = items.filter { it.info.state == DueState.DUE }.sortedBy { until(it) },
            week = items.filter { it.info.state == DueState.OK && until(it) <= 7 }.sortedBy { until(it) },
            later = items.filter { it.info.state == DueState.OK && until(it) > 7 }.sortedBy { until(it) },
            none = items.filter { it.info.state == DueState.NONE }
                .sortedBy { Logic.daysSinceLastContact(it.contact, todayDate)?.let { d -> -d } ?: Long.MIN_VALUE },
        )
    }

    fun call(c: Contact) = ContactActions.call(ctx, c, null, data.settings)

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = BottomBarSpace + 16.dp),
    ) {
        item(key = "header") {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Orbit", style = MaterialTheme.typography.headlineSmallEmphasized, color = Space.Text)
                    Text("Wer als Nächstes dran ist", style = MaterialTheme.typography.labelMedium, color = Space.TextFaint)
                }
                if (audience != Audience.PEOPLE) {
                    SpaceChip(audience.label, true, { Session.audience = Audience.PEOPLE }, leading = Icons.Rounded.Business, accent = Space.Blue, trailing = Icons.Rounded.Close)
                }
            }
        }
        item(key = "stats") {
            GlassCard(padding = 12.dp) {
                Row(Modifier.fillMaxWidth()) {
                    Stat("${model.overdue.size}", "fällig", color = if (model.overdue.isNotEmpty()) Space.Coral else Space.Text)
                    Stat("${model.week.size}", "7 Tage", color = Space.Orange)
                    Stat("${model.later.size}", "später", color = Space.Teal)
                    Stat("${model.none.size}", "ohne", color = Space.TextDim)
                }
            }
        }

        if (model.overdue.isEmpty() && model.week.isEmpty() && model.later.isEmpty() && model.birthdays.isEmpty()) {
            item(key = "empty") {
                EmptyHint(
                    "Noch kein Rhythmus",
                    "Gib Gruppen oder einzelnen Menschen einen Melde-Rhythmus – dann siehst du hier, wer als Nächstes dran ist.",
                )
            }
        }

        if (model.birthdays.isNotEmpty()) {
            item(key = "h-bday") { Header("Geburtstage", model.birthdays.size, Space.Orange) }
            items(model.birthdays, key = { "b-${it.first.id}" }) { (c, days) ->
                val age = c.birthday?.let { Logic.turningAge(it, todayDate) }
                OrbitRow(
                    contact = c,
                    color = contactColor(c, idx),
                    subtitle = (if (days == 0L) "heute 🎂" else Fmt.until(days)) + (age?.let { " · wird $it" } ?: ""),
                    subtitleColor = if (days == 0L) Space.Orange else Space.TextFaint,
                    leadingIcon = true,
                    progress = null,
                    glow = if (days == 0L) Space.Orange else null,
                    onOpen = { nav.open(Route.Detail(c.id)) },
                    onCall = if (c.phones.isNotEmpty()) { { call(c) } } else null,
                    onLog = null,
                    modifier = Modifier.animateItem(),
                )
            }
        }

        fun section(key: String, title: String, list: List<Item>, color: Color) {
            if (list.isEmpty()) return
            item(key = "h-$key") { Header(title, list.size, color) }
            items(list, key = { "$key-${it.contact.id}" }) { item ->
                val c = item.contact
                val info = item.info
                val until = info.dueDate?.let { ChronoUnit.DAYS.between(todayDate, it) }
                val since = Logic.daysSinceLastContact(c, todayDate)
                val subtitle = buildString {
                    when {
                        until == null -> append(if (c.paused) "pausiert" else "kein Rhythmus")
                        until < 0 -> append("seit ${-until} ${if (until == -1L) "Tag" else "Tagen"} fällig")
                        until == 0L -> append("heute fällig")
                        else -> append("fällig ").append(Fmt.until(until))
                    }
                    append(" · ")
                    append(since?.let { "zuletzt ${Fmt.ago(it)}" } ?: "noch kein Kontakt")
                }
                val progress = if (info.rhythm != null && until != null) (1f - until.toFloat() / info.rhythm).coerceIn(0f, 1f) else null
                OrbitRow(
                    contact = c,
                    color = contactColor(c, idx),
                    subtitle = subtitle,
                    subtitleColor = if (info.state == DueState.DUE) Space.Coral else Space.TextFaint,
                    leadingIcon = false,
                    progress = progress,
                    progressColor = color,
                    glow = if (info.state == DueState.DUE) Space.Coral else null,
                    onOpen = { nav.open(Route.Detail(c.id)) },
                    onCall = if (c.phones.isNotEmpty()) { { call(c) } } else null,
                    onLog = { quickEntry = c },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        section("due", "Überfällig", model.overdue, Space.Coral)
        section("week", "In den nächsten 7 Tagen", model.week, Space.Orange)
        section("later", "Später", model.later, Space.Teal)

        if (model.none.isNotEmpty()) {
            item(key = "h-none") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showNone = !showNone }
                        .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Ohne Rhythmus", style = MaterialTheme.typography.labelLargeEmphasized, color = Space.TextDim)
                    Spacer(Modifier.width(8.dp))
                    Text("${model.none.size}", style = MaterialTheme.typography.labelMedium, color = Space.TextFaint)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f).height(1.dp).background(Space.Line))
                    Icon(if (showNone) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Space.TextDim, modifier = Modifier.padding(start = 6.dp).size(20.dp))
                }
            }
            if (showNone) section("none", "", model.none, Space.TextDim)
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
}

@Composable
private fun Header(title: String, count: Int, color: Color) {
    if (title.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.labelLargeEmphasized, color = color)
        Spacer(Modifier.width(8.dp))
        Text("$count", style = MaterialTheme.typography.labelMedium, color = Space.TextFaint)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Space.Line))
    }
}

@Composable
private fun OrbitRow(
    contact: Contact,
    color: Color,
    subtitle: String,
    subtitleColor: Color,
    leadingIcon: Boolean,
    progress: Float?,
    glow: Color?,
    onOpen: () -> Unit,
    onCall: (() -> Unit)?,
    onLog: (() -> Unit)?,
    modifier: Modifier = Modifier,
    progressColor: Color = Space.Teal,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(start = 18.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(contact, color, 38.dp, glowColor = glow)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Logic.displayName(contact, false),
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
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon) {
                    Icon(Icons.Rounded.Cake, null, tint = Space.Orange, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = subtitleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (progress != null) {
                GlowBar(progress, progressColor, Modifier.padding(top = 5.dp, end = 24.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        if (onCall != null) {
            OrbButton(Icons.Rounded.Call, "Anrufen", onCall, size = 36.dp, accent = Space.Teal)
            Spacer(Modifier.width(6.dp))
        }
        if (onLog != null) {
            OrbButton(Icons.Rounded.EditNote, "Kontakt eintragen", onLog, size = 36.dp)
        }
    }
}
