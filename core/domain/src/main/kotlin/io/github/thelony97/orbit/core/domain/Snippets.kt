package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.Snippet
import java.time.LocalDate

/**
 * Textbausteine (PLAN 9 E6): placeholders filled per contact. An unknown or empty value leaves
 * the text readable (the placeholder goes, with the space in front of it).
 */
object Snippets {

    val PLACEHOLDERS = listOf("Vorname", "Rufname", "Anrede", "Kundennummer", "Vertragsnummer", "Datum")

    private val TOKEN = Regex("\\s?\\{([A-Za-zÄÖÜäöü]+)\\}")

    fun value(key: String, c: Contact, today: LocalDate): String = when (key.lowercase()) {
        "vorname" -> c.firstName.ifBlank { CallerIndex.nameOf(c) }
        "rufname" -> c.nickname.ifBlank { c.firstName }.ifBlank { CallerIndex.nameOf(c) }
        "anrede" -> anrede(c)
        "kundennummer" -> CallerIndex.idField(c)?.value.orEmpty()
        "vertragsnummer" -> c.contracts.firstOrNull { it.number.isNotBlank() && it.cancelledOn == null }?.number.orEmpty()
        "datum" -> "${today.dayOfMonth}.${today.monthValue}.${today.year}"
        "name" -> CallerIndex.nameOf(c)
        else -> ""
    }

    /** „Sehr geehrte Frau Mag.a Huber“ for Sie, „Hallo Hansi“ for Du, „Sehr geehrte Damen und Herren“ for organisations. */
    fun anrede(c: Contact): String = when {
        c.isOrg -> "Sehr geehrte Damen und Herren"
        c.greeting?.formal == true -> {
            val sal = c.salutation.trim()
            val head = when {
                sal.startsWith("Frau", ignoreCase = true) -> "Sehr geehrte"
                sal.startsWith("Herr", ignoreCase = true) -> "Sehr geehrter"
                else -> "Guten Tag"
            }
            listOf(head, sal, c.title, c.lastName.ifBlank { c.firstName }).filter { it.isNotBlank() }.joinToString(" ")
        }
        else -> "Hallo " + c.nickname.ifBlank { c.firstName }.ifBlank { CallerIndex.nameOf(c) }
    }

    fun fill(text: String, c: Contact, today: LocalDate): String = TOKEN.replace(text) { m ->
        val v = value(m.groupValues[1], c, today)
        if (v.isBlank()) "" else m.value.takeWhile { it.isWhitespace() } + v
    }

    /** „/ter“ → the snippets whose name starts with „ter“ (all for a bare „/“). */
    fun matching(typed: String, all: List<Snippet>): List<Snippet> {
        if (!typed.startsWith("/")) return emptyList()
        val q = Fold.fold(typed.drop(1).trim())
        return all.filter { Fold.fold(it.name).startsWith(q) }
    }
}
