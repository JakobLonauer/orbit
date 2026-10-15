package io.github.thelony97.orbit.ui.detail

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.thelony97.orbit.core.designsystem.Corners
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Spacing
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.domain.Jobs
import io.github.thelony97.orbit.core.domain.Loans
import io.github.thelony97.orbit.core.model.BuiltinTypes
import io.github.thelony97.orbit.core.model.Loan
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.Mutations
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.today
import io.github.thelony97.orbit.ui.components.GhostButton
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.SectionCard
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.SpaceField
import io.github.thelony97.orbit.ui.components.Undo
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun newLoan() = Loan(newId(), since = today().toString())

/**
 * Verliehen & Schulden (PLAN 9 D6): what is out, either way, and the money balance on top. A row
 * wiped away is settled – with a Beleg in the Verlauf and Rückgängig; tap edits; money they owe
 * offers the Zahl-QR.
 */
@Composable
internal fun LoanSection(c: Contact, data: OrbitData, onEdit: (Loan) -> Unit, onPay: (Loan) -> Unit, modifier: Modifier = Modifier) {
    val open = Loans.open(c)
    val balance = Loans.balance(c)
    val name = Logic.shortName(c)
    SectionCard(
        title = "Verliehen & Schulden",
        modifier = modifier,
        icon = Icons.Rounded.Handshake,
        action = {
            Icon(
                Icons.Rounded.Add, "Verliehen", tint = Space.TextDim,
                modifier = Modifier.size(28.dp).clip(Corners.Row).clickable { onEdit(newLoan()) }.padding(4.dp),
            )
        },
    ) {
        if (balance != 0L) {
            Text(
                if (balance > 0) "$name schuldet dir ${Jobs.format(balance)}" else "Du schuldest $name ${Jobs.format(-balance)}",
                style = MaterialTheme.typography.titleSmallEmphasized.copy(fontFeatureSettings = "tnum"),
                color = if (balance > 0) Space.Teal else Space.Orange,
                modifier = Modifier.padding(bottom = Spacing.XS),
            )
        }
        val canPay = Loans.iban(data.contacts.firstOrNull { it.isMe }) != null
        open.forEach { l -> androidx.compose.runtime.key(l.id) { LoanRow(c, l, canPay, onEdit, onPay) } }
    }
}

@Composable
private fun LoanRow(c: Contact, l: Loan, canPay: Boolean, onEdit: (Loan) -> Unit, onPay: (Loan) -> Unit) {
    val haptics = rememberHaptics()
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        onDismiss = {
            haptics.perform(Haptic.Confirm)
            settle(c, l)
        },
        backgroundContent = {
            Box(Modifier.fillMaxSize().clip(Corners.Row).background(Space.Teal.copy(alpha = 0.18f)).padding(horizontal = Spacing.M), contentAlignment = Alignment.CenterEnd) {
                Text("erledigt", style = MaterialTheme.typography.labelLarge, color = Space.Teal)
            }
        },
    ) {
        Row(
            Modifier.fillMaxWidth().clip(Corners.Row).background(Space.Panel).clickable { onEdit(l) }.padding(vertical = Spacing.S),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (l.toThem) Space.Teal else Space.Orange
            Box(Modifier.size(30.dp).background(tint.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (l.toThem) Icons.AutoMirrored.Rounded.CallMade else Icons.AutoMirrored.Rounded.CallReceived, null, tint = tint, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(Spacing.M))
            Column(Modifier.weight(1f)) {
                Text(Loans.label(l), style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"), color = Space.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (l.toThem) "von dir" else "von " + Logic.shortName(c)) + " · " + Loans.since(l, today()),
                    style = MaterialTheme.typography.labelMedium, color = Space.TextDim,
                )
            }
            if (canPay && l.toThem && l.amountCents != null) {
                Icon(
                    Icons.Rounded.QrCode2, "Zahl-QR", tint = Space.Teal,
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable { onPay(l) }.padding(6.dp),
                )
            }
        }
    }
}

