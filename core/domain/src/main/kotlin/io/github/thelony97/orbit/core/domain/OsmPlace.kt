package io.github.thelony97.orbit.core.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * „Daten ergänzen“ aus OpenStreetMap (PLAN 3.8): what a Nominatim answer offers for an
 * organisation – each value is shown and taken only when Jakob ticks it.
 */
object OsmPlace {

    data class Found(
        val name: String,
        val address: String,
        val website: String?,
        val phone: String?,
        val openingHours: String?,
        val lat: Double?,
        val lon: Double?,
    )

    /** Search text: the organisation's name with the town of its address, if any. */
    fun query(name: String, address: String): String = listOf(name.trim(), address.trim()).filter { it.isNotEmpty() }.joinToString(", ")

    private val json = Json { ignoreUnknownKeys = true }

    /** First hit of a `format=jsonv2&extratags=1&addressdetails=1` answer; null for none or junk. */
    fun parse(body: String): Found? = runCatching {
        val first = (json.parseToJsonElement(body) as? JsonArray)?.firstOrNull()?.jsonObject ?: return null
        fun JsonObject.s(key: String) = this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        val tags = first["extratags"] as? JsonObject
        val addr = first["address"] as? JsonObject
        val street = listOfNotNull(addr?.s("road"), addr?.s("house_number")).joinToString(" ")
        val town = listOfNotNull(addr?.s("postcode"), addr?.s("city") ?: addr?.s("town") ?: addr?.s("village")).joinToString(" ")
        val address = listOf(street, town).filter { it.isNotBlank() }.joinToString(", ")
        Found(
            name = first.s("name") ?: first.s("display_name")?.substringBefore(',').orEmpty(),
            address = address.ifBlank { first.s("display_name").orEmpty() },
            website = tags?.s("website") ?: tags?.s("contact:website") ?: tags?.s("url"),
            phone = (tags?.s("phone") ?: tags?.s("contact:phone"))?.substringBefore(';')?.trim(),
            openingHours = tags?.s("opening_hours"),
            lat = first.s("lat")?.toDoubleOrNull(),
            lon = first.s("lon")?.toDoubleOrNull(),
        )
    }.getOrNull()
}
