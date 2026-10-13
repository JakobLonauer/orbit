package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact

/**
 * vCard 3.0 for sharing a contact (PLAN 3.3) as a file or QR code. Only what belongs on a
 * business card: name, work, numbers, mail, address, links, birthday. Never notes, topics,
 * gift ideas, own fields or Verlauf – those are private.
 */
object VCard {

    fun of(c: Contact): String = buildString {
        fun line(s: String) = append(s).append("\r\n")
        line("BEGIN:VCARD")
        line("VERSION:3.0")
        if (c.isOrg) {
            val name = c.company.ifBlank { c.firstName }
            line("N:${esc(name)};;;;")
            line("FN:${esc(name)}")
            line("ORG:${esc(name)}")
            if (c.position.isNotBlank()) line("TITLE:${esc(c.position)}")
        } else {
            line("N:${esc(c.lastName)};${esc(c.firstName)};;;")
            // FN must not be empty: a person known only by the company is shown under it.
            val fn = listOf(c.firstName, c.lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { c.company }
            line("FN:${esc(fn.ifBlank { "Kontakt" })}")
            if (c.company.isNotBlank()) line("ORG:${esc(c.company)}")
            if (c.position.isNotBlank()) line("TITLE:${esc(c.position)}")
        }
        c.phones.filter { it.value.isNotBlank() }.forEachIndexed { i, p ->
            val type = phoneType(p.label) + if (i == c.defaultPhone) ",PREF" else ""
            line("TEL;TYPE=$type:${p.value.trim()}")
        }
        c.emails.filter { it.value.isNotBlank() }.forEach { e ->
            line("EMAIL;TYPE=${mailType(e.label)}:${e.value.trim()}")
        }
        c.addresses.filter { it.value.isNotBlank() }.forEach { a ->
            // Free-text address: put it into the street part, the rest stays empty.
            line("ADR;TYPE=${if (c.isOrg) "WORK" else "HOME"}:;;${esc(a.value.replace("\n", ", "))};;;;")
        }
        c.links.forEach { l -> line("URL:${l.url.trim()}") }
        c.birthday?.let { b ->
            val md = String.format(java.util.Locale.ROOT, "%02d-%02d", b.month, b.day)
            line("BDAY:" + (b.year?.let { String.format(java.util.Locale.ROOT, "%04d-", it) + md } ?: "--$md"))
        }
        line("END:VCARD")
    }

    /** File name for the shared .vcf, safe on every file system. */
    fun fileName(c: Contact): String {
        val name = if (c.isOrg) c.company else listOf(c.firstName, c.lastName).joinToString(" ").ifBlank { c.company }
        val clean = name.replace(Regex("[^\\p{L}\\p{N} ._-]"), "").trim().replace(Regex("\\s+"), "_")
        return clean.ifBlank { "Kontakt" } + ".vcf"
    }

    /** RFC 2426 escaping for text values. */
    fun esc(s: String): String = s.trim()
        .replace("\\", "\\\\")
        .replace(",", "\\,")
        .replace(";", "\\;")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")

    private fun phoneType(label: String): String {
        val l = label.lowercase()
        return when {
            "mobil" in l || "handy" in l -> "CELL"
            "arbeit" in l || "büro" in l || "dienst" in l || "zentrale" in l -> "WORK"
            "fax" in l -> "FAX"
            "privat" in l || "zuhause" in l || "festnetz" in l -> "HOME"
            else -> "VOICE"
        }
    }

    private fun mailType(label: String): String {
        val l = label.lowercase()
        return if ("arbeit" in l || "dienst" in l || "uni" in l || "schule" in l) "WORK" else "HOME"
    }
}
