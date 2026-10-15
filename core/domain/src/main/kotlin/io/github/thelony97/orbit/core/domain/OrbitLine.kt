package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.BuiltinTypes
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.EntryType
import io.github.thelony97.orbit.core.model.Group
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * Orbit-Zeile (PLAN 9 E1): the search field understands fixed patterns – a person, a kind of
 * entry, a time, „+Notiz“, „#Gruppe“ and the operators `ist:` `hat:` `ort:`. With a person and a
 * kind or time, Enter creates an entry (or a planned meeting); otherwise it stays a search.
 * No guessing beyond these tables.
 */
object OrbitLine {

    enum class Kind { PERSON, TYPE, TIME, NOTE, GROUP, FILTER }

    data class Chip(val kind: Kind, val text: String)

    /** Search operators; empty = no filter. */
    data class Filters(
        val groups: List<String> = emptyList(),
        val org: Boolean? = null,
        val has: Set<String> = emptySet(),
        val place: String? = null,
    ) {
        val isEmpty: Boolean get() = groups.isEmpty() && org == null && has.isEmpty() && place == null
    }

    data class Line(
        val personId: String? = null,
        val typeId: String? = null,
        val at: LocalDateTime? = null,
        val note: String = "",
        val filters: Filters = Filters(),
        /** The text for the plain search (everything but the operators). */
        val rest: String = "",
        val chips: List<Chip> = emptyList(),
        /** A „+Notiz“ was typed. */
        val plusNote: Boolean = false,
    ) {
        /** Enter creates something: a person plus a kind, a time or a „+Notiz“. */
        val capture: Boolean get() = personId != null && (typeId != null || at != null || plusNote)
    }

    private val SYNONYMS = mapOf(
        "anruf" to BuiltinTypes.CALL, "angerufen" to BuiltinTypes.CALL, "telefoniert" to BuiltinTypes.CALL, "telefonat" to BuiltinTypes.CALL,
        "treffen" to BuiltinTypes.MEET, "getroffen" to BuiltinTypes.MEET, "kaffee" to BuiltinTypes.MEET, "essen" to BuiltinTypes.MEET,
        "mittagessen" to BuiltinTypes.MEET, "abendessen" to BuiltinTypes.MEET, "bier" to BuiltinTypes.MEET, "spaziergang" to BuiltinTypes.MEET,
        "video" to BuiltinTypes.VIDEO, "videocall" to BuiltinTypes.VIDEO, "facetime" to BuiltinTypes.VIDEO,
        "nachricht" to BuiltinTypes.MESSAGE, "sms" to BuiltinTypes.MESSAGE, "geschrieben" to BuiltinTypes.MESSAGE, "whatsapp" to BuiltinTypes.MESSAGE,
        "mail" to BuiltinTypes.EMAIL, "email" to BuiltinTypes.EMAIL, "e-mail" to BuiltinTypes.EMAIL,
    )

    /** Words that are a kind and worth keeping as the note („Kaffee“ is more than „Treffen“). */
    private val KEEP_AS_NOTE = setOf("kaffee", "essen", "mittagessen", "abendessen", "bier", "spaziergang")

    private val WEEKDAYS = mapOf(
        "montag" to DayOfWeek.MONDAY, "dienstag" to DayOfWeek.TUESDAY, "mittwoch" to DayOfWeek.WEDNESDAY, "donnerstag" to DayOfWeek.THURSDAY,
        "freitag" to DayOfWeek.FRIDAY, "samstag" to DayOfWeek.SATURDAY, "sonntag" to DayOfWeek.SUNDAY,
    )
    private val SHORT_DAYS = mapOf(
        "mo" to DayOfWeek.MONDAY, "di" to DayOfWeek.TUESDAY, "mi" to DayOfWeek.WEDNESDAY, "do" to DayOfWeek.THURSDAY,
        "fr" to DayOfWeek.FRIDAY, "sa" to DayOfWeek.SATURDAY, "so" to DayOfWeek.SUNDAY,
    )
    private val RELATIVE_DAYS = mapOf("vorgestern" to -2L, "gestern" to -1L, "heute" to 0L, "morgen" to 1L, "ubermorgen" to 2L)
    private val NUMBER_WORDS = mapOf("einer" to 1L, "einem" to 1L, "ein" to 1L, "zwei" to 2L, "drei" to 3L, "vier" to 4L)

    private val CLOCK = Regex("^(\\d{1,2})[:.](\\d{2})$")
    private val HOUR = Regex("^(\\d{1,2})(uhr)?$")
    private val DATE = Regex("^(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})?$")
    private const val NOON = 12

    private class Tok(val raw: String) {
        val f = Fold.fold(raw).trimEnd(',')
        var used = false
    }

