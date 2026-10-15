package io.github.thelony97.orbit.ui.today

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.thelony97.orbit.core.designsystem.Corners
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Motion
import io.github.thelony97.orbit.core.designsystem.Spacing
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.domain.PhoneBook
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.phonebook.PhoneBookFresh
import io.github.thelony97.orbit.phonebook.PhoneBookSync
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.components.Ghost
import io.github.thelony97.orbit.ui.components.fallbackColor
import io.github.thelony97.orbit.ui.components.glass
import io.github.thelony97.orbit.ui.theme.Grotesk
import io.github.thelony97.orbit.ui.theme.Space

/** The cards that apply right now; empty = the row is not there at all (W5). */
@Composable
fun rememberTodayCards(data: OrbitData): List<TodayCard> {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val s = data.settings
    val fresh by produceState(emptyList<PhoneBookFresh.Item>(), s.phoneBook, s.dismissedGhosts, tick, data.contacts.size) {
        value = PhoneBookFresh.list(ctx, data)
    }
    val live by PhoneBookSync.live.collectAsState()
    // PLAN 9 B3: the Rückruf-Stapel, read again whenever Orbit comes to the front.
    val callbacks by produceState(emptyList<io.github.thelony97.orbit.call.Callbacks.Item>(), tick, s.dismissedGhosts) {
        value = io.github.thelony97.orbit.call.Callbacks.load(ctx, data)
    }
    val exportable = remember(data.contacts, s.phoneBookOrgs) { data.contacts.count { PhoneBook.exported(it, s.phoneBookOrgs) } }
    return remember(s.phoneBook, s.dismissedGhosts, fresh, s.onboardingDone, live, exportable, callbacks) {
        buildList {
            // Waiting people first – the one thing that is about someone else's time.
            if (callbacks.isNotEmpty()) add(TodayCard.Callbacks(callbacks.size, callbacks.take(2).map { it.title }, callbacks.any { it.warn }))
            val l = live
            // Offer and live share one key: the taken ghost becomes the live card in place.
            val offer = !s.phoneBook && TodayCard.OFFER_KEY !in s.dismissedGhosts
            if (l != null) {
                add(TodayCard.PhoneBookLive(l.count, l.total, l.done))
            } else if (offer && s.onboardingDone && exportable > 0) {
                add(TodayCard.PhoneBookOffer(exportable))
            }
            if (fresh.isNotEmpty()) add(TodayCard.PhoneBookNew(fresh))
        }
    }
}

private fun dismiss(key: String) =
    OrbitRepository.editSettings { it.copy(dismissedGhosts = (it.dismissedGhosts + key).distinct().takeLast(400)) }

/** Cards keep one size where they can; a longer title widens the card up to [CardMaxWidth] (W6). */
private val CardWidth = 236.dp
private val CardMaxWidth = 296.dp

/**
 * Heute (PLAN 9 C1): a row of cards at the top of the contact list – only what applies today.
 * Every card has the same anatomy: planet (who/what) · one number · one main action. Inside the
 * scrolling list a ghost is put away by holding it („Verwerfen“), never by a swipe.
 */
@Composable
fun TodayRow(cards: List<TodayCard>, nav: Nav, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) PhoneBookSync.turnOn(ctx)
    }
    LazyRow(
        modifier.fillMaxWidth().animateContentSize(),
        contentPadding = PaddingValues(horizontal = Spacing.Gutter),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        items(cards, key = { it.key }) { card ->
            Box(Modifier.animateItem().widthIn(min = CardWidth, max = CardMaxWidth), propagateMinConstraints = true) {
                // The offer and its live card are one surface: the content cross-fades, the card stays.
                AnimatedContent(
                    card,
                    transitionSpec = { fadeIn(tween(Motion.MEDIUM_MS)) togetherWith fadeOut(tween(Motion.FAST_MS)) },
                    contentKey = { it is TodayCard.PhoneBookLive },
                    label = "todaySlot",
                ) { c ->
                    when (c) {
                        is TodayCard.PhoneBookLive -> LiveCard(c, onOpen = { nav.open(Route.SettingsArea("contacts")) })
                        is TodayCard.PhoneBookOffer -> Ghost(
                            source = "Orbits Kontakte als eigenes Konto im Handy – mit Namen und Fotos in WhatsApp, Signal und Gmail",
                            onAccept = {
                                val need = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
                                    .filter { ctx.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
                                if (need.isEmpty()) PhoneBookSync.turnOn(ctx) else permissions.launch(need.toTypedArray())
                            },
                            onDismiss = { dismiss(TodayCard.OFFER_KEY) },
                            dismissAxis = null,
                        ) {
                            CardBody(
                                planet = { Planet(Icons.Rounded.Contacts, Space.Violet) },
                                title = "Orbit ins Telefonbuch",
                                line = if (c.count == 1) "1 Kontakt" else "${c.count} Kontakte",
                            )
                        }
                        is TodayCard.PhoneBookNew -> NewInPhoneBook(c, nav)
                        is TodayCard.Callbacks -> CallbacksCard(c, onOpen = { nav.open(Route.Callbacks) })
                    }
                }
            }
        }
    }
}

