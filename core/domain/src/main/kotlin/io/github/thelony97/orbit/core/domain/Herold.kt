package io.github.thelony97.orbit.core.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLDecoder

/**
 * Herold (PLAN 9 B1, Entscheidung 9.9 „Herold per Webseite“): who a number belongs to, read from
 * the public search page with fixed patterns – structured data first (JSON-LD, microdata, a vCard
 * on the page), then the entry around the matching `tel:` link. Only an entry whose number is the
 * one asked for counts; anything unsure is no hit. Pure functions, so they can be tested offline.
 */
object Herold {

    @kotlinx.serialization.Serializable
    data class Hit(
        val name: String,
        val category: String = "",
        val street: String = "",
        val zip: String = "",
        val city: String = "",
        val phone: String = "",
    ) {
        /** „Hauptstraße 5, 4020 Linz“. */
        val address: String get() = listOf(street, listOf(zip, city).filter { it.isNotBlank() }.joinToString(" "))
            .filter { it.isNotBlank() }.joinToString(", ")
    }

    /** The search pages for a number in national form („07322 1234“ → „073221234“): businesses, then people. */
    fun urls(national: String): List<String> {
        val digits = national.filter(Char::isDigit)
        return listOf(
            "https://www.herold.at/gelbe-seiten/suche/?term=$digits",
            "https://www.herold.at/telefonbuch/suche/?term=$digits",
        )
    }

    /** National digits of an Austrian E.164 number („+43732555123“ → „0732555123“); null for other countries. */
    fun national(e164: String): String? {
        val d = e164.filter { it.isDigit() || it == '+' }
        return if (d.startsWith("+43") && d.length > 6) "0" + d.removePrefix("+43") else null
    }

    /** Same number? The last eight digits decide (formats and prefixes differ on the page). */
    fun sameNumber(a: String, b: String): Boolean {
        val x = a.filter(Char::isDigit)
        val y = b.filter(Char::isDigit)
        if (x.length < 6 || y.length < 6) return false
        val n = minOf(8, x.length, y.length)
        return x.takeLast(n) == y.takeLast(n)
    }

    /** The entry of [html] that has [e164]; null when no entry on the page has this number. */
    fun parse(html: String, e164: String): Hit? =
        fromJsonLd(html, e164) ?: fromVCard(html, e164) ?: fromMicrodata(html, e164) ?: fromTelLink(html, e164)

    // ------------------------------------------------------------------ JSON-LD