    fun parse(text: String, contacts: List<Contact>, types: List<EntryType>, now: LocalDateTime): Line {
        val noteAt = text.indexOf(" +").let { if (it >= 0) it + 1 else if (text.startsWith("+")) 0 else -1 }
        val note = if (noteAt >= 0) text.substring(noteAt + 1).trim() else ""
        val toks = (if (noteAt >= 0) text.substring(0, noteAt) else text).split(' ').filter { it.isNotBlank() }.map(::Tok)
        val chips = ArrayList<Chip>()
        val filters = filtersOf(toks, chips)
        // Without a person nothing is created: then everything but the operators is the search.
        val plain = (toks.filterNot { it.used }.map { it.raw } + listOfNotNull(if (noteAt >= 0) text.substring(noteAt) else null)).joinToString(" ")
        val filterChips = chips.toList()
        val (typeId, typeWord) = typeOf(toks, types, chips)
        val at = timeOf(toks, now, chips)
        val person = personOf(toks, contacts, chips) ?: return Line(filters = filters, rest = plain.trim(), chips = filterChips)
        if (note.isNotBlank()) chips += Chip(Kind.NOTE, note)
        val rest = toks.filterNot { it.used }.joinToString(" ") { it.raw }
        val fullNote = listOfNotNull(typeWord, rest.ifBlank { null }, note.ifBlank { null }).joinToString(" · ")
        return Line(person, typeId, at, fullNote, filters, plain.trim(), chips, plusNote = note.isNotBlank())
    }

    private fun filtersOf(toks: List<Tok>, chips: MutableList<Chip>): Filters {
        var f = Filters()
        for (t in toks) {
            val v = t.f.substringAfter(':')
            val hit = when {
                t.f.startsWith("#") && t.f.length > 1 -> { f = f.copy(groups = f.groups + t.f.drop(1)); true }
                t.f == "ist:org" || t.f == "ist:organisation" -> { f = f.copy(org = true); true }
                t.f == "ist:person" || t.f == "ist:mensch" -> { f = f.copy(org = false); true }
                t.f.startsWith("hat:") && v.isNotBlank() -> { f = f.copy(has = f.has + v); true }
                t.f.startsWith("ort:") && v.isNotBlank() -> { f = f.copy(place = v); true }
                else -> false
            }
            if (hit) {
                t.used = true
                chips += Chip(if (t.f.startsWith("#")) Kind.GROUP else Kind.FILTER, t.raw)
            }
        }
        return f
    }

    /** A kind: an entry type's own name first, then the fixed synonyms. */
    private fun typeOf(toks: List<Tok>, types: List<EntryType>, chips: MutableList<Chip>): Pair<String?, String?> {
        val names = types.filter { it.id != BuiltinTypes.SETTLED }.associateBy { Fold.fold(it.name) }
        for (t in toks) {
            if (t.used) continue
            val id = names[t.f]?.id ?: SYNONYMS[t.f] ?: continue
            t.used = true
            chips += Chip(Kind.TYPE, t.raw)
            return id to (if (t.f in KEEP_AS_NOTE && names[t.f] == null) t.raw.replaceFirstChar { it.uppercase() } else null)
        }
        return null to null
    }

    private fun timeOf(toks: List<Tok>, now: LocalDateTime, chips: MutableList<Chip>): LocalDateTime? {
        val today = now.toLocalDate()
        var day: LocalDate? = null
        var weekday: DayOfWeek? = null
        var time: LocalTime? = null
        val parts = ArrayList<String>()
        var i = 0
        while (i < toks.size) {
            val t = toks[i]
            val next = toks.getOrNull(i + 1)
            if (t.used) { i++; continue }
            val f = t.f.trimEnd('.')
            when {
                f in RELATIVE_DAYS && day == null -> { day = today.plusDays(RELATIVE_DAYS.getValue(f)); take(t, parts) }
                f in WEEKDAYS && weekday == null -> { weekday = WEEKDAYS.getValue(f); take(t, parts) }
                // „Fr“ alone is too often a word; with a time or a dot it is the day.
                f in SHORT_DAYS && weekday == null && (t.f.endsWith('.') || next?.let { isClock(it.f) } == true) -> { weekday = SHORT_DAYS.getValue(f); take(t, parts) }
                f == "in" && next != null && day == null -> {
                    val n = toks.getOrNull(i + 1)?.f?.let { it.toLongOrNull() ?: NUMBER_WORDS[it] }
                    val unit = toks.getOrNull(if (n != null) i + 2 else -1)?.f
                    val d = if (n != null && unit != null) shift(today, n, unit) else null
                    if (d != null) {
                        day = d
                        for (k in i..i + 2) take(toks[k], parts)
                        i += 3
                        continue
                    }
                }
                f == "um" && next != null && time == null && clockOf(next.f) != null -> {
                    time = clockOf(next.f)
                    take(t, parts); take(next, parts)
                    i += 2
                    continue
                }
                time == null && clockOf(t.f) != null && (CLOCK.matches(t.f) || t.f.endsWith("uhr") || next?.f == "uhr") -> {
                    time = clockOf(t.f)
                    take(t, parts)
                    if (next?.f == "uhr") { take(next, parts); i++ }
                }
                day == null && DATE.matches(t.f) -> { day = dateOf(t.f, today); if (day != null) take(t, parts) }
            }
            i++
        }
        if (day == null && weekday == null && time == null) return null
        chips += Chip(Kind.TIME, parts.joinToString(" "))
        val d = day ?: weekday?.let { w ->
            val candidate = today.with(TemporalAdjusters.nextOrSame(w))
            // Today's weekday with a time already past means next week.
            if (candidate == today && time != null && time.isBefore(now.toLocalTime())) candidate.plusWeeks(1) else candidate
        } ?: today
        val t = time ?: if (d == today) now.toLocalTime().withSecond(0).withNano(0) else LocalTime.of(NOON, 0)
        return LocalDateTime.of(d, t)
    }