/** „3 neu im Telefonbuch“: the people themselves as small planets; tap = take over (with a pick). */
@Composable
private fun NewInPhoneBook(card: TodayCard.PhoneBookNew, nav: Nav) {
    val ctx = LocalContext.current
    val n = card.items.size
    val last = card.items.maxOf { it.rawId }
    Ghost(
        source = "Seit Orbit im Telefonbuch ist, von anderen Apps angelegt",
        onAccept = { nav.open(Route.PhoneImportPick(card.items.map { it.contactId }, seenUpTo = last)) },
        onDismiss = {
            PhoneBookFresh.markSeen(ctx, last)
            dismiss("pb:$last")
        },
        dismissAxis = null,
    ) {
        CardBody(
            planet = { People(card.items.take(3).map { it.name to "pb${it.rawId}" }) },
            title = if (n == 1) "Neu im Telefonbuch" else "$n neu im Telefonbuch",
            line = card.items.take(2).joinToString(" · ") { it.name } + if (n > 2) " …" else "",
        )
    }
}

/**
 * The taken offer, live (PLAN 9.2: teal = läuft): the number counts up while Orbit writes, the
 * ring fills; when it is done the planet turns into a tick (Bestätigen) and the card goes.
 */
@Composable
private fun LiveCard(card: TodayCard.PhoneBookLive, onOpen: () -> Unit) {
    val haptics = rememberHaptics()
    val shown by animateIntAsState(card.count, tween(Motion.EMPHASIZED_MS, easing = Motion.Emphasized), label = "liveCount")
    val ring by animateFloatAsState(
        if (card.total <= 0) 0f else card.count.toFloat() / card.total,
        tween(Motion.EMPHASIZED_MS, easing = Motion.Emphasized),
        label = "liveRing",
    )
    androidx.compose.runtime.LaunchedEffect(card.done) { if (card.done) haptics.perform(Haptic.Confirm) }
    Box(Modifier.glass(Corners.Card, tint = Space.Panel, border = Space.Teal.copy(alpha = 0.45f)).clickable(onClick = onOpen)) {
        CardBody(
            planet = {
                Box(
                    Modifier.size(36.dp).drawBehind {
                        val w = 2.dp.toPx()
                        drawArc(Space.Teal.copy(alpha = 0.18f), 0f, 360f, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
                        drawArc(Space.Teal, -90f, 360f * ring, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        card.done,
                        transitionSpec = { (fadeIn(tween(Motion.FAST_MS)) + scaleIn(tween(Motion.MEDIUM_MS, easing = Motion.Emphasized), 0.6f)) togetherWith fadeOut(tween(Motion.FAST_MS)) },
                        label = "liveDone",
                    ) { done ->
                        Icon(if (done) Icons.Rounded.Check else Icons.Rounded.Contacts, null, tint = Space.Teal, modifier = Modifier.size(18.dp))
                    }
                }
            },
            title = "$shown im Telefonbuch",
            line = null,
            numeric = true,
        )
    }
}

/** „3 Rückrufe“: the stack in short; a tap opens it (PLAN 9 B3). */
@Composable
private fun CallbacksCard(card: TodayCard.Callbacks, onOpen: () -> Unit) {
    val accent = if (card.warn) Space.Orange else Space.Violet
    Box(Modifier.glass(Corners.Card, tint = Space.Panel, border = accent.copy(alpha = 0.4f)).clickable(onClick = onOpen)) {
        CardBody(
            planet = { Planet(Icons.Rounded.Call, accent) },
            title = if (card.count == 1) "1 Rückruf" else "${card.count} Rückrufe",
            line = card.names.joinToString(" · ") + if (card.count > 2) " …" else "",
        )
    }
}

/** A round planet with an icon – the „who/what“ of a card. */
@Composable
private fun Planet(icon: ImageVector, color: Color) {
    Box(Modifier.size(36.dp).background(color.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
    }
}

/** Up to three people as overlapping planets, one letter each (the next planet covers the rest). */
@Composable
private fun People(people: List<Pair<String, String>>) {
    val step = 16.dp
    Box(Modifier.size(width = 28.dp + step * (people.size - 1).coerceAtLeast(0), height = 36.dp), contentAlignment = Alignment.CenterStart) {
        people.forEachIndexed { i, (name, id) ->
            val color = fallbackColor(id)
            val words = name.split(' ').filter { it.isNotBlank() }
            // Alone: both initials; in a stack only the first letter shows anyway.
            val initials = if (people.size == 1) {
                listOfNotNull(words.firstOrNull(), words.drop(1).lastOrNull()).joinToString("") { it.take(1) }.uppercase().ifEmpty { "?" }
            } else {
                words.firstOrNull()?.take(1)?.uppercase() ?: "?"
            }
            Box(
                Modifier
                    .offset(x = step * i)
                    .size(28.dp)
                    .background(Space.Panel, CircleShape)
                    .background(color.copy(alpha = 0.35f), CircleShape)
                    .border(1.5.dp, color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(initials, color = Space.Text, fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            }
        }
    }
}

/** Planet · title (the number) · one line – the card anatomy of PLAN 9.2. */
@Composable
private fun CardBody(planet: @Composable () -> Unit, title: String, line: String?, numeric: Boolean = false) {
    Row(Modifier.padding(Spacing.M), verticalAlignment = Alignment.CenterVertically) {
        planet()
        Spacer(Modifier.width(Spacing.M))
        // No weight: the card is as wide as its title needs (within the row's bounds).
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmallEmphasized.let { if (numeric) it.copy(fontFeatureSettings = "tnum") else it },
                color = Space.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (line != null) {
                Text(line, style = MaterialTheme.typography.labelMedium, color = Space.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
