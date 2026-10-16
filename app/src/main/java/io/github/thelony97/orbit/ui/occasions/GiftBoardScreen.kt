package io.github.thelony97.orbit.ui.occasions

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.thelony97.orbit.core.designsystem.Corners
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Motion
import io.github.thelony97.orbit.core.designsystem.Spacing
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.domain.GiftBoard
import io.github.thelony97.orbit.core.domain.Jobs
import io.github.thelony97.orbit.core.model.BuiltinTypes
import io.github.thelony97.orbit.core.model.HistoryEntry
import io.github.thelony97.orbit.data.Mutations
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.OrbButton
import io.github.thelony97.orbit.ui.components.SpaceBackground
import io.github.thelony97.orbit.ui.components.Undo
import io.github.thelony97.orbit.ui.components.fallbackColor
import io.github.thelony97.orbit.ui.components.glass
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun GiftBoard.Stage.color() = when (this) {
    GiftBoard.Stage.IDEA -> Space.Violet
    GiftBoard.Stage.BOUGHT -> Space.Orange
    // Licht-Code: given is done – quiet, never Teal (= läuft).
    GiftBoard.Stage.GIVEN -> Space.TextDim
}

/** Drop hand-over between columns: gift id → where the new card starts (px, relative to its slot). */
private val arriving = HashMap<String, Offset>()

/** Moves a gift; „Verschenkt“ also writes the Geschenk entry (with its price), Rückgängig takes both back. */
private fun moveGift(card: GiftBoard.Card, to: GiftBoard.Stage) {
    val from = GiftBoard.stageOf(card.gift)
    if (from == to) return
    val c = card.contact
    val giftId = card.gift.id
    val entry = if (to == GiftBoard.Stage.GIVEN) {
        HistoryEntry(newId(), c.id, BuiltinTypes.GIFT, System.currentTimeMillis(), note = card.gift.text, amountCents = card.gift.priceCents)
    } else {
        null
    }
    // Only the stage changes – on the gift as it is now (price or text may have changed since the card was drawn).
    fun stage(d: OrbitData, s: GiftBoard.Stage) =
        d.copy(contacts = d.contacts.map { o -> if (o.id != c.id) o else o.copy(gifts = o.gifts.map { if (it.id == giftId) GiftBoard.move(it, s) else it }) })
    OrbitRepository.edit { d ->
        val x = stage(d, to)
        if (entry != null) Mutations.addEntry(x, c.id, entry) else x
    }
    Undo.offer(to.label) {
        OrbitRepository.edit { d ->
            val x = stage(d, from)
            if (entry != null) Mutations.deleteEntry(x, c.id, entry.id) else x
        }
    }
}

/**
 * Geschenke-Board (PLAN 9 E7): three columns – Idee, Gekauft, Verschenkt. Hold a card and pull it
 * into the next column; each card names the person, the price and their budget.
 */
