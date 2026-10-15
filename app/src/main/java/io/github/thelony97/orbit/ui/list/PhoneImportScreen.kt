package io.github.thelony97.orbit.ui.list

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Motion
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.domain.Duplicates
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.Mutations
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.Phones
import io.github.thelony97.orbit.sync.Sync
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.PhoneImport
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.components.EmptyHint
import io.github.thelony97.orbit.ui.components.ListPlaceholder
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.SpaceSearchField
import io.github.thelony97.orbit.ui.components.Undo
import io.github.thelony97.orbit.ui.settings.ManageScaffold
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * Mehrere Handy-Kontakte auf einmal übernehmen (PLAN 3.14). Organisations follow the fixed rule
 * „Firma ohne Personennamen“ ([PhoneImport.read]); numbers already in Orbit show who has them
 * („Diese Nummer gibt es schon“) and are never pre-selected. One pick opens the edit page as before.
 */
@Composable
fun PhoneImportScreen(
    data: OrbitData,
    nav: Nav,
    preview: List<PhoneImport.Item>? = null,
    initialSelection: Set<Long> = emptySet(),
    /** PLAN 9 A1: show only these phone-book contacts (the ones other apps just added). */
    only: Set<Long>? = null,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var items by remember { mutableStateOf(preview) }
    LaunchedEffect(Unit) {
        if (preview == null) {
            val all = PhoneImport.list(ctx)
            items = if (only == null) all else all.filter { it.id in only }
        }
    }
    var selected by remember { mutableStateOf(initialSelection) }
    var busy by remember { mutableStateOf(false) }

    fun takeOver() {
        val all = items ?: return
        val picked = all.filter { it.id in selected }
        if (picked.isEmpty() || busy) return
        busy = true
        scope.launch {
            if (picked.size == 1) {
                val c = PhoneImport.read(ctx, picked.first().uri)
                busy = false
                if (c != null) nav.replace(Route.Edit(prefill = OrbitRepository.json.encodeToString(Contact.serializer(), c)))
                return@launch
            }
            val contacts = takeOverAll(ctx, picked)
            val ids = contacts.map { it.id }.toSet()
            haptics.perform(Haptic.Confirm)
            Undo.offer(if (contacts.size == 1) "1 Kontakt übernommen" else "${contacts.size} Kontakte übernommen") {
                OrbitRepository.edit { d -> ids.fold(d) { acc, id -> Mutations.purge(acc, id) } }
                contacts.mapNotNull { it.photo }.forEach { OrbitRepository.photoFile(it).delete() }
            }
            busy = false
            nav.back()
        }
    }

    ManageScaffold(
        "Aus Handy übernehmen", nav,
        fabLabel = if (selected.isEmpty()) null else "Übernehmen (${selected.size})",
        onFab = ::takeOver,
    ) { padding ->
        PhoneBookList(
            items = items,
            data = data,
            selected = selected,
            onSelected = { selected = it },
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 4.dp, bottom = 120.dp),
        )
    }
}

/**
 * Reads the picked phone-book contacts in full (with photo), stores them and fetches their Verlauf
 * in one pass. Shared by the import page and the first start.
 */
internal suspend fun takeOverAll(ctx: android.content.Context, picked: List<PhoneImport.Item>): List<Contact> {
    // One unreadable entry (or the permission taken away meanwhile) never stops the rest.
    val contacts = picked.mapNotNull { runCatching { PhoneImport.read(ctx, it.uri) }.getOrNull() }
    if (contacts.isEmpty()) return contacts
    OrbitRepository.edit { d -> contacts.fold(d) { acc, c -> Mutations.upsertContact(acc, c) } }
    // Saved first, for sure; the Verlauf is only a bonus on top.
    OrbitRepository.flush()
    // The contacts are saved already; a failing backfill request must never take the import down.
    runCatching { Sync.backfill(ctx, contacts.filter { it.phones.isNotEmpty() }.map { it.id }) }
        .onFailure { android.util.Log.w("Orbit", "backfill request failed", it) }
    return contacts
}

