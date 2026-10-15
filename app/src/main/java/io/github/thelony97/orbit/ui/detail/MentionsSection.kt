package io.github.thelony97.orbit.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.designsystem.Corners
import io.github.thelony97.orbit.core.designsystem.Spacing
import io.github.thelony97.orbit.core.domain.Mentions
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.SectionCard
import io.github.thelony97.orbit.ui.components.fallbackColor
import io.github.thelony97.orbit.ui.theme.Space
import io.github.thelony97.orbit.util.Fmt

/** „Erwähnt“ (PLAN 9 E13): notes of others that name this contact with @; tap opens the other side. */
@Composable
internal fun MentionsSection(links: List<Mentions.Backlink>, data: OrbitData, nav: Nav, modifier: Modifier = Modifier) {
    val byId = data.contacts.associateBy { it.id }
    SectionCard(title = "Erwähnt", modifier = modifier, icon = Icons.Rounded.AlternateEmail) {
        links.forEach { b ->
            val from = byId[b.fromId] ?: return@forEach
            Row(
                Modifier.fillMaxWidth().clip(Corners.Row).clickable {
                    nav.open(if (b.entryId != null) Route.Detail(from.id, entryId = b.entryId) else Route.Detail(from.id))
                }.padding(vertical = Spacing.S),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(from, fallbackColor(from.id), 28.dp)
                Spacer(Modifier.width(Spacing.M))
                Column(Modifier.weight(1f)) {
                    Text(Logic.shortName(from) + " · " + Fmt.whenShort(b.at), style = MaterialTheme.typography.labelMedium, color = Space.TextDim)
                    Text(b.text.replace('\n', ' '), style = MaterialTheme.typography.bodyMedium, color = Space.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
