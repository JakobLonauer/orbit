package io.github.thelony97.orbit.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.designsystem.Corners
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Spacing
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.domain.Contracts
import io.github.thelony97.orbit.core.model.Contract
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.today
import io.github.thelony97.orbit.ui.components.GhostButton
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.SectionCard
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.SpaceField
import io.github.thelony97.orbit.ui.components.Undo
import io.github.thelony97.orbit.ui.sheets.DateDialog
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private fun dmy(d: LocalDate) = "${d.dayOfMonth}.${d.monthValue}.${d.year}"

internal fun newContract() = Contract(java.util.UUID.randomUUID().toString(), start = today().toString())

/** The line under a contract's name: the deadline, or how it ends. */
internal fun contractLine(c: Contract, today: LocalDate): String {
    val t = Contracts.term(c, today) ?: return "Beginn fehlt"
    val days = t.days
    val deadline = t.deadline
    return when {
        t.over -> "beendet ${dmy(t.end)}"
        t.cancelled -> "gekündigt · endet ${dmy(t.end)}"
        deadline != null && days != null -> Contracts.deadlineLine(days, deadline).replaceFirstChar { it.uppercase() } +
            if (days > 1) " · noch $days T" else ""
        else -> "läuft bis ${dmy(t.end)}"
    }
}

/**
 * Verträge (PLAN 9 D3): each contract with its Laufzeit-Ring; tap edits. Pulling the planet out of
 * the ring writes the cancellation mail and marks the contract gekündigt (Rückgängig).
 */
@Composable
internal fun ContractSection(org: Contact, data: OrbitData, onEdit: (Contract) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val today = today()
    SectionCard(
        title = "Verträge",
        modifier = modifier,
        icon = Icons.Rounded.Description,
        action = {
            Icon(
                Icons.Rounded.Add, "Vertrag", tint = Space.TextDim,
                modifier = Modifier.size(28.dp).clip(Corners.Row).clickable { onEdit(newContract()) }.padding(4.dp),
            )
        },
    ) {
        org.contracts.forEach { c ->
            Row(
                Modifier.fillMaxWidth().clip(Corners.Row).clickable { onEdit(c) }.padding(vertical = Spacing.S),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ContractRing(c, today, onPulled = { cancel(ctx, org, c, data) })
                Spacer(Modifier.width(Spacing.M))
                Column(Modifier.weight(1f)) {
                    Text(Contracts.title(c), style = MaterialTheme.typography.bodyLarge, color = Space.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = listOf(c.number, c.price).filter { it.isNotBlank() }.joinToString(" · ")
                    if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = Space.TextDim, maxLines = 1)
                    val t = Contracts.term(c, today)
                    Text(
                        contractLine(c, today),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = if (t?.inWindow == true) Space.Orange else Space.TextFaint,
                    )
                }
            }
        }
    }
}

/** The cancellation mail from the template; the contract counts as gekündigt (Rückgängig). */
private fun cancel(ctx: android.content.Context, org: Contact, c: Contract, data: OrbitData) {
    val t = Contracts.term(c, today()) ?: return
    val me = data.contacts.firstOrNull { it.isMe }?.let { Logic.displayName(it, false) }.orEmpty()
    val (subject, body) = Contracts.cancelMail(c, t.end, me)
    val to = org.emails.firstOrNull { it.value.isNotBlank() }?.value.orEmpty()
    val uri = Uri.parse("mailto:" + Uri.encode(to) + "?subject=" + Uri.encode(subject) + "&body=" + Uri.encode(body))
    runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    if (c.cancelledOn != null) return
    val day = today().toString()
    OrbitRepository.editContact(org.id) { o -> o.copy(contracts = o.contracts.map { if (it.id == c.id) it.copy(cancelledOn = day) else it }) }
    Undo.offer("Gekündigt") {
        OrbitRepository.editContact(org.id) { o -> o.copy(contracts = o.contracts.map { if (it.id == c.id) it.copy(cancelledOn = null) else it }) }
    }
}

private val RingSize = 64.dp
private const val PULL_DP = 44f

