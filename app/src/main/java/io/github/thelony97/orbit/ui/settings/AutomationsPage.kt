package io.github.thelony97.orbit.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import io.github.thelony97.orbit.core.designsystem.Spacing
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

/** How often an automation ran – always a number, also „0×“. */
private fun ran(n: Int) = "${n}×"

/** How many automations are on (for the area's status line). */
fun activeAutomations(data: OrbitData): Int {
    val s = data.settings
    return listOf(
        s.screening && s.screenNight,
        s.autoBusySms,
        s.callbackNudge,
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
    // The SMS goes out by itself and the appointment comes from the calendar – both asked when switched on.
    val busyPermissions = arrayOf(android.Manifest.permission.SEND_SMS, android.Manifest.permission.READ_CALENDAR)
    val askBusy = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> if (result.values.all { it }) OrbitRepository.editSettings { it.copy(autoBusySms = true) } }
    fun setBusy(on: Boolean) {
        val missing = busyPermissions.filter { ctx.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (on && missing.isNotEmpty()) askBusy.launch(missing.toTypedArray()) else OrbitRepository.editSettings { it.copy(autoBusySms = on) }
    }
    val calendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) OrbitRepository.editSettings { it.copy(callbackNudge = true) }
    }
    fun setNudge(on: Boolean) {
        val missing = ctx.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED
        if (on && missing) calendar.launch(android.Manifest.permission.READ_CALENDAR) else OrbitRepository.editSettings { it.copy(callbackNudge = on) }
    }
    val busyGroups = data.groups.filter { it.id in s.autoBusyGroups }
    Column(modifier) {
        SettingsGroup("") {
            SettingItem(
                Icons.Rounded.EventBusy, "Verpasst im Termin → SMS",
                subtitle = (busyGroups.joinToString(" · ") { it.name }.ifBlank { "Alle" }) + " · " + ran(counts.getValue(AutoCount.BUSY_SMS)),
                iconTint = Space.Teal,
                trailing = { SpaceSwitch(s.autoBusySms) { v -> setBusy(v) } },
            )
            if (s.autoBusySms && data.groups.isNotEmpty()) BusyGroups(data)
            Hairline()
            SettingItem(
                Icons.Rounded.Call, "Termin endet → Rückruf-Stapel",
                subtitle = ran(counts.getValue(AutoCount.NUDGE)),
                iconTint = Space.Teal,
                trailing = { SpaceSwitch(s.callbackNudge) { v -> setNudge(v) } },
            )
            Hairline()
            SettingItem(
                Icons.Rounded.Bedtime, "Unbekannt nachts → stumm",
                subtitle = (if (s.screening) "22–7 Uhr" else "Anruf-Filter aus") + " · " + ran(counts.getValue(AutoCount.SCREEN)),
                iconTint = Space.Coral,
                // Without the call filter the rule cannot run – the switch shows off, not a dimmed „on“.
                trailing = { SpaceSwitch(s.screening && s.screenNight, enabled = s.screening) { v -> OrbitRepository.editSettings { it.copy(screenNight = v) } } },
            )
        }
        SettingsGroup("") {
            val sims = data.groups.filter { it.simId != null }
            SettingItem(Icons.Rounded.SimCard, "Gruppe → SIM", subtitle = sims.joinToString(" · ") { it.name }.ifBlank { null }, iconTint = Space.Violet, onClick = onGroups, trailing = { Chevron() })
            Hairline()
            val tones = data.groups.filter { it.ringtone != null }
            SettingItem(Icons.Rounded.LibraryMusic, "Gruppe → Klingelton", subtitle = tones.joinToString(" · ") { it.name }.ifBlank { null }, iconTint = Space.Violet, onClick = onGroups, trailing = { Chevron() })
            Hairline()
            val series = data.contacts.count { it.deletedAt == null && it.numberPrefixes.isNotEmpty() }
            SettingItem(
                Icons.Rounded.Numbers, "Nummernserie → Organisation",
                subtitle = when (series) {
                    0 -> null
                    1 -> "1 Organisation"
                    else -> "$series Organisationen"
                },
                iconTint = Space.Blue,
            )
        }
    }
}

@Composable
private fun BusyGroups(data: OrbitData) {
    val s = data.settings
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 56.dp, end = Spacing.Gutter, bottom = Spacing.S),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
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

@Composable
private fun Chevron() {
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Space.TextDim)
}
