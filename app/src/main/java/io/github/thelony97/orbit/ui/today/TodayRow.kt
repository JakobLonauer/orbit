package io.github.thelony97.orbit.ui.today

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.phonebook.PhoneBookFresh
import io.github.thelony97.orbit.phonebook.PhoneBookSync
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.Route
import io.github.thelony97.orbit.ui.components.AppWork
import io.github.thelony97.orbit.ui.components.Ghost
import io.github.thelony97.orbit.ui.theme.Space

/** One card of the Heute row (PLAN 9 C1); in 3.0-A the ghosts of the phone book. */
@Immutable
sealed interface TodayCard {
    val key: String

    /** PLAN 9 A1: „Orbit im Telefonbuch“, offered once as a ghost. */
    data object PhoneBookOffer : TodayCard {
        override val key = OFFER_KEY
    }

    /** PLAN 9 A1 Rückweg: people other apps added since. */
    data class PhoneBookNew(val items: List<PhoneBookFresh.Item>) : TodayCard {
        override val key = "phonebook-new"
    }

    companion object {
        const val OFFER_KEY = "phonebook-offer"
    }
}

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
    return remember(s.phoneBook, s.dismissedGhosts, fresh, s.onboardingDone) {
        buildList {
            if (!s.phoneBook && TodayCard.OFFER_KEY !in s.dismissedGhosts && s.onboardingDone) add(TodayCard.PhoneBookOffer)
            if (fresh.isNotEmpty()) add(TodayCard.PhoneBookNew(fresh))
        }
    }
}

private fun dismiss(key: String) =
    OrbitRepository.editSettings { it.copy(dismissedGhosts = (it.dismissedGhosts + key).distinct().takeLast(400)) }

/**
 * Heute (PLAN 9 C1): a row of cards at the top of the contact list – only what applies today.
 * Every card has the same anatomy: planet (who/what) · one number · one main action.
 */
@Composable
fun TodayRow(cards: List<TodayCard>, nav: Nav, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) turnOnPhoneBook(ctx)
    }
    LazyRow(
        modifier.fillMaxWidth().animateContentSize(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(cards, key = { it.key }) { card ->
            when (card) {
                TodayCard.PhoneBookOffer -> Ghost(
                    source = "Orbits Kontakte als eigenes Konto im Handy",
                    onAccept = {
                        val need = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
                            .filter { ctx.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
                        if (need.isEmpty()) turnOnPhoneBook(ctx) else permissions.launch(need.toTypedArray())
                    },
                    onDismiss = { dismiss(TodayCard.OFFER_KEY) },
                    modifier = Modifier.animateItem().width(236.dp),
                    accent = Space.Teal,
                    dismissAxis = Orientation.Vertical,
                ) {
                    CardBody(Icons.Rounded.Contacts, Space.Teal, "Orbit im Telefonbuch", "WhatsApp · Signal · Gmail")
                }
                is TodayCard.PhoneBookNew -> Ghost(
                    source = "Seit Orbit im Telefonbuch ist, von anderen Apps angelegt",
                    onAccept = {
                        PhoneBookFresh.markSeen(ctx, card.items.maxOf { it.rawId })
                        nav.open(Route.PhoneImportPick(card.items.map { it.contactId }))
                    },
                    onDismiss = { PhoneBookFresh.markSeen(ctx, card.items.maxOf { it.rawId }); dismiss("pb:" + card.items.maxOf { it.rawId }) },
                    modifier = Modifier.animateItem().width(236.dp),
                    accent = Space.Violet,
                    dismissAxis = Orientation.Vertical,
                ) {
                    val n = card.items.size
                    CardBody(
                        Icons.Rounded.PersonAdd, Space.Violet,
                        if (n == 1) "Neu im Telefonbuch" else "$n neu im Telefonbuch",
                        card.items.take(2).joinToString(" · ") { it.name } + if (n > 2) " …" else "",
                    )
                }
            }
        }
    }
}

private fun turnOnPhoneBook(ctx: android.content.Context) {
    val app = ctx.applicationContext
    OrbitRepository.editSettings { it.copy(phoneBook = true) }
    AppWork.launch {
        PhoneBookFresh.start(app)
        PhoneBookSync.request(app)
    }
}

/** Planet · title · one line – the card anatomy of PLAN 9.2. */
@Composable
private fun CardBody(icon: ImageVector, color: Color, title: String, line: String) {
    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(color.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmallEmphasized, color = Space.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(line, style = MaterialTheme.typography.labelMedium, color = Space.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
