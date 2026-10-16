package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.Loan
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Verliehen & Schulden (PLAN 9 D6): open things and money per person, the balance, and the
 * Zahl-QR (EPC/GiroCode) with Jakob's IBAN that a bank app reads.
 */
object Loans {

    fun open(c: Contact): List<Loan> = c.loans.filter { it.settledOn == null }

    /** Money still open: positive = they owe Jakob, negative = Jakob owes them (cents). */
    fun balance(c: Contact): Long = open(c).sumOf { l -> (l.amountCents ?: 0L) * if (l.toThem) 1 else -1 }

    /** „seit 3 W“ (same units as the list: T/W/M/J) – how long it has been out. */
    fun since(l: Loan, today: LocalDate): String {
        val d = Contracts.day(l.since) ?: return ""
        val days = ChronoUnit.DAYS.between(d, today)
        return when {
            days < 1 -> "seit heute"
            days < WEEK -> "seit $days T"
            days < MONTH -> "seit ${days / WEEK} W"
            days < YEAR -> "seit ${days / MONTH} M"
            else -> "seit ${days / YEAR} J"
        }
    }

    /** What a loan is in one phrase: „Bohrmaschine“, „25 € · Pizza“. */
    fun label(l: Loan): String = listOfNotNull(l.amountCents?.let(Jobs::format), l.what.trim().ifBlank { null }).joinToString(" · ").ifBlank { "Etwas" }

    /** The Beleg in the Verlauf when it is settled. */
    fun receipt(l: Loan): String = when {
        l.amountCents != null && l.toThem -> "Bezahlt bekommen: " + label(l)
        l.amountCents != null -> "Bezahlt: " + label(l)
        l.toThem -> "Zurückbekommen: " + label(l)
        else -> "Zurückgegeben: " + label(l)
    }

    /** Jakob's IBAN from a field labelled „IBAN“ on his own card. */
    fun iban(me: Contact?): String? = me?.fields?.firstOrNull { "iban" in it.label.lowercase() && it.value.isNotBlank() }
        ?.value?.filter { it.isLetterOrDigit() }?.uppercase()?.takeIf { it.length in IBAN_LENGTH }

    /**
     * EPC069-12 QR (GiroCode) payload, version 002 (BIC optional): service tag, name, IBAN, amount,
     * remittance text. Null when the amount is out of range.
     */
    fun epc(name: String, iban: String, cents: Long, text: String): String? {
        if (cents !in 1..MAX_CENTS) return null
        val amount = "EUR" + (cents / 100) + "." + "%02d".format(cents % 100)
        return listOf("BCD", "002", "1", "SCT", "", name.take(NAME_CHARS), iban, amount, "", "", text.take(TEXT_CHARS)).joinToString("\n")
    }

    private const val WEEK = 7L
    private const val MONTH = 30L
    private const val YEAR = 365L
    private val IBAN_LENGTH = 15..34
    private const val MAX_CENTS = 99_999_999_999L
    private const val NAME_CHARS = 70
    private const val TEXT_CHARS = 140
}