    private val ldBlock = Regex("""<script[^>]*application/ld\+json[^>]*>(.*?)</script>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun fromJsonLd(html: String, e164: String): Hit? {
        val objects = ArrayList<JsonObject>()
        ldBlock.findAll(html).forEach { m ->
            runCatching { json.parseToJsonElement(m.groupValues[1].trim()) }.getOrNull()?.let { collect(it, objects) }
        }
        return objects.firstNotNullOfOrNull { o ->
            val phone = o.str("telephone")
            if (phone.isBlank() || !sameNumber(phone, e164)) return@firstNotNullOfOrNull null
            val name = o.str("name").ifBlank { return@firstNotNullOfOrNull null }
            val adr = o["address"] as? JsonObject
            Hit(
                name = clean(name),
                category = clean(o.str("description").takeIf { it.length in 2..60 }.orEmpty()),
                street = clean(adr?.str("streetAddress").orEmpty()),
                zip = clean(adr?.str("postalCode").orEmpty()),
                city = clean(adr?.str("addressLocality").orEmpty()),
                phone = phone,
            )
        }
    }

    private fun collect(e: JsonElement, out: MutableList<JsonObject>) {
        when (e) {
            is JsonObject -> {
                if ("name" in e) out += e
                e.values.forEach { collect(it, out) }
            }
            is JsonArray -> e.forEach { collect(it, out) }
            else -> Unit
        }
    }

    private fun JsonObject.str(key: String): String = when (val v = this[key]) {
        is JsonPrimitive -> v.contentOrNull.orEmpty()
        is JsonArray -> (v.firstOrNull() as? JsonPrimitive)?.contentOrNull.orEmpty()
        else -> ""
    }

    // ------------------------------------------------------------------ vCard on the page

    private val vcardText = Regex("""BEGIN(?::|%3A)VCARD.*?END(?::|%3A)VCARD""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    private fun fromVCard(html: String, e164: String): Hit? = vcardText.findAll(html).firstNotNullOfOrNull { m ->
        val raw = m.value.let { v -> if ("%3A" in v.uppercase()) runCatching { URLDecoder.decode(v, "UTF-8") }.getOrDefault(v) else v }
        val text = raw.replace("&#13;", "").replace("&#10;", "\n").replace("<br>", "\n").replace("<br/>", "\n")
        VCardReader.read(text) { "x" }.firstOrNull { c -> c.phones.any { sameNumber(it.value, e164) } }?.let { c ->
            val name = (if (c.company.isNotBlank()) c.company else listOf(c.firstName, c.lastName).joinToString(" ")).trim()
            if (name.isBlank()) return@let null
            val adr = c.addresses.firstOrNull()?.value.orEmpty()
            val (street, zip, city) = splitAddress(adr)
            Hit(clean(name), clean(c.position), street, zip, city, c.phones.first { sameNumber(it.value, e164) }.value)
        }
    }

    // ------------------------------------------------------------------ microdata

    private val itemTag = Regex("""<[a-z0-9]+([^>]*?)itemprop="(name|telephone|streetAddress|postalCode|addressLocality)"([^>]*)>([^<]*)""", RegexOption.IGNORE_CASE)
    private val contentAttr = Regex("""content="([^"]*)"""", RegexOption.IGNORE_CASE)
    private val hrefTel = Regex("""href="tel:([^"]*)"""", RegexOption.IGNORE_CASE)

    private fun fromMicrodata(html: String, e164: String): Hit? {
        // Walk the properties in page order; a new „name“ starts a new entry.
        var current = HashMap<String, String>()
        val entries = ArrayList<Map<String, String>>()
        itemTag.findAll(html).forEach { m ->
            val key = m.groupValues[2].lowercase()
            val attrs = m.groupValues[1] + " " + m.groupValues[3]
            val value = clean(
                contentAttr.find(attrs)?.groupValues?.get(1)
                    ?: (if (key == "telephone") hrefTel.find(attrs)?.groupValues?.get(1) else null)
                    ?: m.groupValues[4],
            )
            if (value.isBlank()) return@forEach
            if (key == "name" && "name" in current) {
                entries += current
                current = HashMap()
            }
            current.putIfAbsent(key, value)
        }
        if (current.isNotEmpty()) entries += current
        val e = entries.firstOrNull { it["telephone"]?.let { t -> sameNumber(t, e164) } == true && !it["name"].isNullOrBlank() } ?: return null
        return Hit(
            name = e.getValue("name"),
            street = e["streetaddress"].orEmpty(),
            zip = e["postalcode"].orEmpty(),
            city = e["addresslocality"].orEmpty(),
            phone = e["telephone"].orEmpty(),
        )
    }

    // ------------------------------------------------------------------ around the tel: link

    private val telLink = Regex("""href="tel:([^"]+)"""", RegexOption.IGNORE_CASE)
    private val heading = Regex("""<h[1-4][^>]*>(.*?)</h[1-4]>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val zipCity = Regex("""(?:^|[\s>,])(\d{4})\s+([A-ZÄÖÜ][\p{L} .\-]{1,40}?)(?=\s*(?:<|,|$|\n))""")
    private val streetBefore = Regex("""([A-ZÄÖÜ][\p{L}.\- ]{2,40}\s\d{1,4}[a-z]?(?:/\d+)*)\s*,?\s*$""")

    private fun fromTelLink(html: String, e164: String): Hit? {
        val m = telLink.findAll(html).firstOrNull { sameNumber(it.groupValues[1], e164) } ?: return null
        val before = html.substring(maxOf(0, m.range.first - 4_000), m.range.first)
        val name = heading.findAll(before).lastOrNull()?.groupValues?.get(1)?.let(::clean)?.takeIf { it.length in 2..80 } ?: return null
        // The address usually sits between the heading and the number.
        val after = before.substring(before.lastIndexOf(name).coerceAtLeast(0))
        val text = clean(after.replace(Regex("<[^>]+>"), "\n"))
        val zc = zipCity.find(text)
        val street = zc?.let { streetBefore.find(text.substring(0, it.range.first))?.groupValues?.get(1)?.trim() }.orEmpty()
        return Hit(name = name, street = street, zip = zc?.groupValues?.get(1).orEmpty(), city = zc?.groupValues?.get(2)?.trim().orEmpty(), phone = m.groupValues[1])
    }

    // ------------------------------------------------------------------ helpers

    /** „Hauptstraße 5, 4020 Linz“ → street, zip, city. */
    internal fun splitAddress(line: String): Triple<String, String, String> {
        val m = Regex("""^(.*?),?\s*(\d{4})\s+([^,]+)""").find(line.trim()) ?: return Triple(line.trim(), "", "")
        return Triple(m.groupValues[1].trim().trimEnd(','), m.groupValues[2], m.groupValues[3].trim())
    }

    private val entities = mapOf("&amp;" to "&", "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'", "&nbsp;" to " ", "&lt;" to "<", "&gt;" to ">")

    /** Tags, comments and entities out, whitespace folded. */
    internal fun clean(s: String): String {
        var t = s.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("<[^>]+>"), " ")
        entities.forEach { (k, v) -> t = t.replace(k, v) }
        t = t.replace(Regex("&#(\\d+);")) { it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: "" }
        return t.replace(Regex("[ \\t\\r\\f]+"), " ").replace(Regex("\\s*\\n\\s*"), "\n").trim()
    }
}
