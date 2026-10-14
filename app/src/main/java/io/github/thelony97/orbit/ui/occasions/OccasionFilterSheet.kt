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
import androidx.compose.material.icons.rounded.Business
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
                .padding(horizontal = 18.dp)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Tune, null, tint = Space.Coral, modifier = Modifier.size(20.dp))
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
            Title("Sortierung")
            Chips {
                OccasionList.Sort.entries.forEach { s -> SpaceChip(s.label, view.sort == s, { onChange(view.copy(sort = s)) }, accent = Space.Coral) }
                SpaceChip(if (view.reversed) "↑" else "↓", view.reversed, { onChange(view.copy(reversed = !view.reversed)) }, accent = Space.Coral)
            }
            Title("Art")
            Chips {
                OccasionKind.entries.forEach { k ->
                    val on = k in view.kinds
                    SpaceChip(k.label, on, { onChange(view.copy(kinds = if (on) view.kinds - k else view.kinds + k)) })
                }
            }
            Title("Zeitraum")
            Chips {
                listOf(7, 30, 90).forEach { d -> SpaceChip("$d Tage", view.withinDays == d, { onChange(view.copy(withinDays = d)) }) }
                SpaceChip("Ganzes Jahr", view.withinDays == null, { onChange(view.copy(withinDays = null)) })
                SpaceChip("Nur runde", view.onlyRound, { onChange(view.copy(onlyRound = !view.onlyRound)) }, accent = Space.Orange)
            }
            if (groups.isNotEmpty()) {
                Title("Gruppe")
                Chips {
                    groups.forEach { g ->
                        val on = view.groupId == g.id
                        SpaceChip(g.name, on, { onChange(view.copy(groupId = if (on) null else g.id)) }, accent = Color(g.color))
                    }
                }
            }
            Chips {
                SpaceChip("Organisationen", view.orgs, { onChange(view.copy(orgs = !view.orgs)) }, leading = Icons.Rounded.Business, accent = Space.Blue)
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Space.TextDim, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Chips(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}
