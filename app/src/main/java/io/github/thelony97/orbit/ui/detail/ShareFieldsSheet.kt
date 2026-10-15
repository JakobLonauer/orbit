package io.github.thelony97.orbit.ui.detail

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContactPage
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.actions.ShareContact
import io.github.thelony97.orbit.core.designsystem.Corners
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Motion
import io.github.thelony97.orbit.core.designsystem.Spacing
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.model.OccasionKind
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.security.AppLock
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.GhostButton
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.glass
import io.github.thelony97.orbit.ui.theme.Space
import io.github.thelony97.orbit.util.Fmt
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/** One value that can be handed on. */
private data class Piece(val key: String, val label: String, val value: String)

/** A picked field on its way from its chip into the card (PLAN 9 A12 Moment). */
private class Flight(val piece: Piece, val from: Rect) {
    val t = Animatable(0f)
}

/** Everything of [c] that can be handed on – the card, the text and the vCard are all built from this. */
private fun piecesOf(c: Contact): List<Piece> = buildList {
    if (!c.isOrg && c.nickname.isNotBlank()) add(Piece("n", "Rufname", c.nickname.trim()))
    val work = listOf(c.position, if (c.isOrg) "" else c.company).filter { it.isNotBlank() }.joinToString(" · ")
    if (work.isNotBlank()) add(Piece("w", "Arbeit", work))
    c.phones.forEachIndexed { i, p -> if (p.value.isNotBlank()) add(Piece("p$i", p.label.ifBlank { "Telefon" }, p.value.trim())) }
    c.emails.forEachIndexed { i, e -> if (e.value.isNotBlank()) add(Piece("e$i", "E-Mail", e.value.trim())) }
    c.addresses.forEachIndexed { i, a -> if (a.value.isNotBlank()) add(Piece("a$i", "Adresse", a.value.trim().replace("\n", ", "))) }
    if (!c.isOrg) c.birthday?.let { add(Piece("b", "Geburtstag", Fmt.dayOfYear(it))) }
    c.links.forEachIndexed { i, l -> if (l.url.isNotBlank()) add(Piece("l$i", l.title.ifBlank { "Link" }, l.url.trim())) }
    c.fields.forEachIndexed { i, f -> if (f.value.isNotBlank()) add(Piece("f$i", f.label.ifBlank { "Feld" }, f.value.trim())) }
}

