package io.github.thelony97.orbit.core.domain

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Text of the caller notification while it rings (PLAN 3.12, without the dialer role).
 * [publicTitle]/[publicLine] are all the phone's lock screen may show: name and role only;
 * note, topics and customer number appear once the phone is unlocked.
 */
data class CallerNotice(
    val title: String,
    val line: String,
    val details: List<String>,
    val publicTitle: String,
    val publicLine: String,
    /** Upcoming birthday within two weeks, worked out when the call comes (not when the index was written). */
    val occasion: String? = null,
) {
    companion object {
        fun of(card: CallerCard, now: Long, zone: ZoneId): CallerNotice {
            val title = card.name.ifBlank { "Unbekannt" } + if (card.archived) " · archiviert" else ""
            val last = card.lastAt?.let { at ->
                val days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(at).atZone(zone).toLocalDate(), Instant.ofEpochMilli(now).atZone(zone).toLocalDate())
                "zuletzt " + listOf(card.lastKind, AtFormat.ago(days)).filter { it.isNotBlank() }.joinToString(" ")
            }
            val line = listOfNotNull(card.role.takeIf { it.isNotBlank() }, last).joinToString(" · ")
            val occasion = card.birthday?.let { occasion(it, Instant.ofEpochMilli(now).atZone(zone).toLocalDate()) }
            val details = buildList {
                if (card.note.isNotBlank()) add(card.note)
                if (card.topics.isNotEmpty()) add("Ansprechen: " + card.topics.joinToString(" · "))
                if (occasion != null) add(occasion)
                if (card.customerNo.isNotBlank()) add("Kundennummer " + card.customerNo)
            }
            // Lock screen: name and role only – not even "archiviert".
            return CallerNotice(title, line, details, card.name.ifBlank { "Unbekannt" }, card.role, occasion)
        }

        /** „Heute Geburtstag · wird 30“, „Morgen Geburtstag“, „Geburtstag in 5 Tagen · wird 30“; null beyond 14 days. */
        fun occasion(birthday: io.github.thelony97.orbit.core.model.CalendarDay, today: java.time.LocalDate): String? {
            val days = DateMath.daysUntil(birthday, today)
            if (days > 14) return null
            val head = when (days) {
                0L -> "Heute Geburtstag"
                1L -> "Morgen Geburtstag"
                else -> "Geburtstag in $days Tagen"
            }
            return head + (DateMath.turning(birthday, today)?.let { " · wird $it" } ?: "")
        }
    }
}
