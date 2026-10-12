package io.github.thelony97.orbit.ui.list

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
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
import io.github.thelony97.orbit.ui.components.UpdateBanner
import io.github.thelony97.orbit.ui.components.contactColor
import io.github.thelony97.orbit.ui.sheets.EntrySheet
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
    val dueCount = remember(data) { active.count { !it.archived && Logic.due(it, idx, todayDate).state == DueState.DUE } }

    val sections = remember(data, query, filter) {
        val base = active.filter { c ->
            when {
                filter == F_ARCHIVE -> c.archived
                c.archived -> false
                filter == F_FAV -> c.favorite
                filter == F_DUE -> Logic.due(c, idx, todayDate).state == DueState.DUE
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
    val suggestions = remember(data) {
        data.unknownNumbers.count { !it.dismissed && it.calls + it.sms >= 2 }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val total = active.count { !it.archived }

    Column(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
        MediumFlexibleTopAppBar(
            title = { Text("Kontakte") },
            subtitle = { Text(if (total == 1) "1 Mensch in deinem Orbit" else "$total Menschen in deinem Orbit") },
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        )

        TextField(
            state = search,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Suchen in Namen, Notizen, Verlauf …") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { search.clearText() }) { Icon(Icons.Rounded.Close, "Leeren") }
                }
            },
            lineLimits = TextFieldLineLimits.SingleLine,
            shape = RoundedCornerShape(50),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = filter == F_ALL, onClick = { filter = F_ALL }, label = { Text("Alle") })
            FilterChip(
                selected = filter == F_DUE,
                onClick = { filter = if (filter == F_DUE) F_ALL else F_DUE },
                label = { Text(if (dueCount > 0) "Fällig · $dueCount" else "Fällig") },
            )
            FilterChip(
                selected = filter == F_FAV,
                onClick = { filter = if (filter == F_FAV) F_ALL else F_FAV },
                label = { Text("Favoriten") },
                leadingIcon = { Icon(Icons.Rounded.Star, null, Modifier.size(18.dp)) },
            )
            data.groups.forEach { g ->
                val key = "g:${g.id}"
                FilterChip(
                    selected = filter == key,
                    onClick = { filter = if (filter == key) F_ALL else key },
                    label = { Text(g.name) },
                    leadingIcon = { Box(Modifier.size(10.dp).background(Color(g.color), RoundedCornerShape(50))) },
                )
            }
            FilterChip(
                selected = filter == F_ARCHIVE,
                onClick = { filter = if (filter == F_ARCHIVE) F_ALL else F_ARCHIVE },
                label = { Text("Archiv") },
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 112.dp),
        ) {
            item(key = "update") { UpdateBanner() }
            if (suggestions > 0 && filter == F_ALL && query.isEmpty()) {
                item(key = "suggestions") {
                    Surface(
                        onClick = { nav.open(Route.Suggestions) },
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.PersonAdd, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                Text(
                                    if (suggestions == 1) "1 unbekannte Nummer" else "$suggestions unbekannte Nummern",
                                    style = MaterialTheme.typography.titleSmallEmphasized,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Text(
                                    "Öfter in Anrufliste oder SMS – als Kontakt anlegen?",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
            }

            if (sections.all { it.contacts.isEmpty() }) {
                item(key = "empty") {
                    when {
                        query.isNotEmpty() -> EmptyHint("Nichts gefunden", "Für „$query“ gibt es keinen Treffer.")
                        filter == F_DUE -> EmptyHint("Niemand fällig ✨", "Du bist mit allen auf dem Laufenden.")
                        filter != F_ALL -> EmptyHint("Leer", "In diesem Filter ist niemand.")
                        else -> EmptyHint("Dein Orbit ist noch leer", "Tippe auf + und lege die ersten Menschen an – neu oder aus deinen Handy-Kontakten.")
                    }
                }
            }

            sections.forEach { section ->
                if (section.contacts.isEmpty()) return@forEach
                stickyHeader(key = "h-${section.title}") {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(start = 28.dp, top = 12.dp, bottom = 6.dp),
                    )
                }
                itemsIndexed(section.contacts, key = { _, c -> "${section.title}-${c.id}" }) { i, c ->
                    ContactRow(
                        contact = c,
                        idx = idx,
                        index = i,
                        count = section.contacts.size,
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
    idx: Index,
    index: Int,
    count: Int,
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
        modifier = modifier.padding(horizontal = 16.dp, vertical = 1.dp),
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
            val (color, icon, alignment) = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Triple(MaterialTheme.colorScheme.primary, Icons.Rounded.Call, Alignment.CenterStart)
                SwipeToDismissBoxValue.EndToStart -> Triple(MaterialTheme.colorScheme.tertiary, Icons.Rounded.EditNote, Alignment.CenterEnd)
                else -> Triple(Color.Transparent, null, Alignment.Center)
            }
            Box(
                modifier = Modifier.fillMaxSize().background(color, RoundedCornerShape(24.dp)).padding(horizontal = 24.dp),
                contentAlignment = alignment,
            ) {
                if (icon != null) {
                    Icon(
                        icon, null,
                        tint = if (direction == SwipeToDismissBoxValue.StartToEnd) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onTertiary,
                    )
                }
            }
        },
    ) {
        ListItem(
            onClick = onOpen,
            leadingContent = { Avatar(contact, contactColor(contact, idx), 44.dp) },
            supportingContent = {
                Text(
                    since?.let { "zuletzt ${Fmt.ago(it)}" } ?: contact.company.ifBlank { "noch kein Kontakt" },
                    maxLines = 1,
                )
            },
            trailingContent = if (contact.favorite) {
                { Icon(Icons.Rounded.Star, "Favorit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
            } else {
                null
            },
            shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Text(Logic.displayName(contact, lastFirst), maxLines = 1)
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
            ) {
                val progress = checkedProgress
                Icon(
                    if (progress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add,
                    contentDescription = "Kontakt hinzufügen",
                    modifier = Modifier.animateIcon({ checkedProgress }),
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
        )
    }
}
