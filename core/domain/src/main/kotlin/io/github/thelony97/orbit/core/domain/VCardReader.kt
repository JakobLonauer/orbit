package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.CalendarDay
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.ContactKind
import io.github.thelony97.orbit.core.model.LabeledValue
import io.github.thelony97.orbit.core.model.Link
import io.github.thelony97.orbit.core.model.Occasion
import io.github.thelony97.orbit.core.model.OccasionKind
import java.io.ByteArrayOutputStream

/**
 * Reads vCards 2.1, 3.0 and 4.0 (PLAN 9 A2: a scanned QR card or a .vcf file opens Orbit's
 * import). Business-card fields only; quoted-printable (old Android exports) is decoded.
 */
object VCardReader {

    private data class Prop(val name: String, val params: List<String>, val value: String)

    fun read(text: String, newId: () -> String): List<Contact> {
        val out = ArrayList<Contact>()
        var block: MutableList<Prop>? = null
        for (p in props(text)) {
            when {
                p.name == "BEGIN" && p.value.equals("VCARD", ignoreCase = true) -> block = ArrayList()
                p.name == "END" && p.value.equals("VCARD", ignoreCase = true) -> {
                    block?.let { b -> contact(b, newId)?.let(out::add) }
                    block = null
                }
                else -> block?.add(p)
            }
        }
        return out
    }

    /** Logical lines: folded lines joined, quoted-printable soft breaks joined, then split. */
    private fun props(text: String): List<Prop> {
        val raw = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val lines = ArrayList<String>()
        for (line in raw) {
            val last = lines.lastOrNull()
            when {
                // RFC folding: a continuation starts with a space or tab.
                last != null && (line.startsWith(" ") || line.startsWith("\t")) -> lines[lines.lastIndex] = last + line.substring(1)
                // vCard 2.1 quoted-printable: a line ending in '=' continues on the next one.
                last != null && last.endsWith("=") && "QUOTED-PRINTABLE" in last.substringBefore(':').uppercase() ->
                    lines[lines.lastIndex] = last.dropLast(1) + line
                line.isNotBlank() -> lines += line
            }
        }
        return lines.mapNotNull { l ->
            val colon = l.indexOf(':')
            if (colon <= 0) return@mapNotNull null
            val head = l.substring(0, colon).split(';')
            val name = head.first().substringAfterLast('.').uppercase()
            val params = head.drop(1).map { it.uppercase() }
            var value = l.substring(colon + 1)
            if (params.any { "QUOTED-PRINTABLE" in it }) value = quotedPrintable(value)
            Prop(name, params, value)
        }
    }

    private fun quotedPrintable(s: String): String {
        val bytes = ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '=' && i + 2 < s.length) {
                val b = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (b != null) {
                    bytes.write(b)
                    i += 3
                    continue
                }
            }
            val utf8 = c.toString().toByteArray(Charsets.UTF_8)
            bytes.write(utf8, 0, utf8.size)
            i++
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    private fun unescape(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n', 'N' -> append('\n')
                    else -> append(n)
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }

