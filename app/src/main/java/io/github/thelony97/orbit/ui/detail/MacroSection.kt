package io.github.thelony97.orbit.ui.detail

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.actions.ContactActions
import io.github.thelony97.orbit.call.MacroPlayer
import io.github.thelony97.orbit.call.PhoneRole
import io.github.thelony97.orbit.core.designsystem.Corners
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Spacing
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.domain.Macros
import io.github.thelony97.orbit.core.model.Macro
import io.github.thelony97.orbit.core.model.MacroStep
import io.github.thelony97.orbit.core.model.MacroStepKind
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.ui.components.GhostButton
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.SectionCard
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.SpaceField
import io.github.thelony97.orbit.ui.theme.Space

/** Name of an own field (the organisation's first, then Jakob's). */
private fun fieldName(org: Contact, me: Contact?, id: String): String? =
    (org.fields + me?.fields.orEmpty()).firstOrNull { it.id == id }?.label

/**
 * „Makro wählen“ (PLAN 9 B4): calls the organisation and plays the macro once it is answered – as
 * the phone app with Orbit's own keypad, else as a dial string for the phone's dialer („,“ pause,
 * „;“ wait).
 */
fun dialMacro(ctx: android.content.Context, org: Contact, macro: Macro, data: OrbitData) {
    val number = ContactActions.defaultNumber(org) ?: return
    val me = data.contacts.firstOrNull { it.isMe }
    if (PhoneRole.held(ctx)) {
        MacroPlayer.pending = MacroPlayer.Pending(number, macro, org, me)
        ContactActions.call(ctx, org, number, data.settings)
    } else {
        val tail = Macros.dialString(macro.steps) { MacroPlayer.fieldValue(org, me, it) }
        ContactActions.call(ctx, org, number + tail, data.settings)
    }
}

/** The organisation's macros: tap = Makro wählen, hold = edit (PLAN 9.2 Bediensprache). */
@Composable
internal fun MacroSection(org: Contact, data: OrbitData, onEdit: (Macro) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    val me = data.contacts.firstOrNull { it.isMe }
    SectionCard(title = "Makros", modifier = modifier, icon = Icons.Rounded.Dialpad) {
        org.macros.forEach { m ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(Corners.Row)
                    .combinedClickable(
                        onClick = { haptics.perform(Haptic.Confirm); dialMacro(ctx, org, m, data) },
                        onLongClick = { haptics.perform(Haptic.Tick); onEdit(m) },
                    )
                    .padding(vertical = Spacing.S),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(m.name.ifBlank { "Makro" }, style = MaterialTheme.typography.bodyLarge, color = Space.Text)
                    Text(
                        Macros.label(m.steps) { fieldName(org, me, it) },
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = Space.TextDim,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Rounded.PlayArrow, "Makro wählen", tint = Space.Teal, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/**
 * Makro bearbeiten (PLAN 9 B4): the steps as chips – a tap removes one; keys, +1 s pause,
 * „Warten auf Weiter“ and own fields are added at the end.
 */
@Composable
internal fun MacroEditSheet(org: Contact, data: OrbitData, macro: Macro, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    val me = data.contacts.firstOrNull { it.isMe }
    val name = rememberTextFieldState(macro.name)
    var steps by remember(macro.id) { mutableStateOf(macro.steps) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun add(step: MacroStep) {
        haptics.perform(Haptic.Tick)
        val last = steps.lastOrNull()
        // +1 s on a pause makes it longer instead of a second chip.
        steps = if (step.kind == MacroStepKind.PAUSE && last?.kind == MacroStepKind.PAUSE) steps.dropLast(1) + last.copy(ms = last.ms + step.ms) else steps + step
    }

    fun save(newSteps: List<MacroStep>?) {
        OrbitRepository.editContact(org.id) { c ->
            c.copy(macros = if (newSteps == null) c.macros.filter { it.id != macro.id } else c.macros.map { if (it.id == macro.id) it.copy(name = name.text.toString().trim(), steps = newSteps) else it })
        }
        haptics.perform(Haptic.Confirm)
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Space.PanelHigh) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.L).navigationBarsPadding().padding(bottom = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            SpaceField(name, "Name", placeholder = "Kundenservice")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                steps.forEachIndexed { i, s ->
                    val label = when (s.kind) {
                        MacroStepKind.KEY -> s.value
                        MacroStepKind.PAUSE -> "${s.ms / 1_000} s"
                        MacroStepKind.WAIT -> "Weiter"
                        MacroStepKind.FIELD -> fieldName(org, me, s.value) ?: "Feld"
                    }
                    SpaceChip(
                        label, true,
                        { haptics.perform(Haptic.Tick); steps = steps.filterIndexed { j, _ -> j != i } },
                        trailing = Icons.Rounded.Close,
                        accent = if (s.kind == MacroStepKind.FIELD) Space.Violet else Space.Teal,
                    )
                }
            }
            // Keys.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                "123456789*0#".forEach { k -> SpaceChip(k.toString(), false, { add(MacroStep(MacroStepKind.KEY, k.toString())) }) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                SpaceChip("+1 s", false, { add(MacroStep(MacroStepKind.PAUSE, ms = 1_000)) }, leading = Icons.Rounded.HourglassEmpty)
                SpaceChip("Warten", false, { add(MacroStep(MacroStepKind.WAIT)) }, leading = Icons.Rounded.TouchApp)
                (org.fields + me?.fields.orEmpty()).forEach { f ->
                    SpaceChip(f.label.ifBlank { "Feld" }, false, { add(MacroStep(MacroStepKind.FIELD, f.id)) }, leading = Icons.Rounded.Add, accent = Space.Violet)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                GhostButton("Löschen", { save(null) }, color = Space.Coral)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(Spacing.S))
                GlowButton("Speichern", { save(steps) }, enabled = steps.isNotEmpty())
            }
        }
    }
}
