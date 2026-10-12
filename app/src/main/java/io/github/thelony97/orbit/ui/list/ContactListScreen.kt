package io.github.thelony97.orbit.ui.list

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
import io.github.thelony97.orbit.domain.DueState
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.today
import io.github.thelony97.orbit.security.AppLock
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.PhoneImport
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.EmptyHint
import io.github.thelony97.orbit.ui.components.GlowPill
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

private data class Section(val title: String, val contacts: List<Contact>)

private const val F_ALL = "all"
private const val F_FAV = "fav"
private const val F_DUE = "due"
private const val F_ARCHIVE = "archive"

@Composable
fun ContactListScreen(data: OrbitData, idx: Index, nav: Nav) {
    val ctx = LocalContext.current
    val search = rememberTextFieldState()
    var filter by rememberSaveable { mutableStateOf(F_ALL) }
    var quickEntry by remember { mutableStateOf<Contact?>(null) }
    val lastFirst = data.settings.sortByLastName
    val query = search.text.toString()
    val todayDate = today()
    val active = remember(data) { Logic.active(data) }
    val dueIds = remember(data) {
        active.filter { !it.archived && Logic.due(it, idx, todayDate).state == DueState.DUE }.map { it.id }.toSet()
    }

    val sections = remember(data, query, filter) {
        val base = active.filter { c ->
            when {
                filter == F_ARCHIVE -> c.archived
                c.archived -> false
                filter == F_FAV -> c.favorite
                filter == F_DUE -> c.id in dueIds
                filter.startsWith("g:") -> filter.removePrefix("g:") in c.groupIds
                else -> true
            }
        }.filter { Logic.matches(it, query, idx) }
        if (filter == F_DUE) {
            listOf(Section("Fällig", base.sortedBy { Logic.due(it, idx, todayDate).dueDate?.toEpochDay() }))
        } else {
            base.sortedBy { Logic.sortKey(it, lastFirst) }
                .groupBy { Logic.sectionLetter(it, lastFirst) }
                .toSortedMap(compareBy<String> { if (it == "#") "￿" else it })
                .map { (letter, list) -> Section(letter, list) }
        }
    }
    val suggestions = remember(data) { data.unknownNumbers.count { !it.dismissed && it.calls + it.sms >= 2 } }
    val total = active.count { !it.archived }

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
            if (dueIds.isNotEmpty()) {
                GlowPill(
                    text = "${dueIds.size} fällig",
                    color = Space.Coral,
                    onClick = { filter = if (filter == F_DUE) F_ALL else F_DUE },
                )
            }
        }

        SpaceSearchField(
            state = search,
            placeholder = "Suchen in Namen, Notizen, Verlauf …",
            leading = Icons.Rounded.Search,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SpaceChip("Alle", filter == F_ALL, { filter = F_ALL })
            SpaceChip("Fällig", filter == F_DUE, { filter = if (filter == F_DUE) F_ALL else F_DUE }, accent = Space.Coral)
            SpaceChip("Favoriten", filter == F_FAV, { filter = if (filter == F_FAV) F_ALL else F_FAV }, leading = Icons.Rounded.Star, accent = Space.Orange)
            data.groups.forEach { g ->
                val key = "g:${g.id}"
                SpaceChip(g.name, filter == key, { filter = if (filter == key) F_ALL else key }, dot = Color(g.color), accent = Color(g.color))
            }
            SpaceChip("Archiv", filter == F_ARCHIVE, { filter = if (filter == F_ARCHIVE) F_ALL else F_ARCHIVE })
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = BottomBarSpace + 72.dp),
        ) {
            item(key = "update") { UpdateBanner() }
            if (suggestions > 0 && filter == F_ALL && query.isEmpty()) {
                item(key = "suggestions") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .glass(RoundedCornerShape(16.dp), tint = Space.Panel, border = Space.Teal.copy(alpha = 0.35f))
                            .clickable { nav.open(Route.Suggestions) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.PersonAdd, null, tint = Space.Teal, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (suggestions == 1) "1 unbekannte Nummer taucht öfter auf" else "$suggestions unbekannte Nummern tauchen öfter auf",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Space.Text,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Space.TextDim)
                    }
                }
            }

            if (sections.all { it.contacts.isEmpty() }) {
                item(key = "empty") {
                    when {
                        query.isNotEmpty() -> EmptyHint("Nichts gefunden", "Für „$query“ gibt es keinen Treffer.")
                        filter == F_DUE -> EmptyHint("Niemand fällig", "Du bist mit allen auf dem Laufenden.")
                        filter != F_ALL -> EmptyHint("Leer", "In diesem Filter ist niemand.")
                        else -> EmptyHint("Dein Orbit ist leer", "Tippe auf + und hol die ersten Menschen in deinen Orbit.")
                    }
                }
            }

            sections.forEach { section ->
                if (section.contacts.isEmpty()) return@forEach
                stickyHeader(key = "h-${section.title}") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Space.Void.copy(alpha = 0.94f))
                            .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(section.title, style = MaterialTheme.typography.labelLargeEmphasized, color = Space.Violet)
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f).height(1.dp).background(Space.Line))
                    }
                }
                items(section.contacts, key = { c -> "${section.title}-${c.id}" }) { c ->
                    ContactRow(
                        contact = c,
                        color = contactColor(c, idx),
                        due = c.id in dueIds,
                        lastFirst = lastFirst,
                        onOpen = { nav.open(Route.Detail(c.id)) },
                        onCall = { ContactActions.call(ctx, c, null, data.settings) },
                        onQuickEntry = { quickEntry = c },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    quickEntry?.let { c ->
        EntrySheet(
            title = "Kontakt mit ${c.firstName.ifBlank { Logic.displayName(c, lastFirst) }}",
            types = data.entryTypes,
            initial = null,
            defaultType = TypeIds.MEET,
            onSave = { e -> OrbitRepository.edit { d -> Mutations.addEntry(d, c.id, e) } },
            onDismiss = { quickEntry = null },
        )
    }
}

@Composable
private fun ContactRow(
    contact: Contact,
    color: Color,
    due: Boolean,
    lastFirst: Boolean,
    onOpen: () -> Unit,
    onCall: () -> Unit,
    onQuickEntry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val state = rememberSwipeToDismissBoxState()
    val since = Logic.daysSinceLastContact(contact)
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
                    .background(tint.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
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
            Avatar(contact, color, 36.dp, glowColor = if (due) Space.Coral else null)
            Spacer(Modifier.width(12.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Logic.displayName(contact, lastFirst),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = Space.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (contact.favorite) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Star, "Favorit", tint = Space.Orange, modifier = Modifier.size(13.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                since?.let { Fmt.short(it) } ?: "neu",
                style = MaterialTheme.typography.labelLarge,
                color = if (due) Space.Coral else Space.TextFaint,
            )
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
