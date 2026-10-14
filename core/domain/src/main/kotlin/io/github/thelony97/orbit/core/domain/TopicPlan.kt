package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.CheckItem
import io.github.thelony97.orbit.core.model.Contact
import java.time.LocalDate

/**
 * Themen mit Datum (PLAN 3.3/3.13a): open topics whose day has come, for the morning
 * notification. Days the phone missed (off, no alarm) are caught up for up to a week.
 */
object TopicPlan {

    data class Due(val contactId: String, val topic: CheckItem, val day: LocalDate)

    private const val CATCH_UP_DAYS = 7L

    fun dayOf(item: CheckItem): LocalDate? = item.remindOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** [lastRun] is the last day the morning run happened (null = never): its topics were shown then. */
    fun due(contacts: List<Contact>, today: LocalDate, lastRun: LocalDate?, visible: (Contact) -> Boolean): List<Due> {
        val from = maxOf(lastRun?.plusDays(1) ?: today, today.minusDays(CATCH_UP_DAYS))
        return contacts.filter { !it.isMe && visible(it) }.flatMap { c ->
            c.topics.mapNotNull { t ->
                val day = dayOf(t) ?: return@mapNotNull null
                if (t.done || day.isBefore(from) || day.isAfter(today)) null else Due(c.id, t, day)
            }
        }.sortedWith(compareBy({ it.day }, { it.contactId }))
    }
}
