package io.github.thelony97.orbit.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.designsystem.Spacing
import io.github.thelony97.orbit.ui.theme.Space
import io.github.thelony97.orbit.core.domain.FieldGhosts
import io.github.thelony97.orbit.core.model.LabeledValue
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.ui.components.SectionCard
import io.github.thelony97.orbit.util.ExtraStatus
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

/** PLAN 9 E16: the found picture as a dashed planet; tap takes it, holding shows where it is from. */
@Composable
/**
 * E1-01/W2-01: one shared notice when extras (real photo, mentions, ghosts from SMS) didn't
 * load for this contact – e.g. no network – instead of each one failing silently on its own.
 * One tap retries exactly the extras that failed.
 */
@Composable
internal fun ExtrasFailedRow(labels: List<String>, modifier: Modifier = Modifier) {
    SectionCard(
        title = "Zusatzinfos nicht geladen",
        icon = Icons.Rounded.CloudOff,
        modifier = modifier,
        action = {
            Icon(
                Icons.Rounded.Refresh, "Erneut versuchen",
                tint = Space.Violet,
                modifier = Modifier.size(28.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { ExtraStatus.retryAll(labels) }
                    .padding(5.dp),
            )
        },
    ) {
        Text(
            "Ein paar Zusatzinfos waren kurz nicht erreichbar.",
            style = MaterialTheme.typography.labelMedium,
            color = Space.TextFaint,
        )
    }
}

@Composable
internal fun PhotoGhost(c: Contact, f: io.github.thelony97.orbit.links.RealPhotos.Found, modifier: Modifier = Modifier) {
    val bmp = androidx.compose.runtime.remember(f.file) {
        runCatching { android.graphics.BitmapFactory.decodeFile(OrbitRepository.photoFile(f.file).path)?.asImageBitmap() }.getOrNull()
    } ?: return
    Box(modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter, vertical = 6.dp)) {
        Ghost(
            source = "Bild von " + f.source,
            onAccept = { io.github.thelony97.orbit.links.RealPhotos.take(c, f) },
            onDismiss = { OrbitRepository.editSettings { s -> s.copy(dismissedGhosts = (s.dismissedGhosts + "photo:${c.id}").distinct().takeLast(400)) } },
            dismissAxis = null,
        ) {
            Row(Modifier.padding(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Image(
                    bmp, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape),
                )
                Spacer(Modifier.width(Spacing.M))
                Text("Als Foto", style = MaterialTheme.typography.bodyMedium, color = Space.Text)
            }
        }
    }
}
