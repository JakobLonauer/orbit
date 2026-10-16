package io.github.thelony97.orbit.ui.services

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.thelony97.orbit.actions.ContactActions
import io.github.thelony97.orbit.core.designsystem.Corners
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Spacing
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.domain.CallerIndex
import io.github.thelony97.orbit.core.domain.ServiceStatus
import io.github.thelony97.orbit.core.model.AssignmentRoles
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.zone
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.BesideAnchor
import io.github.thelony97.orbit.ui.components.IdCard
import io.github.thelony97.orbit.ui.components.glass
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** One of Jakob's services: the organisation and what it is to him („Hausarzt“). */
@Immutable
data class Service(val org: Contact, val role: String, val color: Color)

/**
 * Meine Dienste (PLAN 9 D1): the organisations ICH is assigned to (Hausarzt, Bank, Werkstatt …),
 * also when organisations are hidden in the lists – archive and trash never.
 */
fun myServices(data: OrbitData): List<Service> {
    val me = data.contacts.firstOrNull { it.isMe } ?: return emptyList()
    val byId = data.contacts.associateBy { it.id }
    val groupColor = data.groups.associate { it.id to it.color }
    return data.orgLinks.filter { it.personId == me.id }.mapNotNull { link ->
        val org = byId[link.orgId]?.takeIf { it.isOrg && it.deletedAt == null && !it.archived } ?: return@mapNotNull null
        val role = AssignmentRoles.of(link).orgSide.ifBlank { link.label }.ifBlank { Logic.shortName(org) }
        val color = org.groupIds.firstNotNullOfOrNull { groupColor[it] }?.let { Color(it) } ?: Space.Blue // organisations are blue everywhere (contactColor)
        Service(org, role, color)
    }.distinctBy { it.org.id }
}

/** The row of tiles under Heute; tap opens, the round button calls, holding offers more. */
@Composable
fun MyServicesRow(services: List<Service>, data: OrbitData, nav: Nav, modifier: Modifier = Modifier) {
    // The status lines follow the clock (a minute is enough).
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        // School holidays only matter for hours with „SH off“ (PLAN 9 D2).
        if (services.any { s -> listOf(s.org.openingHours, s.org.phoneHours).any { "SH" in it } }) {
            io.github.thelony97.orbit.live.SchoolHolidays.ensure(ctx)
            now = System.currentTimeMillis()
        }
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            now = System.currentTimeMillis()
        }
    }
    LazyRow(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.Gutter),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        items(services, key = { it.org.id }) { s ->
            Tile(s, data, nav, now, Modifier.animateItem())
        }
    }
}

@Composable
private fun Tile(s: Service, data: OrbitData, nav: Nav, now: Long, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    var menu by remember { mutableStateOf(false) }
    var hold by remember { mutableStateOf(false) }
    var idCard by remember { mutableStateOf(false) }
    val substitute = s.org.vacationSubstitute?.let { id -> data.contacts.firstOrNull { it.id == id } }?.let { Logic.shortName(it) }
    val status = remember(s.org, now, substitute) {
        ServiceStatus.of(s.org, LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), zone()), substitute, io.github.thelony97.orbit.live.SchoolHolidays.today())
    }
    val number = ContactActions.defaultNumber(s.org)
    val id = CallerIndex.idField(s.org)
    Column(
        modifier
            .width(164.dp)
            .glass(Corners.Card, tint = Space.Panel, border = s.color.copy(alpha = 0.35f))
            .combinedClickable(
                onClick = { nav.open(Route.Detail(s.org.id)) },
                onLongClick = { haptics.perform(Haptic.Tick); menu = true },
            )
            .padding(Spacing.M),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(s.org, s.color, 32.dp)
            Spacer(Modifier.weight(1f))
            if (number != null) {
                Box(
                    Modifier
                        .size(32.dp)
                        .background(Space.Teal.copy(alpha = 0.18f), CircleShape)
                        .clickable {
                            haptics.perform(Haptic.Confirm)
                            ContactActions.call(ctx, s.org, null, data.settings)
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Call, "Anrufen", tint = Space.Teal, modifier = Modifier.size(16.dp)) }
            }
        }
        Spacer(Modifier.size(Spacing.S))
        Text(s.role, style = MaterialTheme.typography.titleSmallEmphasized, color = Space.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(Logic.shortName(s.org), style = MaterialTheme.typography.labelMedium, color = Space.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        status?.let {
            Text(
                it.text,
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = when (it.tone) {
                    ServiceStatus.Tone.OPEN -> Space.Teal
                    ServiceStatus.Tone.AWAY -> Space.Orange
                    ServiceStatus.Tone.CLOSED -> Space.TextFaint
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val actions = buildList {
            if (number != null) add("Dranbleiben" to { hold = true })
            s.org.macros.forEach { m -> add(m.name.ifBlank { "Makro" } to { io.github.thelony97.orbit.ui.detail.dialMacro(ctx, s.org, m, data) }) }
            if (id != null) add(id.label.ifBlank { "Ausweis" } to { idCard = true })
        }
        HoldMenu(menu && actions.isNotEmpty(), s.color, actions, onClose = { menu = false })
    }
    if (hold) io.github.thelony97.orbit.ui.detail.CallHoldSheet(s.org, data, onDismiss = { hold = false }, onSetDefault = { i ->
        io.github.thelony97.orbit.data.OrbitRepository.editContact(s.org.id) { it.copy(defaultPhone = i) }
    })
    if (idCard && id != null) {
        IdCard(Logic.displayName(s.org, false), id.label, id.value, s.color, onDictate = null, onClose = { idCard = false })
    }
}

/** Holding a tile: Dranbleiben, the macros, the card – one tap each. */
@Composable
private fun HoldMenu(open: Boolean, accent: Color, actions: List<Pair<String, () -> Unit>>, onClose: () -> Unit) {
    if (!open) return
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(Unit) {
        delay(5_000)
        close()
    }
    val gap = with(LocalDensity.current) { Spacing.S.roundToPx() }
    Popup(popupPositionProvider = remember(gap) { BesideAnchor(gap) }, onDismissRequest = onClose, properties = PopupProperties(focusable = true)) {
        Column(Modifier.glass(Corners.Row, tint = Space.PanelHighest, border = accent.copy(alpha = 0.5f)).padding(vertical = Spacing.XS)) {
            actions.forEach { (label, run) ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = Space.Text,
                    modifier = Modifier
                        .clickable {
                            onClose()
                            run()
                        }
                        .padding(horizontal = Spacing.M, vertical = Spacing.S),
                )
            }
        }
    }
}
