package io.github.thelony97.orbit.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Call
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.live.AutoCount
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.theme.Space

/** „· 12×“ when it ran at all. */
private fun ran(n: Int) = if (n > 0) " · ${n}×" else ""

/** How many automations are on (for the area's status line). */
fun activeAutomations(data: OrbitData): Int {
    val s = data.settings
    return listOf(
        s.screening && s.screenNight,
        s.autoBusySms,
        true,
        data.groups.any { it.simId != null },
        data.groups.any { it.ringtone != null },
        data.contacts.any { it.numberPrefixes.isNotEmpty() },
    ).count { it }
}

/**
 * Einstellungen › Automatiken (PLAN 9 E17): every built-in automation in one place – switch,
 * what it does in one line, how often it ran. Group rules are set at the group (Kontakte).
 */
@Composable
fun AutomationsPage(data: OrbitData, onGroups: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val s = data.settings
    val counts = remember { listOf(AutoCount.SCREEN, AutoCount.NUDGE, AutoCount.BUSY_SMS).associateWith { AutoCount.get(ctx, it) } }
    androidx.compose.foundation.layout.Column(modifier) {
    SettingsGroup("") {
        SettingItem(
            Icons.Rounded.EventBusy, "Verpasst im Termin → SMS",
            subtitle = "Rückrufzeit = Terminende" + ran(counts.getValue(AutoCount.BUSY_SMS)),
            iconTint = Space.Teal,
            trailing = { SpaceSwitch(s.autoBusySms) { v -> OrbitRepository.editSettings { it.copy(autoBusySms = v) } } },
        )
        if (s.autoBusySms && data.groups.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 56.dp, end = 14.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SpaceChip("Alle", s.autoBusyGroups.isEmpty(), { OrbitRepository.editSettings { it.copy(autoBusyGroups = emptyList()) } }, accent = Space.Teal)
                data.groups.forEach { g ->
                    val on = g.id in s.autoBusyGroups
                    SpaceChip(g.name, on, {
                        OrbitRepository.editSettings { it.copy(autoBusyGroups = if (on) it.autoBusyGroups - g.id else it.autoBusyGroups + g.id) }
                    }, dot = Color(g.color), accent = Space.Teal)
                }
            }
        }
        Hairline()
        SettingItem(
            Icons.Rounded.Call, "Termin endet → Rückruf-Stapel",
            subtitle = "Wer während des Termins anrief" + ran(counts.getValue(AutoCount.NUDGE)),
            iconTint = Space.Teal,
        )
        Hairline()
        SettingItem(
            Icons.Rounded.Bedtime, "Unbekannt nachts → stumm",
            subtitle = (if (s.screening) "22–7 Uhr" else "Anruf-Filter aus") + ran(counts.getValue(AutoCount.SCREEN)),
            iconTint = Space.Teal,
            trailing = { SpaceSwitch(s.screenNight, enabled = s.screening) { v -> OrbitRepository.editSettings { it.copy(screenNight = v) } } },
        )
    }
    SettingsGroup("") {
        val sims = data.groups.filter { it.simId != null }
        SettingItem(Icons.Rounded.SimCard, "Gruppe → SIM", subtitle = sims.joinToString(" · ") { it.name }.ifBlank { null }, iconTint = Space.Orange, onClick = onGroups)
        Hairline()
        val tones = data.groups.filter { it.ringtone != null }
        SettingItem(Icons.Rounded.LibraryMusic, "Gruppe → Klingelton", subtitle = tones.joinToString(" · ") { it.name }.ifBlank { null }, iconTint = Space.Orange, onClick = onGroups)
        Hairline()
        val series = data.contacts.count { it.deletedAt == null && it.numberPrefixes.isNotEmpty() }
        SettingItem(Icons.Rounded.Numbers, "Nummernserie → Organisation", subtitle = if (series > 0) "$series Organisationen" else null, iconTint = Space.Blue)
    }
    }
}
