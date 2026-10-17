package io.github.thelony97.orbit.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.data.Mutations
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.domain.Audience
import io.github.thelony97.orbit.domain.SortMode
import io.github.thelony97.orbit.domain.StatusFilter
import io.github.thelony97.orbit.ui.Session
import io.github.thelony97.orbit.ui.components.GhostButton
import io.github.thelony97.orbit.ui.components.SectionTitle
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.theme.Space

/** Number of filters that differ from the default view (shown as badge on the filter button). */
fun activeFilterCount(data: OrbitData): Int =
    (if (Session.audience != Audience.PEOPLE) 1 else 0) +
        (if (Session.status != StatusFilter.ALL) 1 else 0) +
        Session.groups.count { id -> data.groups.any { it.id == id } } +
        (if (data.settings.listSort != SortMode.NAME.key || data.settings.listSortReversed) 1 else 0)

/** Everything that filters or sorts the list, hidden behind one button. */
@Composable
fun FilterSheet(data: OrbitData, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sort = SortMode.of(data.settings.listSort)
    val reversed = data.settings.listSortReversed
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = Space.PanelHigh,
        contentColor = Space.Text,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(36.dp, 4.dp).clip(CircleShape).background(Space.LineStrong)) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Tune, null, tint = Space.Violet, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Filter & Sortierung", style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
                // Appears with the first filter and glides away with the last (W2).
                androidx.compose.animation.AnimatedVisibility(
                    activeFilterCount(data) > 0,
                    enter = androidx.compose.animation.fadeIn(io.github.thelony97.orbit.core.designsystem.Motion.fast()),
                    exit = androidx.compose.animation.fadeOut(io.github.thelony97.orbit.core.designsystem.Motion.fast()),
                ) {
                    Text(
                        "Zurücksetzen",
                        style = MaterialTheme.typography.labelLarge,
                        color = Space.Coral,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                Session.audience = Audience.PEOPLE
                                Session.status = StatusFilter.ALL
                                Session.groups = emptySet()
                                OrbitRepository.editSettings { it.copy(listSort = SortMode.NAME.key, listSortReversed = false) }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }

            SectionTitle("Anzeigen", Icons.Rounded.Business, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Audience.entries.forEach { a ->
                    SpaceChip(a.label, Session.audience == a, { Session.audience = a }, accent = if (a == Audience.PEOPLE) Space.Violet else Space.Blue)
                }
            }
            Text(
                "Organisationen (Firmen, Praxen, Ämter …) sind sonst überall ausgeblendet. Diese Einstellung gilt, bis du die App schließt.",
                style = MaterialTheme.typography.labelMedium,
                color = Space.TextFaint,
            )

            SectionTitle("Status", modifier = Modifier.padding(top = 10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusFilter.entries.forEach { f ->
                    SpaceChip(
                        f.label, Session.status == f, { Session.status = f },
                        accent = when (f) {
                            StatusFilter.FAVORITES -> Space.Orange
                            else -> Space.Violet
                        },
                    )
                }
            }

            SectionTitle("Gruppen", Icons.Rounded.Groups, modifier = Modifier.padding(top = 10.dp))
            if (data.groups.isEmpty()) {
                Text("Noch keine Gruppen.", style = MaterialTheme.typography.bodySmall, color = Space.TextFaint)
            }
            data.groups.forEach { g ->
                val selected = g.id in Session.groups
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { Session.toggleGroup(g.id) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color(g.color) else Color.Transparent)
                            .then(Modifier.padding(4.dp)),
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (selected) Space.Void else Color(g.color)))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        g.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) Space.Text else Space.TextDim,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (g.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                        if (g.pinned) "Lösen" else "Anpinnen",
                        tint = if (g.pinned) Space.Orange else Space.TextFaint,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable { OrbitRepository.edit { d -> Mutations.upsertGroup(d, g.copy(pinned = !g.pinned)) } }
                            .padding(8.dp),
                    )
                }
            }
            Text(
                "Angepinnte Gruppen erscheinen direkt unter der Suche.",
                style = MaterialTheme.typography.labelMedium,
                color = Space.TextFaint,
            )

            SectionTitle("Sortieren", Icons.AutoMirrored.Rounded.Sort, modifier = Modifier.padding(top = 10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SortMode.entries.forEach { m ->
                    SpaceChip(m.label, sort == m, { OrbitRepository.editSettings { it.copy(listSort = m.key, listSortReversed = false) } }, accent = Space.Teal)
                }
            }
            Row(Modifier.padding(top = 6.dp)) {
                GhostButton(
                    if (reversed) "Umgekehrt: ${sort.label(true)}" else "Reihenfolge umkehren",
                    { OrbitRepository.editSettings { it.copy(listSortReversed = !it.listSortReversed) } },
                    icon = Icons.Rounded.SwapVert,
                    color = if (reversed) Space.Teal else Space.Text,
                )
            }
        }
    }
}
