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
) {
    companion object {
        fun of(card: CallerCard, now: Long, zone: ZoneId): CallerNotice {
            val title = card.name.ifBlank { "Unbekannt" } + if (card.archived) " · archiviert" else ""
            val last = card.lastAt?.let { at ->
                val days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(at).atZone(zone).toLocalDate(), Instant.ofEpochMilli(now).atZone(zone).toLocalDate())
                "zuletzt " + listOf(card.lastKind, AtFormat.ago(days)).filter { it.isNotBlank() }.joinToString(" ")
            }
            val line = listOfNotNull(card.role.takeIf { it.isNotBlank() }, last).joinToString(" · ")
            val details = buildList {
                if (card.note.isNotBlank()) add(card.note)
                if (card.topics.isNotEmpty()) add("Ansprechen: " + card.topics.joinToString(" · "))
                if (card.customerNo.isNotBlank()) add("Kundennummer " + card.customerNo)
            }
            return CallerNotice(title, line, details, title, card.role)
        }
    }
}
