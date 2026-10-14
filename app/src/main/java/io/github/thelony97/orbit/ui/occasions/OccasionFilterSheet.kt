package io.github.thelony97.orbit.ui.occasions

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
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.domain.OccasionList
import io.github.thelony97.orbit.core.model.OccasionKind
import io.github.thelony97.orbit.data.Group
import io.github.thelony97.orbit.ui.components.GhostButton
import io.github.thelony97.orbit.ui.components.SectionTitle
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.theme.Space

/** What „Angepinnt“ and „Alle Anlässe“ show (PLAN 3.5): filters, sorting and organisations. */
@Immutable
data class OccasionView(
    val kinds: Set<OccasionKind> = emptySet(),
    val groupId: String? = null,
    val withinDays: Int? = null,
    val onlyRound: Boolean = false,
    val orgs: Boolean = false,
    val sort: OccasionList.Sort = OccasionList.Sort.NEXT,
    val reversed: Boolean = false,
) : java.io.Serializable {
    /** For the badge on the filter button; the sorting counts once. */
    fun activeCount(): Int =
        kinds.size.coerceAtMost(1) + (if (groupId != null) 1 else 0) + (if (withinDays != null) 1 else 0) +
            (if (onlyRound) 1 else 0) + (if (orgs) 1 else 0) + (if (sort != OccasionList.Sort.NEXT || reversed) 1 else 0)

    fun filter(pinnedOnly: Boolean) = OccasionList.Filter(kinds, groupId, withinDays, onlyRound, pinnedOnly)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OccasionFilterSheet(
    view: OccasionView,
    groups: List<Group>,
    /** The kinds that occur at all (W5: no chip for a kind nobody has). */
    kinds: List<OccasionKind>,
    /** Whether any organisation has an occasion; otherwise no toggle for them. */
    hasOrgs: Boolean,
    onChange: (OccasionView) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
            // Same build as the contact list's sheet (PLAN 3.5 „wie in der Kontaktliste“).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Tune, null, tint = Space.Violet, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Filter & Sortierung", style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
                if (view.activeCount() > 0) {
                    Text(
                        "Zurücksetzen",
                        style = MaterialTheme.typography.labelLarge,
                        color = Space.Coral,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onChange(OccasionView()) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
            val shownKinds = OccasionKind.entries.filter { it in kinds || it in view.kinds }
            if (shownKinds.size > 1) {
                SectionTitle("Art", Icons.Rounded.Category, modifier = Modifier.padding(top = 8.dp))
                Chips {
                    shownKinds.forEach { k ->
                        val on = k in view.kinds
                        SpaceChip(k.label, on, { onChange(view.copy(kinds = if (on) view.kinds - k else view.kinds + k)) })
                    }
                }
            }
            SectionTitle("Zeitraum", Icons.Rounded.DateRange, modifier = Modifier.padding(top = 12.dp))
            Chips {
                listOf(7, 30, 90).forEach { d -> SpaceChip("$d Tage", view.withinDays == d, { onChange(view.copy(withinDays = d)) }) }
                SpaceChip("Ganzes Jahr", view.withinDays == null, { onChange(view.copy(withinDays = null)) })
            }
            Chips {
                SpaceChip("Nur runde", view.onlyRound, { onChange(view.copy(onlyRound = !view.onlyRound)) })
                if (hasOrgs || view.orgs) {
                    SpaceChip("Organisationen", view.orgs, { onChange(view.copy(orgs = !view.orgs)) }, leading = Icons.Rounded.Business, accent = Space.Blue)
                }
            }
            if (groups.isNotEmpty()) {
                SectionTitle("Gruppe", Icons.Rounded.Groups, modifier = Modifier.padding(top = 12.dp))
                Chips {
                    groups.forEach { g ->
                        val on = view.groupId == g.id
                        SpaceChip(g.name, on, { onChange(view.copy(groupId = if (on) null else g.id)) }, accent = Color(g.color))
                    }
                }
            }
            SectionTitle("Sortieren", Icons.AutoMirrored.Rounded.Sort, modifier = Modifier.padding(top = 12.dp))
            Chips {
                OccasionList.Sort.entries.forEach { s -> SpaceChip(s.label, view.sort == s, { onChange(view.copy(sort = s, reversed = false)) }, accent = Space.Teal) }
            }
            Row(Modifier.padding(top = 6.dp)) {
                GhostButton(
                    if (view.reversed) "Umgekehrt" else "Reihenfolge umkehren",
                    { onChange(view.copy(reversed = !view.reversed)) },
                    icon = Icons.Rounded.SwapVert,
                    color = if (view.reversed) Space.Teal else Space.Text,
                )
            }
        }
    }
}

@Composable
private fun Chips(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}
