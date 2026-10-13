package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.OccasionKind
import java.time.LocalDate

/** A reminder that is due on a given day (PLAN 3.5, Planer in PLAN 4). */
data class DueReminder(val upcoming: Upcoming, val daysBefore: Int) {
    /** Lead-time birthday reminders show the person's gift ideas. */
    val withGiftIdeas: Boolean get() = daysBefore > 0 && upcoming.occasion.kind == OccasionKind.BIRTHDAY
}

object ReminderPlan {

    /** Everything to remind about on [today], soonest occasion first. One entry per shared occasion. */
    fun dueOn(contacts: List<Contact>, today: LocalDate, visible: (Contact) -> Boolean): List<DueReminder> =
        UpcomingIndex.build(contacts, today, visible).mapNotNull { u ->
            val d = u.daysUntil.toInt()
            if (d in u.occasion.reminders) DueReminder(u, d) else null
        }

    /** "Heute: Annas 30. Geburtstag · In 7 Tagen: 5. Hochzeitstag Anna & Tom" */
    fun digestLine(items: List<DueReminder>, nameOf: (String) -> String): String =
        items.joinToString(" · ") { "${dayWord(it.daysBefore)}: ${phrase(it.upcoming, nameOf)}" }

    fun dayWord(days: Int): String = when (days) {
        0 -> "Heute"
        1 -> "Morgen"
        else -> "In $days Tagen"
    }

    fun phrase(u: Upcoming, nameOf: (String) -> String): String {
        val owner = nameOf(u.ownerId)
        val names = listOfNotNull(owner, u.withId?.let(nameOf)).joinToString(" & ")
        val n = u.turning?.takeIf { it > 0 }
        return when (u.occasion.kind) {
            OccasionKind.BIRTHDAY -> genitive(owner) + " " + (n?.let { "$it. " } ?: "") + "Geburtstag"
            OccasionKind.CUSTOM -> u.occasion.label.ifBlank { "Anlass" } + " " + names
            else -> (n?.let { "$it. " } ?: "") + label(u.occasion.kind) + " " + names
        }
    }

    fun label(kind: OccasionKind): String = when (kind) {
        OccasionKind.BIRTHDAY -> "Geburtstag"
        OccasionKind.WEDDING -> "Hochzeitstag"
        OccasionKind.ANNIVERSARY -> "Jahrestag"
        OccasionKind.MET -> "Kennenlerntag"
        OccasionKind.NAME_DAY -> "Namenstag"
        OccasionKind.MEMORIAL -> "Gedenktag"
        OccasionKind.CUSTOM -> "Anlass"
    }

    /** German possessive: "Annas", "Max'", "Hans'". */
    fun genitive(name: String): String {
        val n = name.trim()
        if (n.isEmpty()) return n
        return if (n.last().lowercaseChar() in "sßxz") "$n'" else "${n}s"
    }
}
