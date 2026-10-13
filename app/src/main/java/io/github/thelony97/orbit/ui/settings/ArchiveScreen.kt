package io.github.thelony97.orbit.ui.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.domain.AtFormat
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.toLocalDate
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.EmptyHint
import io.github.thelony97.orbit.ui.components.OrbButton
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.SpaceSearchField
import io.github.thelony97.orbit.ui.theme.Space

private enum class ArchiveSort(val label: String) { ARCHIVED("Archiviert"), NAME("Name"), LAST("Letzter Kontakt") }

/**
 * Archiv (PLAN 3.11): the only place archived contacts appear. Search covers the archive only,
 * grouped by the year they were archived. Contacts are drawn as distant, desaturated stars.
 */
@Composable
fun ArchiveScreen(data: OrbitData, idx: Index, nav: Nav) {
    val search = rememberTextFieldState()
    val q = search.text.toString()
    var sort by rememberSaveable { mutableStateOf(ArchiveSort.ARCHIVED) }
    val haptics = rememberHaptics()
    val lastFirst = data.settings.sortByLastName

    val archived = remember(data) { data.contacts.filter { it.deletedAt == null && it.archived && !it.isMe } }
    val sections = remember(data, q, sort) {
        val hits = archived.filter { Logic.matches(it, q, idx) }
        val sorted = when (sort) {
            ArchiveSort.ARCHIVED -> hits.sortedByDescending { archivedAt(it) }
            ArchiveSort.NAME -> hits.sortedBy { Logic.sortKey(it, lastFirst) }
            ArchiveSort.LAST -> hits.sortedByDescending { Logic.lastContactAt(it) ?: Long.MIN_VALUE }
        }
        sorted.groupBy { archivedAt(it).takeIf { t -> t > 0 }?.toLocalDate()?.year ?: 0 }
            .toSortedMap(compareByDescending<Int> { it })
            .map { (year, list) -> year to list }
    }

    ManageScaffold("Archiv", nav) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 4.dp, bottom = 120.dp),
        ) {
            item(key = "search") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SpaceSearchField(search, "Im Archiv suchen", Modifier.fillMaxWidth(), leading = Icons.Rounded.Search)
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ArchiveSort.entries.forEach { s ->
                            SpaceChip(s.label, sort == s, { sort = s }, accent = Space.TextDim)
                        }
                    }
                }
            }
            if (sections.isEmpty()) {
                item(key = "empty") {
                    if (archived.isEmpty()) EmptyHint("Archiv ist leer", "")
                    else EmptyHint("Nichts gefunden", "", action = "Suche leeren" to { search.clearText() })
                }
            }
            sections.forEach { (year, list) ->
                item(key = "y-$year") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (year == 0) "Ohne Datum" else "$year", style = MaterialTheme.typography.labelLargeEmphasized, color = Space.TextDim)
                        Spacer(Modifier.width(8.dp))
                        Text("${list.size}", style = MaterialTheme.typography.labelMedium, color = Space.TextFaint)
                    }
                }
                items(list, key = { "a-${it.id}" }) { c ->
                    ArchiveRow(
                        c = c,
                        lastFirst = lastFirst,
                        onOpen = { nav.open(Route.Detail(c.id)) },
                        onRestore = {
                            haptics.perform(Haptic.Confirm)
                            OrbitRepository.editContact(c.id) { it.copy(archived = false, archivedAt = null) }
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/** When it went into the archive; contacts archived before 1.0.8 fall back to their last change. */
internal fun archivedAt(c: Contact): Long = c.archivedAt ?: c.updatedAt

/** "archiviert im März · zuletzt 2025 · 12 Notizen · 34 Einträge" (the year is the section header). */
internal fun archiveLine(c: Contact): String = buildList {
    val archivedYear = archivedAt(c).takeIf { it > 0 }?.toLocalDate()?.also { add("archiviert im ${AtFormat.month(it.monthValue)}") }?.year
    c.entries.maxOfOrNull { it.at }?.toLocalDate()?.year?.let { y -> if (y != archivedYear) add("zuletzt $y") }
    val notes = c.entries.count { it.note.isNotBlank() } + (if (c.notes.isNotBlank()) 1 else 0)
    if (notes > 0) add(if (notes == 1) "1\u00A0Notiz" else "$notes\u00A0Notizen")
    if (c.entries.isNotEmpty()) add(if (c.entries.size == 1) "1\u00A0Eintrag" else "${c.entries.size}\u00A0Einträge")
}.joinToString(" · ")

@Composable
private fun ArchiveRow(c: Contact, lastFirst: Boolean, onOpen: () -> Unit, onRestore: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A distant star: photo and ring without colour.
        val grey = remember {
            android.graphics.RenderEffect.createColorFilterEffect(
                android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(0f) }),
            ).asComposeRenderEffect()
        }
        Box(Modifier.graphicsLayer { renderEffect = grey; alpha = 0.7f }) {
            Avatar(c, Space.TextDim, 42.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(Logic.displayName(c, lastFirst), style = MaterialTheme.typography.bodyLarge, color = Space.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val role = listOf(c.position, if (c.isOrg) "" else c.company).filter { it.isNotBlank() }.joinToString(" · ")
            if (role.isNotEmpty()) {
                Text(role, style = MaterialTheme.typography.labelMedium, color = Space.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(archiveLine(c), style = MaterialTheme.typography.labelSmall, color = Space.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(1.dp))
        }
        OrbButton(Icons.Rounded.Unarchive, "Zurückholen", onRestore, size = 36.dp, accent = Space.Teal)
    }
}
