package io.github.thelony97.orbit.ui.today

import androidx.compose.runtime.Immutable
import io.github.thelony97.orbit.phonebook.PhoneBookFresh

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