/** The phone book with search, „Alle neuen“ and the „gibt es schon“ hint; [items] null = loading. */
@Composable
internal fun PhoneBookList(
    items: List<PhoneImport.Item>?,
    data: OrbitData,
    selected: Set<Long>,
    onSelected: (Set<Long>) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    val iso = remember { Phones.countryIso(ctx) }
    val lookup = remember(data.contacts) { Duplicates.Lookup(data.contacts) { Phones.normalize(it, iso) } }
    val search = rememberTextFieldState()
    val q = search.text.toString().trim()
    val list = items
    when {
        list == null -> Box(modifier.padding(top = contentPadding.calculateTopPadding())) { ListPlaceholder() }
        list.isEmpty() -> Box(modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) { EmptyHint("Keine Kontakte im Handy", "") }
        else -> {
            val hits = remember(list, lookup) { list.associate { it.id to lookup.hit(it.numbers) } }
            val shown = remember(list, q) {
                if (q.isEmpty()) list
                else list.filter { it.name.contains(q, ignoreCase = true) || it.numbers.any { n -> n.filter(Char::isDigit).contains(q.filter(Char::isDigit).ifEmpty { "\u0000" }) } }
            }
            val fresh = shown.filter { hits[it.id] == null }.map { it.id }.toSet()
            LazyColumn(modifier.fillMaxSize(), contentPadding = contentPadding) {
                item(key = "search") {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        SpaceSearchField(search, "Im Handy suchen", Modifier.fillMaxWidth(), leading = Icons.Rounded.Search)
                        if (fresh.isNotEmpty()) {
                            val all = selected.containsAll(fresh)
                            Row(Modifier.padding(top = 10.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SpaceChip("Alle neuen · ${fresh.size}", all, { onSelected(if (all) selected - fresh else selected + fresh) })
                            }
                        }
                    }
                }
                if (shown.isEmpty()) {
                    item(key = "none") { EmptyHint("Nichts gefunden", "", action = "Suche leeren" to { search.clearText() }) }
                }
                items(shown, key = { it.id }) { item ->
                    val hit = hits[item.id]
                    val owner = hit?.let { h -> data.contacts.firstOrNull { it.id == h.contactId } }
                    ImportRow(
                        item = item,
                        selected = item.id in selected,
                        already = owner?.let { Logic.displayName(it, data.settings.sortByLastName) + if (it.archived) " (Archiv)" else "" },
                        onToggle = {
                            haptics.perform(Haptic.Tick)
                            onSelected(if (item.id in selected) selected - item.id else selected + item.id)
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportRow(item: PhoneImport.Item, selected: Boolean, already: String?, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val fill by animateColorAsState(if (selected) Space.Violet else Space.Panel, Motion.fast(), label = "pick")
    Row(
        // One row height with or without a second line (steady rhythm); rows already in Orbit recede.
        modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onToggle)
            .graphicsLayer { alpha = if (already != null && !selected) 0.6f else 1f }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).background(fill, CircleShape).border(1.dp, if (selected) Space.Violet else Space.TextFaint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Rounded.Check, null, tint = Space.Void, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, color = Space.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Quiet note, not an error; the name only when it differs from the phone's.
            val sub = when {
                already == null -> item.numbers.firstOrNull().orEmpty()
                already.equals(item.name.trim(), ignoreCase = true) -> "schon im Orbit"
                else -> "schon im Orbit · $already"
            }
            if (sub.isNotEmpty()) {
                Text(sub, style = MaterialTheme.typography.labelMedium, color = Space.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * vCard-Datei mit mehreren Karten (PLAN 9 A2): picked like the phone book – cards whose number is
 * already in Orbit are shown as such and not pre-selected; one undo for the whole take-over.
 */
@Composable
fun CardImportScreen(data: OrbitData, nav: Nav, drafts: List<Contact>) {
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    val iso = remember { Phones.countryIso(ctx) }
    val lookup = remember(data.contacts) { Duplicates.Lookup(data.contacts) { Phones.normalize(it, iso) } }
    val rows = remember(drafts) {
        drafts.mapIndexed { i, c -> PhoneImport.Item(i.toLong(), Logic.displayName(c, false), c.phones.map { it.value }) }
    }
    val hits = remember(rows, lookup) { rows.associate { it.id to lookup.hit(it.numbers) } }
    var selected by remember(rows) { mutableStateOf(rows.filter { hits[it.id] == null }.map { it.id }.toSet()) }

    fun takeOver() {
        val picked = drafts.filterIndexed { i, _ -> i.toLong() in selected }
        if (picked.isEmpty()) return
        val ids = picked.map { it.id }.toSet()
        OrbitRepository.edit { d -> picked.fold(d) { acc, c -> Mutations.upsertContact(acc, c) } }
        runCatching { Sync.backfill(ctx, picked.filter { it.phones.isNotEmpty() }.map { it.id }) }
        haptics.perform(Haptic.Confirm)
        Undo.offer(if (picked.size == 1) "1 Kontakt übernommen" else "${picked.size} Kontakte übernommen") {
            OrbitRepository.edit { d -> ids.fold(d) { acc, id -> Mutations.purge(acc, id) } }
        }
        nav.back()
    }

    ManageScaffold(
        "vCard übernehmen", nav,
        fabLabel = if (selected.isEmpty()) null else "Übernehmen (${selected.size})",
        onFab = ::takeOver,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding() + 4.dp, bottom = 120.dp)) {
            items(rows, key = { it.id }) { item ->
                val owner = hits[item.id]?.let { h -> data.contacts.firstOrNull { it.id == h.contactId } }
                ImportRow(
                    item = item,
                    selected = item.id in selected,
                    already = owner?.let { Logic.displayName(it, data.settings.sortByLastName) + if (it.archived) " (Archiv)" else "" },
                    onToggle = {
                        haptics.perform(Haptic.Tick)
                        selected = if (item.id in selected) selected - item.id else selected + item.id
                    },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}