    /** Splits a structured value at unescaped ';'. */
    private fun parts(s: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                cur.append(c).append(s[i + 1])
                i += 2
                continue
            }
            if (c == ';') {
                out += unescape(cur.toString())
                cur.clear()
            } else {
                cur.append(c)
            }
            i++
        }
        out += unescape(cur.toString())
        return out
    }

    private fun types(p: Prop): Set<String> =
        p.params.flatMap { param -> param.removePrefix("TYPE=").split(',') }.map { it.trim('"') }.toSet()

    private fun phoneLabel(p: Prop): String {
        val t = types(p)
        return when {
            "CELL" in t -> "Mobil"
            "FAX" in t -> "Fax"
            "WORK" in t -> "Arbeit"
            "HOME" in t -> "Privat"
            else -> "Sonstige"
        }
    }

    private fun mailLabel(p: Prop): String {
        val t = types(p)
        return when {
            "WORK" in t -> "Arbeit"
            "HOME" in t -> "Privat"
            else -> "Sonstige"
        }
    }

    private fun contact(props: List<Prop>, newId: () -> String): Contact? {
        var first = ""
        var last = ""
        var prefix = ""
        var full = ""
        var company = ""
        var position = ""
        var nickname = ""
        var notes = ""
        var birthday: CalendarDay? = null
        var companyKind = false
        val phones = ArrayList<LabeledValue>()
        val emails = ArrayList<LabeledValue>()
        val addresses = ArrayList<LabeledValue>()
        val links = ArrayList<Link>()
        for (p in props) {
            when (p.name) {
                "N" -> parts(p.value).let { n ->
                    last = n.getOrNull(0).orEmpty().trim()
                    first = listOfNotNull(n.getOrNull(1), n.getOrNull(2)).joinToString(" ").trim()
                    prefix = n.getOrNull(3).orEmpty().trim()
                }
                "FN" -> full = unescape(p.value).trim()
                "ORG" -> company = parts(p.value).filter { it.isNotBlank() }.joinToString(" · ").trim()
                "TITLE" -> position = unescape(p.value).trim()
                "NICKNAME" -> nickname = unescape(p.value).split(',').first().trim()
                "NOTE" -> notes = unescape(p.value).trim()
                "TEL" -> p.value.removePrefix("tel:").trim().takeIf { it.any(Char::isDigit) }?.let { phones += LabeledValue(phoneLabel(p), it) }
                "EMAIL" -> p.value.removePrefix("mailto:").trim().takeIf { '@' in it }?.let { emails += LabeledValue(mailLabel(p), it) }
                "ADR" -> parts(p.value).let { a ->
                    val street = a.getOrNull(2).orEmpty().trim()
                    val city = listOfNotNull(a.getOrNull(5)?.trim()?.takeIf { it.isNotEmpty() }, a.getOrNull(3)?.trim()?.takeIf { it.isNotEmpty() }).joinToString(" ")
                    val country = a.getOrNull(6).orEmpty().trim()
                    val line = listOf(street, city, country).filter { it.isNotEmpty() }.joinToString(", ")
                    if (line.isNotEmpty()) addresses += LabeledValue(if ("WORK" in types(p)) "Arbeit" else "Privat", line)
                }
                "URL" -> unescape(p.value).trim().takeIf { it.isNotEmpty() }?.let { links += Link(newId(), "Webseite", if (it.startsWith("http")) it else "https://$it") }
                "BDAY" -> birthday = day(p.value.trim())
                "X-ABSHOWAS" -> companyKind = p.value.trim().equals("COMPANY", ignoreCase = true)
            }
        }
        if (first.isBlank() && last.isBlank() && full.isNotBlank() && !(company.isNotBlank() && full == company)) {
            val words = full.split(Regex("\\s+"))
            first = words.dropLast(1).joinToString(" ").ifBlank { words.last() }
            last = if (words.size > 1) words.last() else ""
        }
        val isOrg = companyKind || (first.isBlank() && last.isBlank() && company.isNotBlank())
        if (!isOrg && first.isBlank() && last.isBlank() && company.isBlank()) return null
        val c = Contact(
            id = newId(),
            kind = if (isOrg) ContactKind.ORG else ContactKind.PERSON,
            firstName = if (isOrg) "" else first,
            lastName = if (isOrg) "" else last,
            title = if (isOrg) "" else prefix,
            nickname = if (isOrg) "" else nickname,
            company = company.ifBlank { if (isOrg) full else "" },
            position = position,
            notes = notes,
            phones = phones,
            emails = emails,
            addresses = addresses,
            links = links,
        )
        return if (birthday != null && !isOrg) {
            c.copy(dates = listOf(Occasion(newId(), OccasionKind.BIRTHDAY, date = birthday, reminders = Occasion.BIRTHDAY_REMINDERS)))
        } else c
    }

    /** "1990-05-17", "19900517", "--05-17", "--0517", "1990-05-17T00:00:00Z". */
    fun day(raw: String): CalendarDay? {
        val s = raw.substringBefore('T')
        Regex("^(\\d{4})-?(\\d{2})-?(\\d{2})$").find(s)?.let { m ->
            val (y, mo, d) = m.destructured
            return CalendarDay(d.toInt(), mo.toInt(), y.toInt().takeIf { it > 1800 }).takeIf { it.month in 1..12 && it.day in 1..31 }
        }
        Regex("^--(\\d{2})-?(\\d{2})$").find(s)?.let { m ->
            val (mo, d) = m.destructured
            return CalendarDay(d.toInt(), mo.toInt(), null).takeIf { it.month in 1..12 && it.day in 1..31 }
        }
        return null
    }
}
