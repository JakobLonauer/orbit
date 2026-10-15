package io.github.thelony97.orbit.ui.detail

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.thelony97.orbit.core.designsystem.Corners
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Spacing
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.domain.WifiCode
import io.github.thelony97.orbit.core.model.WifiNet
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.ui.components.GhostButton
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.SectionCard
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.SpaceField
import io.github.thelony97.orbit.ui.components.Undo
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun newWifi() = WifiNet(newId(), "")

/** The network the phone is on now (needs the location permission); null when unknown. */
@Suppress("DEPRECATION")
internal fun currentSsid(ctx: Context): String? = runCatching {
    WifiCode.ssidOf(ctx.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid)
}.getOrNull()

/** „Verbinden“ with one tap: the system's own „Netzwerk speichern?“ sheet for this WLAN. */
internal fun connect(ctx: Context, w: WifiNet) {
    val b = WifiNetworkSuggestion.Builder().setSsid(w.ssid)
    if (w.password.isNotEmpty()) {
        if (w.security.equals("WPA3", ignoreCase = true)) b.setWpa3Passphrase(w.password) else b.setWpa2Passphrase(w.password)
    }
    val list = arrayListOf(runCatching { b.build() }.getOrNull() ?: return)
    runCatching {
        ctx.startActivity(Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, list).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** WLAN einer Organisation (PLAN 9 D7): tap = Verbinden, the QR to pass it on, hold = edit. */
@Composable
internal fun WifiSection(c: Contact, onEdit: (WifiNet) -> Unit, onQr: (WifiNet) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    SectionCard(
        title = "WLAN",
        modifier = modifier,
        icon = Icons.Rounded.Wifi,
        action = {
            Icon(
                Icons.Rounded.Add, "WLAN", tint = Space.TextDim,
                modifier = Modifier.size(28.dp).clip(Corners.Row).clickable { onEdit(newWifi()) }.padding(4.dp),
            )
        },
    ) {
        c.wifi.forEach { w ->
            Row(
                Modifier.fillMaxWidth().clip(Corners.Row)
                    .combinedClickable(
                        onClick = { haptics.perform(Haptic.Confirm); connect(ctx, w) },
                        onLongClick = { haptics.perform(Haptic.Tick); onEdit(w) },
                    )
                    .padding(vertical = Spacing.S),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(30.dp).background(Space.Teal.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Wifi, null, tint = Space.Teal, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(Spacing.M))
                Column(Modifier.weight(1f)) {
                    Text(w.ssid, style = MaterialTheme.typography.bodyLarge, color = Space.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (w.password.isEmpty()) "offen" else "Verbinden", style = MaterialTheme.typography.labelMedium, color = Space.Teal)
                }
                Icon(Icons.Rounded.QrCode2, "QR", tint = Space.TextDim, modifier = Modifier.size(36.dp).clip(CircleShape).clickable { onQr(w) }.padding(6.dp))
            }
        }
    }
}

/** The WLAN as a QR (any camera joins it) with name and password underneath. */
@Composable
internal fun WifiQr(w: WifiNet, onClose: () -> Unit) {
    val payload = WifiCode.qr(w)
    val code by produceState<android.graphics.Bitmap?>(null, payload) { this.value = withContext(Dispatchers.Default) { io.github.thelony97.orbit.ui.components.qrCode(payload) } }
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.clip(RoundedCornerShape(28.dp)).background(Space.PanelHigh).clickable(interactionSource = null, indication = null, onClick = onClose).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(w.ssid, style = MaterialTheme.typography.titleLargeEmphasized, color = Space.Text)
            if (w.password.isNotEmpty()) Text(w.password, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), color = Space.Teal)
            Spacer(Modifier.height(18.dp))
            Box(Modifier.size(248.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp), contentAlignment = Alignment.Center) {
                code?.let { Image(it.asImageBitmap(), "WLAN-QR", filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize()) }
            }
        }
    }
}

/** „WLAN speichern“: the network the phone is on is taken over; the password is typed once. */
@Composable
internal fun WifiEditSheet(c: Contact, wifi: WifiNet, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    val ssid = rememberTextFieldState(wifi.ssid)
    val password = rememberTextFieldState(wifi.password)
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val known = c.wifi.any { it.id == wifi.id }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) currentSsid(ctx)?.let { s -> ssid.edit { replace(0, length, s) } }
    }

    fun save(delete: Boolean) {
        val next = wifi.copy(ssid = ssid.text.toString().trim(), password = password.text.toString())
        OrbitRepository.editContact(c.id) { o ->
            val rest = o.wifi.filter { it.id != wifi.id }
            o.copy(wifi = if (delete) rest else if (known) o.wifi.map { if (it.id == wifi.id) next else it } else o.wifi + next)
        }
        if (delete) Undo.offer("WLAN gelöscht") { OrbitRepository.editContact(c.id) { o -> if (o.wifi.any { it.id == wifi.id }) o else o.copy(wifi = o.wifi + wifi) } }
        haptics.perform(Haptic.Confirm)
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Space.PanelHigh) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.L).navigationBarsPadding().padding(bottom = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            SpaceChip("Verbundenes Netz", false, {
                val now = currentSsid(ctx)
                if (now != null) ssid.edit { replace(0, length, now) } else permission.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
            }, leading = Icons.Rounded.Wifi, accent = Space.Teal)
            SpaceField(ssid, "Netzwerk")
            SpaceField(password, "Passwort")
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (known) GhostButton("Löschen", { save(true) }, color = Space.Coral)
                Spacer(Modifier.weight(1f))
                GlowButton("Speichern", { save(false) }, enabled = ssid.text.isNotBlank())
            }
        }
    }
}
