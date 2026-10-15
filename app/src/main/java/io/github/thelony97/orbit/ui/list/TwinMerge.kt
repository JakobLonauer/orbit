package io.github.thelony97.orbit.ui.list

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Motion
import io.github.thelony97.orbit.core.designsystem.Spacing
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.domain.Twins
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.Ghost
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.Undo
import io.github.thelony97.orbit.ui.components.fallbackColor
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.launch

/** PLAN 9 E5: „Doppelstern“ in Heute – the two as overlapping planets; tap opens the merge. */
@Composable
fun TwinGhost(a: Contact, b: Contact, key: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var open by remember(key) { mutableStateOf(false) }
    Ghost(source = "Gleiche Nummer, Mail oder Name mit Geburtstag", onAccept = { open = true }, onDismiss = onDismiss, modifier = modifier, dismissAxis = null) {
        Row(Modifier.padding(Spacing.M), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(52.dp)) {
                Avatar(a, fallbackColor(a.id), 34.dp)
                Avatar(b, fallbackColor(b.id), 34.dp, Modifier.offset(x = 18.dp))
            }
            Spacer(Modifier.width(Spacing.M))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Space.Violet, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Doppelstern", style = MaterialTheme.typography.titleSmallEmphasized, color = Space.Text)
                }
                Text(Logic.shortName(a) + " · " + Logic.shortName(b), style = MaterialTheme.typography.labelMedium, color = Space.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (open) MergeSheet(a, b, onDismiss = { open = false })
}

/**
 * Zusammenführen (PLAN 9 E5): who stays (the one with more history), one chip pair per
 * conflicting field, the counter „142 + 37 → 179 Einträge“. Verschmelzen pulls the two planets
 * into one (Moment), then merges; Rückgängig puts everything back.
 */
@Composable
fun MergeSheet(a: Contact, b: Contact, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val (keep, gone) = if (a.entries.size >= b.entries.size) a to b else b to a
    val conflicts = remember(keep, gone) { Twins.conflicts(keep, gone) }
    var fromGone by remember { mutableStateOf(emptySet<String>()) }
    val fuse = remember { Animatable(0f) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun merge() {
        scope.launch {
            haptics.perform(Haptic.Threshold)
            fuse.animateTo(1f, tween(Motion.MEDIUM_MS, easing = Motion.Emphasized))
            haptics.perform(Haptic.Confirm)
            var before: io.github.thelony97.orbit.core.model.OrbitDoc? = null
            OrbitRepository.edit { d ->
                before = d
                Twins.apply(d, keep.id, gone.id, fromGone, System.currentTimeMillis())
            }
            Undo.offer("Zusammengeführt") {
                val old = before ?: return@offer
                val byId = old.contacts.associateBy { it.id }
                fun touched(o: Contact, c: Contact) = c.id == keep.id || c.id == gone.id || o.dates != c.dates || o.entries != c.entries || o.vacationSubstitute != c.vacationSubstitute
                OrbitRepository.edit { d ->
                    d.copy(
                        contacts = d.contacts.map { c -> byId[c.id]?.takeIf { o -> o !== c && touched(o, c) } ?: c },
                        relations = old.relations, orgLinks = old.orgLinks, meetings = old.meetings,
                    )
                }
            }
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Space.PanelHigh) {
        Column(
            modifier.fillMaxWidth().padding(horizontal = Spacing.L).navigationBarsPadding().padding(bottom = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(keep, fallbackColor(keep.id), 64.dp, Modifier.graphicsLayer { translationX = fuse.value * 36.dp.toPx() })
                Spacer(Modifier.width(Spacing.L))
                Avatar(gone, fallbackColor(gone.id), 64.dp, Modifier.graphicsLayer {
                    translationX = -fuse.value * 44.dp.toPx()
                    alpha = 1f - fuse.value
                    scaleX = 1f - 0.3f * fuse.value
                    scaleY = 1f - 0.3f * fuse.value
                })
            }
            Text(Twins.countLine(keep, gone), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), color = Space.Text)
            conflicts.forEach { c ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(c.label, style = MaterialTheme.typography.labelMedium, color = Space.TextDim)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        SpaceChip(c.keep.take(CHIP_CHARS), c.field !in fromGone, { fromGone = fromGone - c.field }, accent = Space.Violet)
                        SpaceChip(c.gone.take(CHIP_CHARS), c.field in fromGone, { fromGone = fromGone + c.field }, accent = Space.Violet)
                    }
                }
            }
            GlowButton("Verschmelzen", { merge() }, icon = Icons.Rounded.AutoAwesome)
        }
    }
}

private const val CHIP_CHARS = 28
