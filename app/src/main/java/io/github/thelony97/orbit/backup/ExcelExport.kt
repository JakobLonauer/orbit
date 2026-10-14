package io.github.thelony97.orbit.backup

import io.github.thelony97.orbit.core.domain.Xlsx
import io.github.thelony97.orbit.core.model.BuiltinTypes
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.domain.zone
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Export Excel (PLAN 3.14): Kontakte and Verlauf as sheets, Mitarbeiter only when there are any.
 * Everything outside the trash, archived contacts marked. Never passwords – Orbit has none here.
 */
object ExcelExport {

    const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    fun fileName(): String = "Orbit-${LocalDate.now(zone())}.xlsx"

    fun write(out: OutputStream, d: OrbitData) = Xlsx.write(out, sheets(d))

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private fun time(at: Long) = stamp.format(Instant.ofEpochMilli(at).atZone(zone()))
    private fun day(at: Long) = Instant.ofEpochMilli(at).atZone(zone()).toLocalDate().toString()

    fun sheets(d: OrbitData): List<Xlsx.Sheet> {
        val contacts = d.contacts.filter { it.deletedAt == null }
            .sortedWith(compareBy<Contact> { !it.isMe }.thenBy { Logic.sortKey(it, d.settings.sortByLastName) })
        val groups = d.groups.associate { it.id to it.name }
        val types = (BuiltinTypes.all + d.entryTypes).associate { it.id to it.name }
        val names = contacts.associate { it.id to Logic.displayName(it, false) }
        fun lines(values: List<io.github.thelony97.orbit.data.LabeledValue>) =
            values.filter { it.value.isNotBlank() }.joinToString("\n") { if (it.label.isBlank()) it.value else "${it.label}: ${it.value}" }

        val people = Xlsx.Sheet(
            "Kontakte",
            listOf("Art", "Vorname", "Nachname", "Firma", "Position", "Gruppen", "Telefon", "E-Mail", "Adresse", "Geburtstag", "Anlässe", "Kennengelernt", "Notiz oben", "Notizen", "Links", "Favorit", "Archiviert", "Angelegt"),
            contacts.map { c ->
                listOf(
                    when { c.isMe -> "Ich"; c.isOrg -> "Organisation"; else -> "Person" },
                    c.firstName, c.lastName, c.company, c.position,
                    c.groupIds.mapNotNull { groups[it] }.joinToString(", "),
                    lines(c.phones), lines(c.emails), lines(c.addresses),
                    c.birthday?.let { b -> "%02d.%02d.".format(b.day, b.month) + (b.year?.toString() ?: "") },
                    c.anniversaries.joinToString("\n") { o -> "${o.title}: %02d.%02d.".format(o.date.day, o.date.month) + (o.date.year?.toString() ?: "") },
                    c.howWeMet, c.pinnedNote, c.notes,
                    c.links.joinToString("\n") { l -> if (l.title.isBlank()) l.url else "${l.title}: ${l.url}" },
                    if (c.favorite) "ja" else null,
                    if (c.archived) "ja" else null,
                    c.createdAt.takeIf { it > 0 }?.let(::day),
                )
            },
            widths = listOf(12, 14, 16, 20, 18, 16, 26, 28, 30, 12, 24, 20, 24, 40, 30, 8, 10, 12),
        )

        val entries = contacts.flatMap { c -> c.entries.map { c to it } }.sortedByDescending { it.second.at }
        val history = Xlsx.Sheet(
            "Verlauf",
            listOf("Zeit", "Kontakt", "Mitarbeiter", "Art", "Richtung", "Dauer (Min)", "Notiz", "Nachrichten"),
            entries.map { (c, e) ->
                listOf(
                    time(e.at),
                    names[c.id],
                    e.memberId?.let { m -> c.members.firstOrNull { it.id == m }?.name },
                    types[e.typeId] ?: e.typeId,
                    when (e.incoming) { true -> "eingehend"; false -> "ausgehend"; null -> null },
                    e.durationSec?.takeIf { it > 0 }?.let { Math.round(it / 6.0) / 10.0 },
                    e.note,
                    buildList {
                        e.messages.sortedBy { it.at }.forEach { m -> add((if (m.incoming) "← " else "→ ") + m.text) }
                        e.mails.sortedBy { it.at }.forEach { m -> add((if (m.incoming) "← " else "→ ") + m.subject) }
                    }.joinToString("\n"),
                )
            },
            widths = listOf(17, 22, 16, 12, 11, 11, 50, 50),
        )

        val members = contacts.filter { it.isOrg }.flatMap { o -> o.members.map { o to it } }
        val staff = Xlsx.Sheet(
            "Mitarbeiter",
            listOf("Organisation", "Name", "Rolle", "Telefon", "E-Mail", "Notiz"),
            members.map { (o, m) -> listOf(names[o.id], m.name, m.role, lines(m.phones), lines(m.emails), m.note) },
            widths = listOf(24, 20, 18, 26, 28, 40),
        )
        return listOfNotNull(people, history, staff.takeIf { members.isNotEmpty() })
    }
}
