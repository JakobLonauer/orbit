package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.OrbitDoc
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Änderungsprotokoll (PLAN 9 E23): per contact which field changed from what to what, kept 30
 * days; each change can be taken back on its own. Fields are compared as JSON, so every field –
 * also future ones – is covered without a list to maintain.
 */
object ChangeLog {

    @Serializable
    data class Change(val id: String, val contactId: String, val field: String, val old: String, val new: String, val at: Long)

    const val KEEP_MS = 30L * 86_400_000L
    const val MAX = 3_000

    private val IGNORE = setOf("updatedAt", "createdAt", "archivedAt", "dead", "defaultPhone")

    private val LABELS = mapOf(
        "firstName" to "Vorname", "lastName" to "Nachname", "nickname" to "Rufname", "title" to "Titel", "salutation" to "Anrede",
        "company" to "Firma", "position" to "Position", "phones" to "Telefon", "emails" to "E-Mail", "addresses" to "Adresse",
        "notes" to "Notizen", "pinnedNote" to "Angeheftet", "howWeMet" to "Kennengelernt", "photo" to "Foto", "groupIds" to "Gruppen",
        "favorite" to "Favorit", "archived" to "Archiviert", "deletedAt" to "Papierkorb", "topics" to "Themen", "gifts" to "Geschenkideen",
        "dates" to "Anlässe", "links" to "Links", "fields" to "Felder", "kind" to "Art", "openingHours" to "Öffnungszeiten",
        "phoneHours" to "Telefonzeiten", "contracts" to "Verträge", "loans" to "Verliehen", "wifi" to "WLAN", "members" to "Mitarbeiter",
        "trade" to "Gewerk", "macros" to "Makros", "ringtone" to "Klingelton", "greeting" to "Du/Sie",
    )

    fun label(field: String): String = LABELS[field] ?: field

    private fun obj(c: Contact): JsonObject = DocJson.json.encodeToJsonElement(Contact.serializer(), c).jsonObject

    fun diff(prev: OrbitDoc?, next: OrbitDoc, at: Long, newId: () -> String): List<Change> {
        if (prev == null) return emptyList()
        val before = prev.contacts.associateBy { it.id }
        return next.contacts.flatMap { c ->
            val o = before[c.id]
            if (o == null || o === c || o.copy(updatedAt = c.updatedAt) == c) return@flatMap emptyList()
            val a = obj(o)
            val b = obj(c)
            (a.keys + b.keys).filter { it !in IGNORE && a[it] != b[it] }.map { k ->
                Change(newId(), c.id, k, (a[k] ?: JsonNull).toString(), (b[k] ?: JsonNull).toString(), at)
            }
        }
    }

    private fun current(c: Contact, field: String): JsonElement = obj(c)[field] ?: JsonNull

    private fun same(a: JsonElement, json: String): Boolean =
        runCatching { a == DocJson.json.parseToJsonElement(json) }.getOrDefault(false)

    /** Only while the field still holds [ch]'s new value – a later change is never overwritten. */
    fun canRevert(c: Contact, ch: Change): Boolean = same(current(c, ch.field), ch.new)

    /** [c] with [ch]'s field back at its old value; unchanged when the field moved on since. */
    fun revert(c: Contact, ch: Change): Contact = put(c, ch.field, from = ch.new, to = ch.old)

    /** Rückgängig of [revert]: the field to [ch]'s new value again, only if it still holds the old one. */
    fun redo(c: Contact, ch: Change): Contact = put(c, ch.field, from = ch.old, to = ch.new)

    private fun put(c: Contact, field: String, from: String, to: String): Contact {
        val o = obj(c).toMutableMap()
        if (!same(o[field] ?: JsonNull, from)) return c
        val value = DocJson.json.parseToJsonElement(to)
        if (value is JsonNull) o.remove(field) else o[field] = value
        return DocJson.json.decodeFromJsonElement(Contact.serializer(), JsonObject(o)).copy(entries = c.entries, gallery = c.gallery)
    }

    fun prune(list: List<Change>, now: Long): List<Change> = list.filter { now - it.at <= KEEP_MS }.takeLast(MAX)

    /** A readable short form of a stored value: text as is, lists by their values, else „–“. */
    fun preview(json: String): String = runCatching { show(DocJson.json.parseToJsonElement(json)) }.getOrDefault(json).ifBlank { "–" }

    private fun show(e: JsonElement): String = when (e) {
        is JsonNull -> ""
        is JsonPrimitive -> when (e.content) {
            "true" -> "ja"
            "false" -> "nein"
            else -> e.content
        }
        is JsonArray -> e.joinToString(", ") { show(it) }
        is JsonObject -> listOf("value", "text", "name", "ssid", "label").firstNotNullOfOrNull { k -> e[k]?.let(::show)?.takeIf { it.isNotBlank() } } ?: "…"
    }
}
