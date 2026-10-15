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

    /** PLAN 9 B3: who waits for a call back (the stack opens on a tap). */
    data class Callbacks(val count: Int, val names: List<String>, val warn: Boolean) : TodayCard {
        override val key = "callbacks"
    }

    /** PLAN 9 A1 Rückweg: people other apps added since. */
    data class PhoneBookNew(val items: List<PhoneBookFresh.Item>) : TodayCard {
        override val key = "phonebook-new"
    }

    /** PLAN 9 C1: a fixed-rule item of the day (meeting, occasion, topic, „Heute vor N Jahren“). */
    data class Plan(
        val item: io.github.thelony97.orbit.core.domain.TodayPlan.Item,
        val contact: io.github.thelony97.orbit.data.Contact,
        val with: io.github.thelony97.orbit.data.Contact? = null,
        /** The entry type's name for „Heute vor N Jahren“ („Treffen“). */
        val kindName: String = "",
        /** First group colour (ARGB) – the same colour as in the list. */
        val color: Long? = null,
    ) : TodayCard {
        override val key = item.key
    }

    /** PLAN 9 B5/C1: a Dranbleiben planned for today. */
    data class Persist(val plan: io.github.thelony97.orbit.call.Persist.Plan) : TodayCard {
        override val key = "persist:${plan.id}"
    }

    companion object {
        const val OFFER_KEY = io.github.thelony97.orbit.phonebook.PhoneBookSync.OFFER_KEY
    }
}
