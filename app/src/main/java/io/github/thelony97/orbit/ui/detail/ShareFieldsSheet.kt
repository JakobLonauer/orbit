package io.github.thelony97.orbit.ui.detail

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.actions.ShareContact
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Motion
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.model.OccasionKind
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.security.AppLock
import io.github.thelony97.orbit.ui.components.GhostButton
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.glass
import io.github.thelony97.orbit.ui.theme.Space
import io.github.thelony97.orbit.util.Fmt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One value that can be handed on. */
private data class Piece(val key: String, val label: String, val value: String)

/**
 * Weitergeben mit Feldwahl (PLAN 9 A12): tap the fields to hand on – they gather on a small card
 * that becomes the share; as text, vCard or QR.
 */
@Composable
fun ShareFieldsSheet(c: Contact, onDismiss: () -> Unit, onQr: (Contact) -> Unit) {
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pieces = remember(c) {
        buildList {
            c.phones.forEachIndexed { i, p -> if (p.value.isNotBlank()) add(Piece("p$i", p.label.ifBlank { "Telefon" }, p.value.trim())) }
            c.emails.forEachIndexed { i, e -> if (e.value.isNotBlank()) add(Piece("e$i", "E-Mail", e.value.trim())) }
            c.addresses.forEachIndexed { i, a -> if (a.value.isNotBlank()) add(Piece("a$i", "Adresse", a.value.trim())) }
            if (!c.isOrg) c.birthday?.let { add(Piece("b", "Geburtstag", Fmt.dayOfYear(it))) }
            c.links.forEachIndexed { i, l -> if (l.url.isNotBlank()) add(Piece("l$i", l.title.ifBlank { "Link" }, l.url.trim())) }
            c.fields.forEachIndexed { i, f -> if (f.value.isNotBlank()) add(Piece("f$i", f.label.ifBlank { "Feld" }, f.value.trim())) }
        }
    }
    // The default number and the first mail are what one usually passes on.
    var picked by remember(c) {
        mutableStateOf(
            setOfNotNull(
                pieces.firstOrNull { it.key == "p${c.defaultPhone}" }?.key ?: pieces.firstOrNull { it.key.startsWith("p") }?.key,
                pieces.firstOrNull { it.key.startsWith("e") }?.key,
            ),
        )
    }
    var sending by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (sending) 0.94f else 1f, tween(Motion.FAST_MS), label = "send")

    fun filtered(): Contact {
        fun <T> keep(list: List<T>, prefix: String) = list.filterIndexed { i, _ -> "$prefix$i" in picked }
        val phones = keep(c.phones, "p")
        return c.copy(
            phones = phones,
            defaultPhone = phones.indexOfFirst { it == c.phones.getOrNull(c.defaultPhone) }.coerceAtLeast(0),
            emails = keep(c.emails, "e"),
            addresses = keep(c.addresses, "a"),
            links = keep(c.links, "l"),
            fields = keep(c.fields, "f"),
            dates = if ("b" in picked) c.dates.filter { it.kind == OccasionKind.BIRTHDAY } else emptyList(),
            notes = "", pinnedNote = "", howWeMet = "", topics = emptyList(), gifts = emptyList(),
        )
    }

    fun text(): String = buildString {
        append(Logic.titledName(c))
        if (!c.isOrg && c.company.isNotBlank()) append("\n").append(listOf(c.position, c.company).filter { it.isNotBlank() }.joinToString(" · "))
        pieces.filter { it.key in picked }.forEach { p -> append("\n").append(p.label).append(": ").append(p.value) }
    }

    /** The little card shrinks into the share – then the system sheet takes over. */
    fun send(action: () -> Unit) {
        haptics.perform(Haptic.Confirm)
        sending = true
        scope.launch {
            delay(Motion.FAST_MS.toLong())
            action()
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Space.PanelHigh) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(Logic.titledName(c), style = MaterialTheme.typography.titleLargeEmphasized, color = Space.Text)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pieces.forEach { p ->
                    val on = p.key in picked
                    SpaceChip(
                        if (p.key.startsWith("p") || p.key.startsWith("f")) p.label + " " + p.value.takeLast(4) else p.label,
                        on,
                        {
                            haptics.perform(Haptic.Tick)
                            picked = if (on) picked - p.key else picked + p.key
                        },
                        accent = Space.Teal,
                    )
                }
            }
            // The card the picked fields gather on (PLAN 9 A12 Moment).
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { scaleX = press; scaleY = press }
                    .glass(androidx.compose.foundation.shape.RoundedCornerShape(20.dp), tint = Space.Panel, border = Space.Teal.copy(alpha = 0.4f))
                    .animateContentSize(tween(Motion.MEDIUM_MS))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(Logic.titledName(c), style = MaterialTheme.typography.titleMedium, color = Space.Text)
                pieces.forEach { p ->
                    AnimatedVisibility(
                        p.key in picked,
                        enter = expandVertically(tween(Motion.MEDIUM_MS, easing = Motion.Emphasized)) + fadeIn(tween(Motion.MEDIUM_MS)),
                        exit = shrinkVertically(tween(Motion.FAST_MS)) + fadeOut(tween(Motion.FAST_MS)),
                    ) {
                        Row {
                            Text(p.label + "  ", style = MaterialTheme.typography.labelMedium, color = Space.TextFaint)
                            Text(p.value, style = MaterialTheme.typography.bodyMedium, color = Space.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlowButton("Teilen", {
                    send {
                        AppLock.allowExternal()
                        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text())
                        runCatching { ctx.startActivity(Intent.createChooser(intent, null)) }
                    }
                }, icon = Icons.Rounded.Share, color = Space.Teal)
                GhostButton("vCard", { send { ShareContact.vcard(ctx, filtered()) } })
                GhostButton("QR", { val f = filtered(); send { onQr(f) } }, icon = Icons.Rounded.QrCode2)
            }
        }
    }
}