/**
 * Laufzeit als Ring (PLAN 9 D3 Moment): the term runs clockwise from the top in teal, the
 * Kündigungsfenster is orange, after the deadline the rest is faint; in the middle the days to the
 * deadline („−77“). The planet sits where today is; pulled out past the rim it snaps (Einrasten),
 * let go there it [onPulled], else it springs back.
 */
@Composable
internal fun ContractRing(c: Contract, today: LocalDate, onPulled: () -> Unit, modifier: Modifier = Modifier) {
    val t = Contracts.term(c, today)
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val pull = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var armed by remember { mutableStateOf(false) }
    val pulled by androidx.compose.runtime.rememberUpdatedState(onPulled)
    val progress = t?.progress(today) ?: 0f
    val windowAt = t?.windowAt()
    val deadlineAt = t?.deadlineAt()
    val accent = when {
        t == null || t.over -> Space.TextFaint
        t.cancelled -> Space.Teal
        t.inWindow -> Space.Orange
        else -> Space.Teal
    }
    Box(modifier.size(RingSize), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(c.id, t?.cancelled) {
                if (t == null || t.over) return@pointerInput
                var acc = Offset.Zero
                detectDragGestures(
                    onDragEnd = {
                        acc = Offset.Zero
                        val go = armed
                        armed = false
                        if (go) {
                            haptics.perform(Haptic.Confirm)
                            pulled()
                        }
                        scope.launch { pull.animateTo(Offset.Zero, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
                    },
                    onDragCancel = {
                        acc = Offset.Zero
                        armed = false
                        scope.launch { pull.animateTo(Offset.Zero, spring()) }
                    },
                ) { change, drag ->
                    change.consume()
                    acc += drag
                    val to = acc
                    scope.launch { pull.snapTo(to) }
                    val far = with(density) { hypot(to.x, to.y).toDp().value } > PULL_DP
                    if (far != armed) {
                        armed = far
                        haptics.perform(if (far) Haptic.Threshold else Haptic.Tick)
                    }
                }
            },
        ) { drawRing(Ring(progress, windowAt, deadlineAt, accent, armed), pull.value) }
        Text(
            ringLabel(t),
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = if (t?.inWindow == true) Space.Orange else Space.Text,
            modifier = Modifier.graphicsLayer { alpha = if (pull.value == Offset.Zero) 1f else 0.4f },
        )
    }
}

private fun ringLabel(t: Contracts.Term?): String = when {
    t == null -> "–"
    t.over -> "aus"
    t.cancelled -> "✓"
    t.days != null -> "−${t.days}"
    else -> ""
}

private data class Ring(val progress: Float, val windowAt: Float?, val deadlineAt: Float?, val accent: Color, val armed: Boolean)

private fun DrawScope.drawRing(ring: Ring, pull: Offset) {
    val w = 5.dp.toPx()
    val inset = w / 2 + 4.dp.toPx()
    val arc = Size(size.width - inset * 2, size.height - inset * 2)
    val tl = Offset(inset, inset)
    drawArc(Space.TextFaint.copy(alpha = 0.25f), 0f, 360f, false, tl, arc, style = Stroke(w))
    if (ring.windowAt != null && ring.deadlineAt != null) {
        drawArc(Space.Orange.copy(alpha = 0.55f), -90f + 360f * ring.windowAt, 360f * (ring.deadlineAt - ring.windowAt).coerceAtLeast(0.01f), false, tl, arc, style = Stroke(w))
    }
    drawArc(ring.accent, -90f, 360f * ring.progress, false, tl, arc, style = Stroke(w, cap = StrokeCap.Round))
    // The planet: today on the ring, or pulled away by the finger (with a tether).
    val r = arc.width / 2
    val a = (-90f + 360f * ring.progress) * PI.toFloat() / 180f
    val home = Offset(center.x + r * cos(a), center.y + r * sin(a))
    val at = home + pull
    if (pull != Offset.Zero) drawLine(ring.accent.copy(alpha = 0.5f), home, at, 1.5.dp.toPx(), cap = StrokeCap.Round)
    drawCircle(if (ring.armed) Space.Coral else ring.accent, if (ring.armed) 7.dp.toPx() else 5.5.dp.toPx(), at)
}

private val MIN_MONTHS = listOf(0, 12, 24, 36)
private val RENEW_MONTHS = listOf(0, 1, 12, 24)
private val NOTICE = listOf(0 to 14, 0 to 30, 1 to 0, 2 to 0, 3 to 0, 6 to 0)

private fun noticeLabel(m: Int, d: Int) = if (m > 0) "$m M" else "$d T"

/** Vertrag bearbeiten (PLAN 9 D3): name, number, price as typed; Laufzeiten and Frist as chips. */
@Composable
internal fun ContractEditSheet(org: Contact, contract: Contract, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    val name = rememberTextFieldState(contract.name)
    val number = rememberTextFieldState(contract.number)
    val price = rememberTextFieldState(contract.price)
    var start by remember { mutableStateOf(Contracts.day(contract.start) ?: today()) }
    var minMonths by remember { mutableIntStateOf(contract.minMonths) }
    var renew by remember { mutableIntStateOf(contract.renewMonths) }
    var notice by remember { mutableStateOf(contract.noticeMonths to contract.noticeDays) }
    var cancelled by remember { mutableStateOf(contract.cancelledOn) }
    var pickStart by remember { mutableStateOf(false) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val known = org.contracts.any { it.id == contract.id }

    fun save(delete: Boolean) {
        val next = contract.copy(
            name = name.text.toString().trim(), number = number.text.toString().trim(), price = price.text.toString().trim(),
            start = start.toString(), minMonths = minMonths, renewMonths = renew, noticeMonths = notice.first, noticeDays = notice.second,
            cancelledOn = cancelled,
        )
        OrbitRepository.editContact(org.id) { o ->
            val rest = o.contracts.filter { it.id != contract.id }
            o.copy(contracts = if (delete) rest else if (known) o.contracts.map { if (it.id == contract.id) next else it } else o.contracts + next)
        }
        if (delete) Undo.offer("Vertrag gelöscht") {
            OrbitRepository.editContact(org.id) { o -> if (o.contracts.any { it.id == contract.id }) o else o.copy(contracts = o.contracts + contract) }
        }
        haptics.perform(Haptic.Confirm)
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Space.PanelHigh) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.L).navigationBarsPadding().padding(bottom = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            SpaceField(name, "Vertrag", placeholder = "Handy")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                SpaceField(number, "Nummer", Modifier.weight(1f))
                SpaceField(price, "Preis", Modifier.weight(1f), placeholder = "19,90 €/M", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
            }
            ContractChips("Beginn", Space.Teal) {
                SpaceChip(dmy(start), true, { pickStart = true }, leading = Icons.Rounded.Event, accent = Space.Teal)
            }
            ContractChips("Mindestlaufzeit", Space.Teal) {
                MIN_MONTHS.forEach { m -> SpaceChip(if (m == 0) "keine" else "$m M", minMonths == m, { minMonths = m }, accent = Space.Teal) }
            }
            ContractChips("Verlängerung", Space.Teal) {
                RENEW_MONTHS.forEach { m -> SpaceChip(if (m == 0) "unbefristet" else "$m M", renew == m, { renew = m }, accent = Space.Teal) }
            }
            ContractChips("Kündigungsfrist", Space.Orange) {
                NOTICE.forEach { n -> SpaceChip(noticeLabel(n.first, n.second), notice == n, { notice = n }, accent = Space.Orange) }
            }
            ContractChips("Status", Space.Coral) {
                SpaceChip("gekündigt", cancelled != null, { cancelled = if (cancelled == null) today().toString() else null }, accent = Space.Coral)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (known) GhostButton("Löschen", { save(true) }, color = Space.Coral)
                Spacer(Modifier.weight(1f))
                GlowButton("Speichern", { save(false) })
            }
        }
    }
    if (pickStart) DateDialog(start, onDismiss = { pickStart = false }, onPick = { start = it; pickStart = false })
}

@Composable
private fun ContractChips(label: String, color: Color, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.85f))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) { content() }
    }
}
