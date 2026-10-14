package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.Greeting

/**
 * Gratulations-Vorlage pro Kontakt (PLAN 3.5): the text „Gratulieren“ fills into WhatsApp or the
 * SMS. Du greets by first name, Sie by full name; an unknown language falls back to German.
 */
object Greetings {

    /** Code to the language's own name, in the order the chips show them. */
    val LANGUAGES = listOf(
        "de" to "Deutsch",
        "en" to "English",
        "it" to "Italiano",
        "fr" to "Français",
        "es" to "Español",
        "hr" to "Hrvatski",
        "tr" to "Türkçe",
    )

    private val TEXTS: Map<String, Pair<(String) -> String, (String) -> String>> = mapOf(
        "de" to ({ n: String -> "Alles Gute zum Geburtstag, $n!" } to { n: String -> "Herzlichen Glückwunsch zum Geburtstag, $n! Alles Gute für Sie." }),
        "en" to ({ n: String -> "Happy birthday, $n!" } to { n: String -> "Happy birthday, $n! All the best." }),
        "it" to ({ n: String -> "Buon compleanno, $n!" } to { n: String -> "Tanti auguri di buon compleanno, $n!" }),
        "fr" to ({ n: String -> "Joyeux anniversaire, $n !" } to { n: String -> "Joyeux anniversaire, $n ! Tous mes vœux." }),
        "es" to ({ n: String -> "¡Feliz cumpleaños, $n!" } to { n: String -> "¡Feliz cumpleaños, $n! Le deseo lo mejor." }),
        "hr" to ({ n: String -> "Sretan rođendan, $n!" } to { n: String -> "Sretan Vam rođendan, $n!" }),
        "tr" to ({ n: String -> "İyi ki doğdun, $n!" } to { n: String -> "Doğum gününüz kutlu olsun, $n!" }),
    )

    fun languageName(code: String): String = LANGUAGES.firstOrNull { it.first == code }?.second ?: LANGUAGES.first().second

    /** The next language for the cycling chip. */
    fun nextLanguage(code: String): String {
        val i = LANGUAGES.indexOfFirst { it.first == code }
        return LANGUAGES[(i + 1).mod(LANGUAGES.size)].first
    }

    fun text(c: Contact, g: Greeting? = c.greeting): String {
        val greeting = g ?: Greeting()
        val first = c.firstName.trim()
        val full = listOf(c.firstName.trim(), c.lastName.trim()).filter { it.isNotEmpty() }.joinToString(" ")
        val fallback = c.company.trim()
        val name = (if (greeting.formal) full else first).ifEmpty { full.ifEmpty { fallback } }
        val (du, sie) = TEXTS[greeting.language] ?: TEXTS.getValue("de")
        val text = if (greeting.formal) sie(name) else du(name)
        // No name at all: „Alles Gute zum Geburtstag!“ instead of a dangling comma.
        return if (name.isEmpty()) text.replace(Regex(",\\s*(?=[! ])"), "") else text
    }
}
