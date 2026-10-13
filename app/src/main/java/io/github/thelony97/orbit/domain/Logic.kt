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

    /**
     * Folded search text per contact, built on first use and kept for this data snapshot: typing
     * in the search no longer folds every note and SMS again on each key (the Index is rebuilt
     * whenever the data changes, so the cache can never be stale).
     */
    private val haystacks = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun haystack(c: Contact, build: (Contact) -> String): String =
        if (contacts[c.id] === c) haystacks.getOrPut(c.id) { build(c) } else build(c)
}

object Logic {

    fun displayName(c: Contact, lastNameFirst: Boolean): String {
        if (c.isOrg) {
            return c.company.trim().ifBlank { listOf(c.firstName, c.lastName).joinToString(" ").trim() }.ifBlank { "Ohne Namen" }
        }
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

    /** Short name for sentences ("Kontakt mit Anna"). */
    fun shortName(c: Contact): String =
        if (c.isOrg) displayName(c, false) else c.firstName.trim().ifBlank { displayName(c, false) }

    fun sortKey(c: Contact, lastNameFirst: Boolean): String {
        if (c.isOrg) return fold(displayName(c, false))
        val primary = if (lastNameFirst) c.lastName.ifBlank { c.firstName } else c.firstName.ifBlank { c.lastName }
        val secondary = if (lastNameFirst) c.firstName else c.lastName
        return fold("$primary $secondary").ifBlank { fold(c.company) }
    }

    fun sectionLetter(c: Contact, lastNameFirst: Boolean): String {
        val ch = sortKey(c, lastNameFirst).firstOrNull()?.uppercaseChar() ?: '#'
        return if (ch.isLetter()) ch.toString() else "#"
    }

    fun initials(c: Contact): String {
        if (c.isOrg) {
            val words = displayName(c, false).split(' ', '-').filter { it.isNotBlank() && it.first().isLetterOrDigit() }
            return words.take(2).joinToString("") { it.take(1) }.uppercase().ifEmpty { "?" }
        }
        val a = c.firstName.trim().firstOrNull()
        val b = c.lastName.trim().firstOrNull()
        val s = listOfNotNull(a, b).joinToString("").uppercase()
        return s.ifEmpty { c.company.take(1).uppercase().ifEmpty { "?" } }
    }

    /** Lower-case, accents removed – "Müller" matches "muller". */
    fun fold(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    fun lastContactAt(c: Contact): Long? = c.entries.maxOfOrNull { it.at }

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

    /** Everyone except "Ich". */
    fun others(data: OrbitData): List<Contact> = data.contacts.filter { it.deletedAt == null && !it.isMe }

    fun me(data: OrbitData): Contact? = data.contacts.firstOrNull { it.isMe }

    fun matches(c: Contact, query: String, idx: Index): Boolean {
        val q = fold(query.trim())
        if (q.isEmpty()) return true
        val digits = query.filter { it.isDigit() }
        if (digits.length >= 3 && c.phones.any { p -> p.value.filter { it.isDigit() }.contains(digits) }) return true
        val folded = idx.haystack(c) { fold(haystackOf(it, idx)) }
        return q.split(' ').filter { it.isNotBlank() }.all { folded.contains(it) }
    }

    private fun haystackOf(c: Contact, idx: Index): String {
        return buildString {
            append(c.firstName).append(' ').append(c.lastName).append(' ')
            append(c.company).append(' ').append(c.position).append(' ').append(c.website).append(' ')
            if (c.isOrg) append(io.github.thelony97.orbit.data.Defaults.orgCategory(c.orgCategory).label).append(' ')
            append(c.howWeMet).append(' ').append(c.notes).append(' ').append(c.pinnedNote).append(' ').append(c.address).append(' ')
            // Own fields (Kundennummer …) and link titles are findable too (PLAN 3.3).
            c.fields.forEach { append(it.label).append(' ').append(it.value).append(' ') }
            c.links.forEach { append(it.title).append(' ') }
            c.emails.forEach { append(it.value).append(' ') }
            c.groupIds.forEach { id -> idx.groups[id]?.let { append(it.name).append(' ') } }
            c.topics.forEach { append(it.text).append(' ') }
            c.gifts.forEach { append(it.text).append(' ') }
            c.anniversaries.forEach { append(it.title).append(' ') }
            // Talks with hidden org members never show up in search (PLAN 3.6).
            c.entries.forEach { e ->
                if (e.memberId != null) return@forEach
                append(e.note).append(' ')
                e.messages.forEach { m -> append(m.text).append(' ') }
            }
        }
    }
}
