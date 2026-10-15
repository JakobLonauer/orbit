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

    /** PLAN 9 E10: Neue Gesichter – the Lernkarten, once a week while there are some. */
    data class Faces(val people: List<io.github.thelony97.orbit.data.Contact>, val week: String) : TodayCard {
        override val key = "faces:$week"
    }

    /** PLAN 9 E9: a finished appointment with someone, not yet in the Verlauf. */
    data class CalendarMeet(val item: io.github.thelony97.orbit.phonebook.CalendarGhosts.Item, val contact: io.github.thelony97.orbit.data.Contact) : TodayCard {
        override val key = item.key
    }

    /** PLAN 9 E5: a Doppelstern – two contacts that are one. */
    data class Twin(val twin: io.github.thelony97.orbit.core.domain.Twins.Twin, val a: io.github.thelony97.orbit.data.Contact, val b: io.github.thelony97.orbit.data.Contact) : TodayCard {
        override val key = io.github.thelony97.orbit.core.domain.Twins.key(twin.a, twin.b)
    }

    /** PLAN 9 B5/C1: a Dranbleiben planned for today. */
    data class Persist(val plan: io.github.thelony97.orbit.call.Persist.Plan) : TodayCard {
        override val key = "persist:${plan.id}"
    }

    companion object {
        const val OFFER_KEY = io.github.thelony97.orbit.phonebook.PhoneBookSync.OFFER_KEY
    }
}

/** Title and line of a day's item – the same words in the Heute row and the widget (PLAN 9 C1). */
internal fun planLines(card: TodayCard.Plan, name: String): Pair<String, String?> = when (val item = card.item) {
    is io.github.thelony97.orbit.core.domain.TodayPlan.Meet ->
        io.github.thelony97.orbit.util.Fmt.time(item.start) + " · " + name to item.place.ifBlank { null }
    is io.github.thelony97.orbit.core.domain.TodayPlan.Occasion -> {
        val label = if (item.kind == io.github.thelony97.orbit.core.model.OccasionKind.CUSTOM) item.label.ifBlank { "Anlass" }
        else io.github.thelony97.orbit.core.domain.ReminderPlan.label(item.kind)
        val names = listOfNotNull(name, card.with?.let { io.github.thelony97.orbit.domain.Logic.shortName(it) }).joinToString(" & ")
        if (item.kind == io.github.thelony97.orbit.core.model.OccasionKind.BIRTHDAY) {
            "$name hat Geburtstag" to item.turning?.takeIf { it > 0 }?.let { "wird $it" }
        } else {
            (item.turning?.takeIf { it > 0 }?.let { "$it. " } ?: "") + label to names
        }
    }
    is io.github.thelony97.orbit.core.domain.TodayPlan.Topic -> item.text to name
    is io.github.thelony97.orbit.core.domain.TodayPlan.Deadline ->
        item.title + " · " + io.github.thelony97.orbit.core.domain.Contracts.deadlineLine(item.days, item.deadline) to name
    is io.github.thelony97.orbit.core.domain.TodayPlan.YearsAgo ->
        (if (item.years == 1) "Heute vor einem Jahr" else "Heute vor ${item.years} Jahren") to
            listOf(card.kindName, name, item.note).filter { it.isNotBlank() }.joinToString(" · ")
}
