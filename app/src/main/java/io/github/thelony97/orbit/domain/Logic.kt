package io.github.thelony97.orbit.domain

import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.DayOfYear
import io.github.thelony97.orbit.data.EntryType
import io.github.thelony97.orbit.data.Group
import io.github.thelony97.orbit.data.OrbitData
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

fun zone(): ZoneId = ZoneId.systemDefault()

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone()).toLocalDate()

fun LocalDate.startMillis(): Long = atStartOfDay(zone()).toInstant().toEpochMilli()

fun today(): LocalDate = LocalDate.now(zone())

/** Pre-computed lookups so list screens don't search lists repeatedly. */
class Index(val data: OrbitData) {
    val groups: Map<String, Group> = data.groups.associateBy { it.id }
    val types: Map<String, EntryType> = data.entryTypes.associateBy { it.id }
    val contacts: Map<String, Contact> = data.contacts.associateBy { it.id }
}

enum class DueState { NONE, OK, DUE }

data class DueInfo(
    val rhythm: Int?,
    val dueDate: LocalDate?,
    val state: DueState,
    /** Days since the last timer-resetting contact (or since the contact was created). */
    val daysSinceReset: Long?,
)

object Logic {

    fun displayName(c: Contact, lastNameFirst: Boolean): String {
        val first = c.firstName.trim()
        val last = c.lastName.trim()
        return when {
            first.isEmpty() && last.isEmpty() -> c.company.ifBlank { "Ohne Namen" }
            last.isEmpty() -> first
            first.isEmpty() -> last
            lastNameFirst -> "$last, $first"
            else -> "$first $last"
        }
    }

    fun sortKey(c: Contact, lastNameFirst: Boolean): String {
        val primary = if (lastNameFirst) c.lastName.ifBlank { c.firstName } else c.firstName.ifBlank { c.lastName }
        val secondary = if (lastNameFirst) c.firstName else c.lastName
        return fold("$primary $secondary").ifBlank { fold(c.company) }
    }

    fun sectionLetter(c: Contact, lastNameFirst: Boolean): String {
        val ch = sortKey(c, lastNameFirst).firstOrNull()?.uppercaseChar() ?: '#'
        return if (ch.isLetter()) ch.toString() else "#"
    }

    fun initials(c: Contact): String {
        val a = c.firstName.trim().firstOrNull()
        val b = c.lastName.trim().firstOrNull()
        val s = listOfNotNull(a, b).joinToString("").uppercase()
        return s.ifEmpty { c.company.take(1).uppercase().ifEmpty { "?" } }
    }

    /** Lower-case, accents removed – "Müller" matches "muller". */
    fun fold(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    fun rhythm(c: Contact, idx: Index): Int? =
        c.rhythmDays ?: c.groupIds.mapNotNull { idx.groups[it]?.rhythmDays }.minOrNull()

    fun lastContactAt(c: Contact): Long? = c.entries.maxOfOrNull { it.at }

    fun lastResetAt(c: Contact, idx: Index): Long? =
        c.entries.filter { idx.types[it.typeId]?.resetsTimer ?: true }.maxOfOrNull { it.at }

    fun due(c: Contact, idx: Index, today: LocalDate = today()): DueInfo {
        val r = rhythm(c, idx)
        val base = lastResetAt(c, idx) ?: c.createdAt.takeIf { it > 0 }
        val since = base?.let { ChronoUnit.DAYS.between(it.toLocalDate(), today) }
        if (r == null || c.paused || c.archived || c.deletedAt != null) {
            return DueInfo(r, null, DueState.NONE, since)
        }
        var dueDate = (base?.toLocalDate() ?: today).plusDays(r.toLong())
        c.snoozedUntil?.let { s -> val sd = s.toLocalDate(); if (sd.isAfter(dueDate)) dueDate = sd }
        val state = if (!today.isBefore(dueDate)) DueState.DUE else DueState.OK
        return DueInfo(r, dueDate, state, since)
    }

    fun daysSinceLastContact(c: Contact, today: LocalDate = today()): Long? =
        lastContactAt(c)?.let { ChronoUnit.DAYS.between(it.toLocalDate(), today) }

    fun nextOccurrence(d: DayOfYear, today: LocalDate = today()): LocalDate {
        fun inYear(y: Int): LocalDate {
            val month = d.month.coerceIn(1, 12)
            val maxDay = java.time.YearMonth.of(y, month).lengthOfMonth()
            return LocalDate.of(y, month, d.day.coerceIn(1, maxDay))
        }
        val thisYear = inYear(today.year)
        return if (thisYear.isBefore(today)) inYear(today.year + 1) else thisYear
    }

    fun daysUntil(d: DayOfYear, today: LocalDate = today()): Long =
        ChronoUnit.DAYS.between(today, nextOccurrence(d, today))

    /** Age the person turns at the next occurrence, if the year is known. */
    fun turningAge(d: DayOfYear, today: LocalDate = today()): Int? =
        d.year?.let { nextOccurrence(d, today).year - it }

    fun isToday(d: DayOfYear, today: LocalDate = today()): Boolean = daysUntil(d, today) == 0L

    fun active(data: OrbitData): List<Contact> = data.contacts.filter { it.deletedAt == null }

    fun matches(c: Contact, query: String, idx: Index): Boolean {
        val q = fold(query.trim())
        if (q.isEmpty()) return true
        val digits = query.filter { it.isDigit() }
        if (digits.length >= 3 && c.phones.any { p -> p.value.filter { it.isDigit() }.contains(digits) }) return true
        val haystack = buildString {
            append(c.firstName).append(' ').append(c.lastName).append(' ')
            append(c.company).append(' ').append(c.position).append(' ')
            append(c.howWeMet).append(' ').append(c.notes).append(' ').append(c.address).append(' ')
            c.emails.forEach { append(it.value).append(' ') }
            c.groupIds.forEach { id -> idx.groups[id]?.let { append(it.name).append(' ') } }
            c.topics.forEach { append(it.text).append(' ') }
            c.gifts.forEach { append(it.text).append(' ') }
            c.anniversaries.forEach { append(it.label).append(' ') }
            c.entries.forEach { e ->
                append(e.note).append(' ')
                e.messages.forEach { m -> append(m.text).append(' ') }
            }
        }
        val folded = fold(haystack)
        return q.split(' ').filter { it.isNotBlank() }.all { folded.contains(it) }
    }
}
