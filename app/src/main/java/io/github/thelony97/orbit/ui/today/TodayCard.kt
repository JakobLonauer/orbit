package io.github.thelony97.orbit.ui.today

import androidx.compose.runtime.Immutable
import io.github.thelony97.orbit.phonebook.PhoneBookFresh

/** One card of the Heute row (PLAN 9 C1); in 3.0-A the ghosts of the phone book. */
@Immutable
sealed interface TodayCard {
    val key: String

    /** PLAN 9 A1: „Orbit ins Telefonbuch“, offered once as a ghost; [count] contacts would go in. */
    data class PhoneBookOffer(val count: Int) : TodayCard {
        override val key = OFFER_KEY
    }

    /**
     * PLAN 9 A1 Moment: the offer taken – the same card (same key, so the surface stays) counts
     * the first fill live, ticks when [done] and then goes.
     */
    data class PhoneBookLive(val count: Int, val total: Int, val done: Boolean) : TodayCard {
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