/** Only what is on the card goes out – as vCard or QR. */
private fun handedOn(c: Contact, picked: Set<String>): Contact {
    fun <T> keep(list: List<T>, prefix: String) = list.filterIndexed { i, _ -> "$prefix$i" in picked }
    val phones = keep(c.phones, "p")
    // A person known only by the company keeps it, otherwise the card would have no name.
    val nameless = !c.isOrg && c.firstName.isBlank() && c.lastName.isBlank()
    return c.copy(
        nickname = if ("n" in picked) c.nickname else "",
        company = if (c.isOrg || nameless || "w" in picked) c.company else "",
        position = if ("w" in picked) c.position else "",
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

/**
 * Weitergeben mit Feldwahl (PLAN 9 A12): tap the fields to hand on – each one flies from its chip
 * into a small card, and the card, shrinking into the pressed button, becomes the share (text,
 * vCard or QR). What the card shows is exactly what goes out.
 */
@Composable
fun ShareFieldsSheet(c: Contact, color: Color, onDismiss: () -> Unit, onQr: (Contact) -> Unit) {
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pieces = remember(c) { piecesOf(c) }
    // The default number and the first mail are what one usually passes on.
    var picked by remember(c) {
        mutableStateOf(
            setOfNotNull(
                pieces.firstOrNull { it.key == "p${c.defaultPhone}" }?.key ?: pieces.firstOrNull { it.key.startsWith("p") }?.key,
                pieces.firstOrNull { it.key.startsWith("e") }?.key,
            ),
        )
    }
    // Rows whose value has arrived on the card; the first picks are there from the start.
    val landed = remember(c) { mutableStateMapOf<String, Boolean>().apply { picked.forEach { put(it, true) } } }
    val chipAt = remember { mutableStateMapOf<String, Rect>() }
    val valueAt = remember { mutableStateMapOf<String, Rect>() }
    val flights = remember { mutableStateListOf<Flight>() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var cardAt by remember { mutableStateOf(Rect.Zero) }
    val buttonAt = remember { mutableStateMapOf<String, Offset>() }
    val send = remember { Animatable(0f) }
    var sendFrom by remember { mutableStateOf(Offset.Zero) }
    var sendTo by remember { mutableStateOf(Offset.Zero) }

    fun toggle(p: Piece) {
        haptics.perform(Haptic.Tick)
        if (p.key in picked) {
            picked = picked - p.key
            landed.remove(p.key)
            return
        }
        picked = picked + p.key
        val from = chipAt[p.key]
        if (from == null) {
            landed[p.key] = true
            return
        }
        val flight = Flight(p, from)
        flights += flight
        scope.launch {
            flight.t.animateTo(1f, tween(Motion.EMPHASIZED_MS, easing = Motion.Emphasized))
            flights.remove(flight)
            if (p.key in picked) {
                landed[p.key] = true
                // The haptic sits on the landing, the peak of the movement (PLAN 1.2).
                haptics.perform(Haptic.Segment)
            }
        }
    }

    fun text(): String = buildString {
        append(Logic.titledName(c))
        pieces.filter { it.key in picked }.forEach { p -> append("\n").append(p.label).append(": ").append(p.value) }
    }

    /** The card shrinks into the pressed button – then the system sheet takes over. */
    fun send(button: String, action: () -> Unit) {
        if (send.isRunning || send.value > 0f) return
        haptics.perform(Haptic.Confirm)
        sendFrom = cardAt.center
        sendTo = buttonAt[button] ?: cardAt.center
        scope.launch {
            send.animateTo(1f, tween(Motion.MEDIUM_MS, easing = Motion.Exit))
            action()
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Space.PanelHigh) {
        Box(Modifier.fillMaxWidth().onGloballyPositioned { origin = it.positionInRoot() }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.L).navigationBarsPadding().padding(bottom = Spacing.L),
                verticalArrangement = Arrangement.spacedBy(Spacing.M),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    pieces.forEach { p ->
                        SpaceChip(
                            // Numbers by their last digits („Mobil · 4567“), everything else by its name.
                            if (p.key.startsWith("p")) p.label + " · " + p.value.filter(Char::isDigit).takeLast(4) else p.label,
                            p.key in picked,
                            { toggle(p) },
                            modifier = Modifier.onGloballyPositioned { chipAt[p.key] = it.boundsInRoot() },
                        )
                    }
                }
                ShareCard(
                    c = c,
                    color = color,
                    pieces = pieces,
                    picked = picked,
                    landed = landed,
                    onValueAt = { key, r -> valueAt[key] = r },
                    modifier = Modifier
                        .onGloballyPositioned { cardAt = it.boundsInRoot() }
                        .graphicsLayer {
                            val t = send.value
                            translationX = (sendTo.x - sendFrom.x) * t
                            translationY = (sendTo.y - sendFrom.y) * t
                            val s = 1f - 0.85f * t
                            scaleX = s
                            scaleY = s
                            alpha = 1f - t
                        },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    GlowButton(
                        "Text",
                        {
                            send("text") {
                                AppLock.allowExternal()
                                val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text())
                                runCatching { ctx.startActivity(Intent.createChooser(intent, null)) }
                            }
                        },
                        modifier = Modifier.onGloballyPositioned { buttonAt["text"] = it.boundsInRoot().center },
                        icon = Icons.Rounded.Share,
                    )
                    GhostButton(
                        "vCard",
                        { send("vcard") { ShareContact.vcard(ctx, handedOn(c, picked), withFields = true) } },
                        modifier = Modifier.onGloballyPositioned { buttonAt["vcard"] = it.boundsInRoot().center },
                        icon = Icons.Rounded.ContactPage,
                    )
                    GhostButton(
                        "QR",
                        {
                            val part = handedOn(c, picked)
                            send("qr") { onQr(part) }
                        },
                        modifier = Modifier.onGloballyPositioned { buttonAt["qr"] = it.boundsInRoot().center },
                        icon = Icons.Rounded.QrCode2,
                    )
                }
            }
            // The fields in flight, above everything (drawn in the sheet's own coordinates).
            flights.forEach { f ->
                val pad = with(density) { Offset(8.dp.toPx(), 4.dp.toPx()) }
                val arc = with(density) { 48.dp.toPx() }
                Text(
                    f.piece.value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Space.Text,
                    maxLines = 1,
                    modifier = Modifier
                        .offset {
                            val t = f.t.value
                            val start = f.from.topLeft
                            val end = (valueAt[f.piece.key]?.topLeft ?: Offset(cardAt.left + 100f, cardAt.bottom - 60f)) - pad
                            // A gentle arc over the chips, not a straight slide.
                            val ctrl = Offset((start.x + end.x) / 2f, min(start.y, end.y) - arc)
                            val u = 1f - t
                            val p = start * (u * u) + ctrl * (2f * u * t) + end * (t * t) - origin
                            IntOffset(p.x.roundToInt(), p.y.roundToInt())
                        }
                        .drawBehind {
                            val a = 1f - f.t.value
                            drawRoundRect(Space.PanelHighest.copy(alpha = 0.95f * a), cornerRadius = CornerRadius(size.height / 2))
                            drawRoundRect(
                                Space.Violet.copy(alpha = 0.75f * a),
                                cornerRadius = CornerRadius(size.height / 2),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** The small card the picked fields gather on: planet · name, then one row per field. */
@Composable
private fun ShareCard(
    c: Contact,
    color: Color,
    pieces: List<Piece>,
    picked: Set<String>,
    landed: Map<String, Boolean>,
    onValueAt: (String, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .glass(Corners.Card, tint = Space.Panel, border = color.copy(alpha = 0.45f))
            .animateContentSize(tween(Motion.MEDIUM_MS, easing = Motion.Emphasized))
            .padding(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.XS),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(c, color, 36.dp)
            Spacer(Modifier.width(Spacing.M))
            Text(
                Logic.titledName(c),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = Space.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(Spacing.XS))
        pieces.forEach { p ->
            AnimatedVisibility(
                p.key in picked,
                enter = expandVertically(tween(Motion.MEDIUM_MS, easing = Motion.Emphasized)),
                exit = shrinkVertically(tween(Motion.FAST_MS)) + fadeOut(tween(Motion.FAST_MS)),
            ) {
                val shown by animateFloatAsState(if (landed[p.key] == true) 1f else 0f, tween(Motion.FAST_MS), label = "landed")
                Row(Modifier.graphicsLayer { alpha = shown }) {
                    // One label column: every value starts at the same x, on the label's baseline.
                    Text(
                        p.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = Space.TextFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(88.dp).alignByBaseline(),
                    )
                    Text(
                        p.value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Space.Text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .alignByBaseline()
                            .onGloballyPositioned { onValueAt(p.key, it.boundsInRoot()) },
                    )
                }
            }
        }
    }
}
