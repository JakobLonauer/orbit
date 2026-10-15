package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.CheckItem
import io.github.thelony97.orbit.core.model.Contact

/** Geschenke-Board (PLAN 9 E7): Idee → gekauft → verschenkt, with price and a budget per person. */
object GiftBoard {

    enum class Stage(val label: String) { IDEA("Idee"), BOUGHT("Gekauft"), GIVEN("Verschenkt") }

    data class Card(val contact: Contact, val gift: CheckItem)

    fun stageOf(g: CheckItem): Stage = when {
        g.done -> Stage.GIVEN
        g.bought -> Stage.BOUGHT
        else -> Stage.IDEA
    }

    /** The item moved to [to]. */
    fun move(g: CheckItem, to: Stage): CheckItem = when (to) {
        Stage.IDEA -> g.copy(bought = false, done = false)
        Stage.BOUGHT -> g.copy(bought = true, done = false)
        Stage.GIVEN -> g.copy(bought = true, done = true)
    }

    fun columns(contacts: List<Contact>): Map<Stage, List<Card>> {
        val all = contacts.filter { it.deletedAt == null && !it.archived }.flatMap { c -> c.gifts.map { Card(c, it) } }
        return Stage.entries.associateWith { s -> all.filter { stageOf(it.gift) == s }.sortedByDescending { it.gift.createdAt } }
    }

    /** What is spent on [c]: bought and given gifts with a price. */
    fun spent(c: Contact): Long = c.gifts.filter { it.bought || it.done }.sumOf { it.priceCents ?: 0L }

    /** „45 / 60 €“ when a budget is set; orange once it is over. */
    fun budgetLine(c: Contact): Pair<String, Boolean>? {
        val budget = c.giftBudgetCents ?: return null
        val spent = spent(c)
        return "${Jobs.format(spent).removeSuffix(" €")} / ${Jobs.format(budget)}" to (spent > budget)
    }
}
