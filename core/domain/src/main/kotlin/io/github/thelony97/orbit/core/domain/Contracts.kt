package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.Contract
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Verträge (PLAN 9 D3): from start, Mindestlaufzeit, Verlängerung and Kündigungsfrist the current
 * term and its „Kündigen bis …“ day – the deadline that still reaches this term's end.
 */
object Contracts {

    /** Days before the deadline the window is orange and Heute shows it. */
    const val LEAD_DAYS = 30L

    /** Lead days on which a notification goes out. */
    val NOTIFY_DAYS = setOf(30L, 7L, 1L, 0L)

    private const val MAX_TERMS = 2_400

    /**
     * The term [today] lies in: [start] … [end] (the day it ends at), [deadline] the last day to
     * cancel for [end]; [days] until then. A cancelled contract keeps the term it was cancelled for
     * and has no deadline; [over] once that has passed.
     */
    data class Term(val start: LocalDate, val end: LocalDate, val deadline: LocalDate?, val days: Long?, val cancelled: Boolean, val over: Boolean) {
        /** How far the term has run, 0…1. */
        fun progress(today: LocalDate): Float {
            val all = ChronoUnit.DAYS.between(start, end).coerceAtLeast(1)
            return (ChronoUnit.DAYS.between(start, today).toFloat() / all).coerceIn(0f, 1f)
        }

        /** Where the deadline sits in the term, 0…1 (null when cancelled). */
        fun deadlineAt(): Float? = deadline?.let {
            val all = ChronoUnit.DAYS.between(start, end).coerceAtLeast(1)
            (ChronoUnit.DAYS.between(start, it).toFloat() / all).coerceIn(0f, 1f)
        }

        /** Where the orange window starts, 0…1. */
        fun windowAt(): Float? = deadline?.let {
            val all = ChronoUnit.DAYS.between(start, end).coerceAtLeast(1)
            (ChronoUnit.DAYS.between(start, it.minusDays(LEAD_DAYS)).toFloat() / all).coerceIn(0f, 1f)
        }

        val inWindow: Boolean get() = days != null && days in 0..LEAD_DAYS
    }

    fun day(iso: String?): LocalDate? = iso?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }

    private fun step(c: Contract): Long = if (c.renewMonths > 0) c.renewMonths.toLong() else 1L

    private fun endOf(c: Contract, start: LocalDate, k: Int): LocalDate {
        val first = start.plusMonths(if (c.minMonths > 0) c.minMonths.toLong() else step(c))
        return first.plusMonths(k * step(c))
    }

    fun deadlineFor(c: Contract, end: LocalDate): LocalDate = end.minusMonths(c.noticeMonths.toLong()).minusDays(c.noticeDays.toLong())

    /** The first term whose deadline is on or after [ref]. */
    private fun termFrom(c: Contract, start: LocalDate, ref: LocalDate): Pair<LocalDate, LocalDate>? {
        for (k in 0 until MAX_TERMS) {
            val end = endOf(c, start, k)
            if (!deadlineFor(c, end).isBefore(ref)) return (if (k == 0) start else endOf(c, start, k - 1)) to end
        }
        return null
    }

    fun term(c: Contract, today: LocalDate): Term? {
        val start = day(c.start) ?: return null
        val cancelled = day(c.cancelledOn)
        if (cancelled != null) {
            val (s, e) = termFrom(c, start, cancelled) ?: return null
            return Term(s, e, null, null, cancelled = true, over = !today.isBefore(e))
        }
        val (s, e) = termFrom(c, start, today) ?: return null
        val deadline = deadlineFor(c, e)
        return Term(s, e, deadline, ChronoUnit.DAYS.between(today, deadline), cancelled = false, over = false)
    }

    /** A deadline that is near: for Heute (days ≤ [LEAD_DAYS]) and the notifications. */
    data class Due(val contactId: String, val contract: Contract, val deadline: LocalDate, val days: Long)

    fun due(contacts: List<Contact>, today: LocalDate, lead: Long = LEAD_DAYS): List<Due> = contacts.flatMap { o ->
        o.contracts.mapNotNull { c ->
            val t = term(c, today) ?: return@mapNotNull null
            val d = t.days ?: return@mapNotNull null
            if (d in 0..lead && t.deadline != null) Due(o.id, c, t.deadline, d) else null
        }
    }.sortedBy { it.days }

    /** „Handy“, else the number, else „Vertrag“. */
    fun title(c: Contract): String = c.name.ifBlank { c.number }.ifBlank { "Vertrag" }

    /** „Kündigen bis 30.11.“ / „heute kündigen“ / „morgen kündigen“. */
    fun deadlineLine(days: Long, deadline: LocalDate): String = when (days) {
        0L -> "heute kündigen"
        1L -> "morgen kündigen"
        else -> "kündigen bis ${deadline.dayOfMonth}.${deadline.monthValue}."
    }

    /** The cancellation mail: subject and body (Jakob only sends it). */
    fun cancelMail(c: Contract, end: LocalDate, myName: String): Pair<String, String> {
        val what = listOf(c.name, c.number.takeIf { it.isNotBlank() }?.let { "Nr. $it" }).filterNot { it.isNullOrBlank() }.joinToString(" ")
        val subject = "Kündigung" + if (what.isNotBlank()) " $what" else ""
        val date = "${end.dayOfMonth}.${end.monthValue}.${end.year}"
        val body = buildString {
            append("Sehr geehrte Damen und Herren,\n\n")
            append("hiermit kündige ich ")
            append(if (what.isNotBlank()) "meinen Vertrag $what" else "meinen Vertrag")
            append(" fristgerecht zum $date, hilfsweise zum nächstmöglichen Termin.\n\n")
            append("Bitte bestätigen Sie mir die Kündigung und das Vertragsende schriftlich.\n\n")
            append("Mit freundlichen Grüßen\n")
            append(myName)
        }
        return subject to body.trimEnd()
    }
}