/** Settled: the loan becomes history, the Verlauf gets its Beleg; Rückgängig takes both back. */
private fun settle(c: Contact, l: Loan) {
    val day = today().toString()
    val entry = io.github.thelony97.orbit.core.model.HistoryEntry(
        newId(), c.id, BuiltinTypes.SETTLED, System.currentTimeMillis(), note = Loans.receipt(l), amountCents = l.amountCents,
    )
    OrbitRepository.edit { d ->
        Mutations.addEntry(d, c.id, entry).let { x ->
            x.copy(contacts = x.contacts.map { o -> if (o.id != c.id) o else o.copy(loans = o.loans.map { if (it.id == l.id) it.copy(settledOn = day) else it }) })
        }
    }
    Undo.offer("Erledigt") {
        OrbitRepository.edit { d ->
            Mutations.deleteEntry(d, c.id, entry.id).let { x ->
                x.copy(contacts = x.contacts.map { o -> if (o.id != c.id) o else o.copy(loans = o.loans.map { if (it.id == l.id) it.copy(settledOn = null) else it }) })
            }
        }
    }
}

/** Zahl-QR (PLAN 9 D6): the open amount as a GiroCode with Jakob's IBAN – the other bank app scans it. */
@Composable
internal fun PayQr(c: Contact, l: Loan, data: OrbitData, onClose: () -> Unit) {
    val me = data.contacts.firstOrNull { it.isMe }
    val iban = Loans.iban(me)
    val cents = l.amountCents
    val payload = if (iban != null && cents != null) Loans.epc(me?.let { Logic.displayName(it, false) }.orEmpty(), iban, cents, l.what.ifBlank { "Orbit" }) else null
    val code by produceState<Bitmap?>(null, payload) { this.value = payload?.let { withContext(Dispatchers.Default) { io.github.thelony97.orbit.ui.components.qrCode(it) } } }
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.clip(RoundedCornerShape(28.dp)).background(Space.PanelHigh).clickable(interactionSource = null, indication = null, onClick = onClose).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(cents?.let(Jobs::format).orEmpty(), style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"), color = Space.Text)
            Text(Logic.shortName(c) + " · " + l.what, style = MaterialTheme.typography.labelLarge, color = Space.Teal, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Box(Modifier.size(248.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp), contentAlignment = Alignment.Center) {
                code?.let { Image(it.asImageBitmap(), "Zahl-QR", filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize()) }
            }
        }
    }
}

/** Verliehen/Geschuldet bearbeiten: who to whom, a thing or money, what. */
@Composable
internal fun LoanEditSheet(c: Contact, loan: Loan, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    val what = rememberTextFieldState(loan.what)
    val amount = rememberTextFieldState(loan.amountCents?.let { Jobs.format(it).removeSuffix(" €") }.orEmpty())
    var toThem by remember { mutableStateOf(loan.toThem) }
    var money by remember { mutableStateOf(loan.amountCents != null) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val known = c.loans.any { it.id == loan.id }
    val name = Logic.shortName(c)

    fun save(delete: Boolean) {
        val next = loan.copy(what = what.text.toString().trim(), toThem = toThem, amountCents = if (money) Jobs.parseAmount(amount.text.toString()) else null)
        OrbitRepository.editContact(c.id) { o ->
            val rest = o.loans.filter { it.id != loan.id }
            o.copy(loans = if (delete) rest else if (known) o.loans.map { if (it.id == loan.id) next else it } else o.loans + next)
        }
        if (delete) Undo.offer("Gelöscht") { OrbitRepository.editContact(c.id) { o -> if (o.loans.any { it.id == loan.id }) o else o.copy(loans = o.loans + loan) } }
        haptics.perform(Haptic.Confirm)
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Space.PanelHigh) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.L).navigationBarsPadding().padding(bottom = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                SpaceChip("Du → $name", toThem, { toThem = true }, leading = Icons.AutoMirrored.Rounded.CallMade, accent = Space.Teal)
                SpaceChip("$name → dir", !toThem, { toThem = false }, leading = Icons.AutoMirrored.Rounded.CallReceived, accent = Space.Orange)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                SpaceChip("Ding", !money, { money = false })
                SpaceChip("Geld", money, { money = true })
            }
            SpaceField(what, if (money) "Wofür" else "Was", placeholder = if (money) "Pizza" else "Bohrmaschine")
            if (money) {
                SpaceField(amount, "Betrag €", placeholder = "25", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (known) GhostButton("Löschen", { save(true) }, color = Space.Coral)
                Spacer(Modifier.weight(1f))
                GlowButton("Speichern", { save(false) })
            }
        }
    }
}
