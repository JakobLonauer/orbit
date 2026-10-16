package io.github.thelony97.orbit.ui.detail

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.actions.ContactActions
import io.github.thelony97.orbit.call.Persist
import io.github.thelony97.orbit.call.PhoneRole
import io.github.thelony97.orbit.core.designsystem.Corners
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Spacing
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.domain.Redial
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.Phones
import io.github.thelony97.orbit.domain.zone
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.theme.Space
import java.time.LocalDateTime

/** „7:30“, tomorrow „morgen 7:30“. */
internal fun persistLabel(at: LocalDateTime, now: LocalDateTime): String {
    val time = "%d:%02d".format(at.hour, at.minute)
    return if (at.toLocalDate() == now.toLocalDate()) time else "morgen $time"
}

/** The next time [hour]:[minute] comes round – today if still ahead, else tomorrow. */
internal fun nextAt(hour: Int, minute: Int, now: LocalDateTime): LocalDateTime {
    val today = now.toLocalDate().atTime(hour, minute)
    return if (today.isAfter(now)) today else today.plusDays(1)
}

/**
 * Halten auf „Anrufen“ (PLAN 9 B5): which number (the star makes it the default), and
 * Dranbleiben – now as the phone app, at one of the next half hours, or at a time of Jakob's.
 */
@Composable
internal fun CallHoldSheet(c: Contact, data: OrbitData, onDismiss: () -> Unit, onSetDefault: (Int) -> Unit) {
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val phoneApp = remember { PhoneRole.held(ctx) }
    val iso = remember { Phones.countryIso(ctx) }
    var picking by remember { mutableStateOf(false) }
    val number = ContactActions.defaultNumber(c)
    val name = Logic.displayName(c, data.settings.sortByLastName)
    val now = remember { LocalDateTime.now(zone()) }
    val phones = c.phones.withIndex().filter { it.value.value.isNotBlank() }

    fun plan(at: LocalDateTime) {
        val n = number ?: return
        Persist.plan(ctx, n, name, c.id, at.atZone(zone()).toInstant().toEpochMilli())
        haptics.perform(Haptic.Confirm)
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Space.PanelHigh) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.L).navigationBarsPadding().padding(bottom = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            phones.forEach { (i, p) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(Corners.Row)
                        .clickable {
                            haptics.perform(Haptic.Confirm)
                            ContactActions.call(ctx, c, p.value, data.settings)
                            onDismiss()
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Call, null, tint = Space.Teal, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(Spacing.M))
                    Column(Modifier.weight(1f)) {
                        Text(Phones.pretty(p.value, iso), style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"), color = Space.Text)
                        if (p.label.isNotBlank()) Text(p.label, style = MaterialTheme.typography.labelMedium, color = Space.TextDim)
                    }
                    if (phones.size > 1) {
                        IconButton(onClick = { haptics.perform(Haptic.Tick); onSetDefault(i) }) {
                            Icon(if (c.defaultPhone == i) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Als Standard", tint = Space.Violet)
                        }
                    }
                }
            }
            if (number != null) {
                HorizontalDivider(color = Space.Line)
                Row(Modifier.padding(top = Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Repeat, null, tint = Space.Teal, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.S))
                    Text("Dranbleiben", style = MaterialTheme.typography.titleSmallEmphasized, color = Space.Text)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    // Redialling needs the phone role (PLAN 9 B5); without it only planned reminders.
                    if (phoneApp) {
                        SpaceChip("Jetzt", false, {
                            haptics.perform(Haptic.Confirm)
                            Persist.startNow(ctx, number, name, c.id)
                            onDismiss()
                        }, leading = Icons.Rounded.Call, accent = Space.Teal)
                    }
                    Redial.offers(now).forEach { at ->
                        SpaceChip(persistLabel(at, now), false, { plan(at) }, accent = Space.Teal)
                    }
                    SpaceChip("Uhrzeit", false, { picking = true }, leading = Icons.Rounded.Schedule, accent = Space.Teal)
                }
            }
        }
    }
    if (picking) {
        io.github.thelony97.orbit.ui.sheets.TimeDialog(
            initial = Redial.offers(now).first().toLocalTime(),
            onDismiss = { picking = false },
            onPick = { t -> plan(nextAt(t.hour, t.minute, LocalDateTime.now(zone()))) },
        )
    }
}
