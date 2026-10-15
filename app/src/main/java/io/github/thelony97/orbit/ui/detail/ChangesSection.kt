package io.github.thelony97.orbit.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import io.github.thelony97.orbit.core.designsystem.Corners
import io.github.thelony97.orbit.core.designsystem.Spacing
import io.github.thelony97.orbit.core.domain.ChangeLog
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.store.ChangeLogFile
import io.github.thelony97.orbit.ui.components.SectionCard
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.Undo
import io.github.thelony97.orbit.ui.theme.Space
import io.github.thelony97.orbit.util.Fmt

private const val PREVIEW = 40
private const val SHOWN = 12

/** This contact's changes of the last 30 days (PLAN 9 E23), newest first. */
@Composable
internal fun rememberChanges(contactId: String): List<ChangeLog.Change> {
    val ctx = LocalContext.current
    remember { ChangeLogFile.load(ctx) }
    val all by ChangeLogFile.all.collectAsState()
    return remember(all, contactId) { all.filter { it.contactId == contactId }.sortedByDescending { it.at } }
}

/**
 * Änderungen (PLAN 9 E23): folded to one line; open, every change shows old → new and „Zurück“
 * puts that one field back (with Rückgängig).
 */
@Composable
internal fun ChangesSection(c: Contact, changes: List<ChangeLog.Change>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    SectionCard(title = "Änderungen · ${changes.size}", modifier = modifier, icon = Icons.Rounded.History) {
        Text(
            if (open) "Weniger" else "Letzte 30 Tage",
            style = MaterialTheme.typography.labelLarge, color = Space.Violet,
            modifier = Modifier.clip(Corners.Row).clickable { open = !open }.padding(vertical = Spacing.XS),
        )
        AnimatedVisibility(open) {
            Column {
                changes.take(SHOWN).forEach { ch ->
                    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.XS), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ChangeLog.label(ch.field) + " · " + Fmt.whenShort(ch.at), style = MaterialTheme.typography.labelMedium, color = Space.TextDim)
                            Text(
                                ChangeLog.preview(ch.old).take(PREVIEW) + "  →  " + ChangeLog.preview(ch.new).take(PREVIEW),
                                style = MaterialTheme.typography.bodySmall, color = Space.Text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // Only while the field still holds this change's value (a later change wins).
                        val revertible = remember(c, ch) { ChangeLog.canRevert(c, ch) }
                        if (revertible) {
                            SpaceChip("Zurück", false, {
                                OrbitRepository.editContact(c.id) { ChangeLog.revert(it, ch) }
                                Undo.offer(ChangeLog.label(ch.field) + " zurück") {
                                    OrbitRepository.editContact(c.id) { x -> ChangeLog.redo(x, ch) }
                                }
                            }, accent = Space.Violet)
                        }
                    }
                }
            }
        }
    }
}
