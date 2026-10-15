package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.BuiltinTypes
import io.github.thelony97.orbit.core.model.HistoryEntry
import java.time.Instant
import java.time.ZoneId

/**
 * Gewerk & Auftrag (PLAN 9 D5): an „Auftrag“ entry with amount and stars; a craft business is
 * found by its Gewerk and shows its last job in one line – „Therme 05/2025 · 340 € · ★★★★“.
 */
object Jobs {

    /** Common Gewerke offered as chips (Austrian words). */
    val trades = listOf(
        "Installateur", "Elektriker", "Maler", "Tischler", "Fliesenleger", "Dachdecker",
        "Rauchfangkehrer", "Schlosser", "Gärtner", "Werkstatt", "Baumeister", "Reinigung",
    )

    const val MAX_STARS = 5

    /** „340“, „340,50“, „1.240,– €“, „€ 89.90“ → cents; null when there is no number. */
    fun parseAmount(text: String): Long? {
        val t = text.replace("€", "").replace("EUR", "", ignoreCase = true).replace(",–", "").replace(",-", "").trim()
        if (t.none { it.isDigit() }) return null
        val clean = t.filter { it.isDigit() || it == ',' || it == '.' }
        val lastSep = clean.indexOfLast { it == ',' || it == '.' }
        // A separator with exactly 1–2 digits after it is the decimal one; the others group thousands.
        val decimals = if (lastSep >= 0 && clean.length - lastSep - 1 in 1..2) clean.substring(lastSep + 1) else ""
        val whole = (if (decimals.isNotEmpty()) clean.substring(0, lastSep) else clean).filter { it.isDigit() }.ifEmpty { "0" }
        val cents = decimals.padEnd(2, '0')
        return runCatching { whole.toLong() * 100 + (if (decimals.isEmpty()) 0 else cents.toLong()) }.getOrNull()
    }

    /** „340 €“, „340,50 €“, „1.240 €“. */
    fun format(cents: Long): String {
        val euros = cents / 100
        val rest = cents % 100
        val grouped = euros.toString().reversed().chunked(3).joinToString(".").reversed()
        return if (rest == 0L) "$grouped €" else "$grouped,%02d €".format(rest)
    }

    fun stars(n: Int): String = "★".repeat(n.coerceIn(0, MAX_STARS))

    /** The newest „Auftrag“ of a contact (the organisation's own, not a member's). */
    fun latest(entries: List<HistoryEntry>): HistoryEntry? =
        entries.filter { it.typeId == BuiltinTypes.JOB && it.memberId == null }.maxByOrNull { it.at }

    /** „Therme 05/2025 · 340 € · ★★★★“ – what was done (first line of the note), when, cost, stars. */
    fun line(e: HistoryEntry, zone: ZoneId): String {
        val d = Instant.ofEpochMilli(e.at).atZone(zone).toLocalDate()
        val what = e.note.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(WHAT_CHARS)
        val head = listOfNotNull(what, "%02d/%d".format(d.monthValue, d.year)).joinToString(" ")
        return listOfNotNull(head, e.amountCents?.let(::format), e.stars?.takeIf { it > 0 }?.let(::stars)).joinToString(" · ")
    }

    private const val WHAT_CHARS = 28
}