@Composable
fun GiftBoardScreen(data: OrbitData, nav: Nav, modifier: Modifier = Modifier) {
    val cols = GiftBoard.columns(data.contacts)
    var editing by remember { mutableStateOf<GiftBoard.Card?>(null) }
    // The column of a lifted card is drawn on top, so the card glides over its neighbours.
    var liftedColumn by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    // The column a lifted card would land in – its head lights up.
    var aimColumn by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    SpaceBackground(modifier) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter, vertical = Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                OrbButton(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", { nav.back() })
                Spacer(Modifier.width(Spacing.M))
                Text("Geschenke", style = MaterialTheme.typography.headlineSmallEmphasized, color = Space.Text)
            }
            // The budget once per person: on their first card (board order Idee → Verschenkt).
            val budgetOn = remember(cols) { cols.values.flatten().distinctBy { it.contact.id }.mapTo(HashSet()) { it.gift.id } }
            // 12 + 4 per column = the 16-dp gutter outside, 8 dp between the columns.
            if (cols.values.all { it.isEmpty() }) io.github.thelony97.orbit.ui.components.EmptyHint("Keine Geschenkideen", "")
            else BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = Spacing.Gutter - Spacing.XS)) {
                val colWidth = maxWidth / GiftBoard.Stage.entries.size
                // One scroll for the board: a card pulled across is never cut off by its column.
                Row(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    GiftBoard.Stage.entries.forEachIndexed { i, stage ->
                        Column(Modifier.width(colWidth).zIndex(if (liftedColumn == i) 1f else 0f).padding(horizontal = Spacing.XS), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            val list = cols.getValue(stage)
                            val aimed by androidx.compose.animation.core.animateFloatAsState(
                                if (aimColumn == i && liftedColumn != i) 1f else 0f, Motion.snappy(), label = "aim",
                            )
                            Text(
                                stage.label + if (list.isNotEmpty()) " · ${list.size}" else "",
                                style = MaterialTheme.typography.labelLarge, color = stage.color(),
                                modifier = Modifier.padding(vertical = Spacing.S).graphicsLayer {
                                    val k = 1f + 0.12f * aimed
                                    scaleX = k
                                    scaleY = k
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                                },
                            )
                            list.forEach { card ->
                                key(card.contact.id + card.gift.id) {
                                    GiftCard(
                                        card, stage, i, colWidth.value, card.gift.id in budgetOn, onEdit = { editing = card },
                                        onLift = { up -> liftedColumn = if (up) i else -1; if (!up) aimColumn = -1 }, onAim = { aimColumn = it },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    editing?.let { card -> GiftEditDialog(card, onDismiss = { editing = null }) }
}

/** Price of the gift and the person's yearly budget, as typed („25“, „19,90“). */
@Composable
private fun GiftEditDialog(card: GiftBoard.Card, onDismiss: () -> Unit) {
    val price = androidx.compose.foundation.text.input.rememberTextFieldState(card.gift.priceCents?.let { Jobs.format(it).removeSuffix(" €") }.orEmpty())
    val budget = androidx.compose.foundation.text.input.rememberTextFieldState(card.contact.giftBudgetCents?.let { Jobs.format(it).removeSuffix(" €") }.orEmpty())
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Space.PanelHigh,
        title = { Text(card.gift.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                io.github.thelony97.orbit.ui.components.SpaceField(price, "Preis €")
                io.github.thelony97.orbit.ui.components.SpaceField(budget, "Budget " + Logic.shortName(card.contact) + " €")
            }
        },
        confirmButton = {
            io.github.thelony97.orbit.ui.components.DialogConfirm(onClick = {
                val p = Jobs.parseAmount(price.text.toString())
                val b = Jobs.parseAmount(budget.text.toString())
                OrbitRepository.editContact(card.contact.id) { c -> c.copy(giftBudgetCents = b, gifts = c.gifts.map { if (it.id == card.gift.id) it.copy(priceCents = p) else it }) }
                onDismiss()
            }) { Text("Speichern") }
        },
    )
}

@Composable
private fun GiftCard(
    card: GiftBoard.Card,
    stage: GiftBoard.Stage,
    column: Int,
    colWidthDp: Float,
    showBudget: Boolean,
    onEdit: () -> Unit,
    onLift: (Boolean) -> Unit,
    onAim: (Int) -> Unit = {},
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    // A card that was just dropped here starts where the finger let go and glides into its slot (W2).
    val drag = remember { Animatable(arriving.remove(card.gift.id) ?: Offset.Zero, Offset.VectorConverter) }
    LaunchedEffect(Unit) { if (drag.value != Offset.Zero) drag.animateTo(Offset.Zero, Motion.snappy()) }
    // The gesture outlives recompositions (keyed by id) – it reads the card as it is now.
    val current by androidx.compose.runtime.rememberUpdatedState(card)
    val currentStage by androidx.compose.runtime.rememberUpdatedState(stage)
    val currentColumn by androidx.compose.runtime.rememberUpdatedState(column)
    var lifted by remember { mutableStateOf(false) }
    val lift by androidx.compose.animation.core.animateFloatAsState(if (lifted) 1f else 0f, Motion.snappy(), label = "lift")
    var aim by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val c = card.contact
    Column(
        Modifier
            .fillMaxWidth()
            .zIndex(if (lifted) 1f else 0f)
            .graphicsLayer {
                translationX = drag.value.x
                translationY = drag.value.y
                val s = 1f + 0.06f * lift
                scaleX = s
                scaleY = s
                shadowElevation = 16.dp.toPx() * lift
            }
            .pointerInput(card.gift.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { haptics.perform(Haptic.Tick); lifted = true; aim = currentColumn; onLift(true); onAim(currentColumn) },
                    onDragEnd = {
                        val shift = (drag.value.x / (colWidthDp * density)).roundToInt()
                        val targetIndex = (currentColumn + shift).coerceIn(0, GiftBoard.Stage.entries.lastIndex)
                        val target = GiftBoard.Stage.entries[targetIndex]
                        lifted = false
                        onLift(false)
                        if (target != currentStage) {
                            haptics.perform(Haptic.Confirm)
                            // The card in the new column takes over from here (x only; its slot height differs).
                            arriving[current.gift.id] = Offset(drag.value.x - (targetIndex - currentColumn) * colWidthDp * density, 0f)
                            moveGift(current, target)
                        } else {
                            scope.launch { drag.animateTo(Offset.Zero, Motion.snappy()) }
                        }
                    },
                    onDragCancel = { lifted = false; onLift(false); scope.launch { drag.animateTo(Offset.Zero, Motion.snappy()) } },
                ) { change, amount ->
                    change.consume()
                    val next = drag.value + amount
                    // A tick for every column boundary crossed; the aimed column's head lights.
                    val now = (currentColumn + (next.x / (colWidthDp * density)).roundToInt()).coerceIn(0, GiftBoard.Stage.entries.lastIndex)
                    if (now != aim) {
                        aim = now
                        haptics.perform(Haptic.Tick)
                        onAim(now)
                    }
                    scope.launch { drag.snapTo(next) }
                }
            }
            .glass(Corners.Row, tint = Space.Panel, border = stage.color().copy(alpha = 0.35f))
            .clickable(onClick = onEdit)
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(c, fallbackColor(c.id), 20.dp)
            Spacer(Modifier.width(Spacing.S))
            Text(Logic.shortName(c), style = MaterialTheme.typography.labelMedium, color = Space.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(card.gift.text, style = MaterialTheme.typography.bodyMedium, color = Space.Text, maxLines = 3, overflow = TextOverflow.Ellipsis)
        // Prices are costs: Orange in every column.
        card.gift.priceCents?.let { Text(Jobs.format(it), style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = Space.Orange.copy(alpha = 0.85f)) }
        // Budget = Kosten (Licht-Code Orange): quiet while under, full once over.
        if (showBudget) GiftBoard.budgetLine(c)?.let { (line, over) ->
            Text(line, style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"), color = if (over) Space.Orange else Space.Orange.copy(alpha = 0.6f))
        }
    }
}