    private fun take(t: Tok, parts: MutableList<String>) {
        t.used = true
        parts += t.raw
    }

    private fun isClock(f: String) = CLOCK.matches(f) || f.endsWith("uhr") || HOUR.matches(f)

    private fun clockOf(f: String): LocalTime? {
        CLOCK.find(f)?.let { m ->
            val h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
            return if (h in 0..23 && min in 0..59) LocalTime.of(h, min) else null
        }
        HOUR.find(f)?.let { m -> val h = m.groupValues[1].toInt(); return if (h in 0..23) LocalTime.of(h, 0) else null }
        return null
    }

    private fun shift(today: LocalDate, n: Long, unit: String): LocalDate? = when {
        unit.startsWith("tag") -> today.plusDays(n)
        unit.startsWith("woche") -> today.plusWeeks(n)
        unit.startsWith("monat") -> today.plusMonths(n)
        unit.startsWith("jahr") -> today.plusYears(n)
        else -> null
    }

    private fun dateOf(f: String, today: LocalDate): LocalDate? {
        val m = DATE.find(f) ?: return null
        val d = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt()
        val y = m.groupValues[3].toIntOrNull() ?: today.year
        return runCatching { LocalDate.of(y, mo, d) }.getOrNull()
    }

    /** The longest run of words (up to three) that names exactly one contact. */
    private fun personOf(toks: List<Tok>, contacts: List<Contact>, chips: MutableList<Chip>): String? {
        val live = contacts.filter { it.deletedAt == null && !it.isMe }
        for (len in 3 downTo 1) {
            for (start in 0..toks.size - len) {
                val run = toks.subList(start, start + len)
                if (run.any { it.used }) continue
                val q = run.joinToString(" ") { it.f }
                val hits = live.filter { c -> namesOf(c).any { it == q } }
                val one = hits.singleOrNull() ?: hits.filterNot { it.archived }.singleOrNull() ?: continue
                run.forEach { it.used = true }
                chips += Chip(Kind.PERSON, run.joinToString(" ") { it.raw })
                return one.id
            }
        }
        return null
    }

    private fun namesOf(c: Contact): List<String> = listOf(
        listOf(c.firstName, c.lastName).filter { it.isNotBlank() }.joinToString(" "),
        c.firstName, c.nickname, c.company, CallerIndex.nameOf(c),
    ).filter { it.isNotBlank() }.map { Fold.fold(it.trim()) }.distinct()

    /** Does [c] pass the operators? Groups by name, `hat:` geburtstag/thema/vertrag/schulden/notiz, `ort:` in an address. */
    fun accepts(c: Contact, f: Filters, groups: List<Group>): Boolean {
        if (f.isEmpty) return true
        if (f.org != null && c.isOrg != f.org) return false
        if (f.groups.isNotEmpty()) {
            val names = groups.filter { it.id in c.groupIds }.map { Fold.fold(it.name) }
            if (f.groups.any { g -> names.none { it.startsWith(g) } }) return false
        }
        if (f.has.any { !has(c, it) }) return false
        f.place?.let { p -> if (c.addresses.none { Fold.fold(it.value).contains(p) } && c.geo?.query?.let { Fold.fold(it).contains(p) } != true) return false }
        return true
    }

    private fun has(c: Contact, what: String): Boolean = when {
        what.startsWith("geburtstag") -> c.birthday != null
        what.startsWith("thema") || what.startsWith("themen") -> c.topics.any { !it.done }
        what.startsWith("vertrag") -> c.contracts.isNotEmpty()
        what.startsWith("schuld") || what.startsWith("verlieh") -> c.loans.any { it.settledOn == null }
        what.startsWith("notiz") -> c.notes.isNotBlank() || c.pinnedNote.isNotBlank()
        what.startsWith("foto") || what.startsWith("bild") -> c.photo != null
        what.startsWith("nummer") || what.startsWith("telefon") -> c.phones.isNotEmpty()
        what.startsWith("mail") -> c.emails.isNotEmpty()
        what.startsWith("wlan") -> c.wifi.isNotEmpty()
        else -> false
    }
}
