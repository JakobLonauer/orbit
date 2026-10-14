package io.github.thelony97.orbit.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.domain.PhotoMonths
import io.github.thelony97.orbit.core.domain.Visibility
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.zone
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.components.EmptyHint
import io.github.thelony97.orbit.ui.components.OrbButton
import io.github.thelony97.orbit.ui.components.SpaceBackground
import io.github.thelony97.orbit.ui.list.AlphabetRail
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * Fotos-Seite (PLAN 3.10): every picture by the date it was taken, month headers and a month
 * scrubber at the edge; [contactId] = one contact's folder („Alle“ from the contact page).
 */
@Composable
fun PhotosScreen(contactId: String?, data: OrbitData, idx: Index, nav: Nav) {
    val owner = contactId?.let { idx.contacts[it] }
    val orgsShown = io.github.thelony97.orbit.ui.Session.audience != io.github.thelony97.orbit.domain.Audience.PEOPLE
    val months = remember(data, contactId, orgsShown) {
        PhotoMonths.build(data.contacts, zone()) { c ->
            if (contactId != null) c.id == contactId
            // Same visibility as the list: organisations only when they are shown there (PLAN 2.3).
            else io.github.thelony97.orbit.core.domain.Visibility.inLists(c, orgsShown)
        }
    }
    val flat = remember(months) { months.flatMap { it.items } }
    var viewer by remember { mutableStateOf<Int?>(null) }
    val gallery = rememberGalleryImport(contactId.orEmpty())
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // LazyColumn index of each month header: header item 0, then per month 1 + rows.
    val monthIndex = remember(months) {
        var i = 1
        months.associate { m -> (m.key to i).also { i += 1 + (m.items.size + 2) / 3 } }
    }
    SpaceBackground {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item(key = "head") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OrbButton(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", nav.back, size = 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Fotos" + (owner?.let { " · " + Logic.shortName(it) } ?: "") + " · ${flat.size}",
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            color = Space.Text,
                        )
                    }
                }
                // W6: a contact's empty folder offers the one way in; the global page has nothing to add to.
                if (flat.isEmpty()) item(key = "empty") {
                    EmptyHint("Noch keine Fotos", "", action = if (contactId != null) ("Fotos hinzufügen" to { gallery.launch() }) else null)
                }
                var offset = 0
                months.forEach { m ->
                    item(key = "m-${m.key}") {
                        Text(m.title, style = MaterialTheme.typography.labelLargeEmphasized, color = Space.TextDim, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp))
                    }
                    val base = offset
                    m.items.chunked(3).forEachIndexed { r, row ->
                        item(key = "r-${m.key}-$r") {
                            // The rail's room stays free on the right (W2).
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 34.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEachIndexed { c, item ->
                                    val index = base + r * 3 + c
                                    PhotoThumb(item.photo.file, Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).clickable { viewer = index })
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    offset += m.items.size
                }
            }
            // Monats-Scrubber (PLAN 1.3): month number on the rail, „Oktober 2026“ in the bubble.
            if (months.size >= 3) {
                val byKey = months.associateBy { it.key }
                AlphabetRail(
                    letters = months.map { it.key },
                    onLetter = { k -> monthIndex[k]?.let { scope.launch { listState.scrollToItem(it) } } },
                    modifier = Modifier.align(Alignment.CenterEnd),
                    // Month number: „J“ would not tell Juni from Juli (and Jänner).
                    railLabel = { k -> k.substringAfterLast('-').toIntOrNull()?.toString().orEmpty() },
                    bubbleLabel = { k -> byKey[k]?.title.orEmpty() },
                )
            }
        }
    }
    viewer?.let { start -> PhotoViewer(flat, start, onDismiss = { viewer = null }) }
}
