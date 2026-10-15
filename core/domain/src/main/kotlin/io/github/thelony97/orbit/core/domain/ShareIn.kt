package io.github.thelony97.orbit.core.domain

/**
 * Teilen-Eingang (PLAN 3.8): what another app handed over. A place from Google Maps, a page from
 * the browser, plain text; pictures arrive as files and never pass through here.
 */
object ShareIn {

    sealed interface Item {
        /** Google Maps: name and address as Maps put them in the text, the link leads to the exact place. */
        data class Place(val name: String, val address: String, val mapsUrl: String) : Item
        data class Page(val title: String, val url: String) : Item
        data class Text(val text: String) : Item

        /** PLAN 9 A4: a marked or shared phone number on its own. */
        data class Number(val number: String) : Item

        /** PLAN 9 A4: a marked or shared mail address on its own. */
        data class Email(val address: String) : Item
    }

    private val URL = Regex("https?://\\S+")
    private val PHONE = Regex("^(tel:)?\\+?[0-9 ()/.\\-]{5,26}$")
    private val MAIL = Regex("^(mailto:)?[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

    fun isMaps(url: String): Boolean {
        val u = url.lowercase()
        return "maps.app.goo.gl" in u || "goo.gl/maps" in u || Regex("(^|//|\\.)google\\.[a-z.]+/maps").containsMatchIn(u) || "maps.google." in u
    }

    fun parse(text: String?, subject: String?): Item? {
        val t = text?.trim().orEmpty()
        val s = subject?.trim().orEmpty()
        if (t.isEmpty() && s.isEmpty()) return null
        // A number or an address alone (fixed patterns): it belongs to someone or becomes a contact.
        if (PHONE.matches(t) && t.count(Char::isDigit) in 6..15) return Item.Number(t.removePrefix("tel:").trim())
        if (MAIL.matches(t)) return Item.Email(t.removePrefix("mailto:").trim())
        val url = URL.find(t)?.value?.trimEnd('.', ',', ')')
        if (url != null && isMaps(url)) {
            val lines = t.replace(url, "").lines().map { it.trim() }.filter { it.isNotEmpty() }
            val name = lines.firstOrNull() ?: s
            val address = lines.drop(1).joinToString(", ")
            return Item.Place(name = name.ifBlank { s }, address = address, mapsUrl = url)
        }
        if (url != null) {
            // A shared page: the text is the address (sometimes with the title before it).
            val rest = t.replace(url, "").trim().trim('-', '–', '|', ':').trim()
            val title = s.ifBlank { rest }.ifBlank { url.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/') }
            if (rest.length <= 120) return Item.Page(title = title, url = url)
        }
        return Item.Text(listOf(s, t).filter { it.isNotEmpty() }.distinct().joinToString("\n"))
    }
}
