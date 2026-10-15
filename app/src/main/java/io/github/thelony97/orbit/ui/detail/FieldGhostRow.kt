package io.github.thelony97.orbit.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.designsystem.Spacing
import io.github.thelony97.orbit.core.domain.FieldGhosts
import io.github.thelony97.orbit.core.model.LabeledValue
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.ui.components.Ghost
import io.github.thelony97.orbit.ui.theme.Space

/** Takes a ghost into the card (number, mail or address). */
private fun take(c: Contact, g: FieldGhosts.Ghost) {
    val v = LabeledValue("", g.value)
    OrbitRepository.editContact(c.id) { x ->
        when (g.kind) {
            FieldGhosts.Kind.PHONE -> x.copy(phones = x.phones + v)
            FieldGhosts.Kind.EMAIL -> x.copy(emails = x.emails + v)
            FieldGhosts.Kind.ADDRESS -> x.copy(addresses = x.addresses + v)
        }
    }
}

/**
 * Geister aus SMS (PLAN 9 E3): dashed fields where they would land; tap takes it into the card,
 * holding shows the message it came from (and Verwerfen).
 */
@Composable
internal fun FieldGhostRow(c: Contact, ghosts: List<FieldGhosts.Ghost>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ghosts.forEach { g ->
            Ghost(
                source = g.source,
                onAccept = { take(c, g) },
                onDismiss = {
                    val key = g.key(c.id)
                    OrbitRepository.editSettings { s -> s.copy(dismissedGhosts = (s.dismissedGhosts + key).distinct().takeLast(400)) }
                },
                dismissAxis = null,
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.M, vertical = Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when (g.kind) {
                            FieldGhosts.Kind.PHONE -> Icons.Rounded.Call
                            FieldGhosts.Kind.EMAIL -> Icons.Rounded.Email
                            FieldGhosts.Kind.ADDRESS -> Icons.Rounded.Place
                        },
                        null, tint = Space.Violet, modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(Spacing.S))
                    Text(g.value, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = Space.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
