package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Attachment
import io.github.thelony97.orbit.core.model.HistoryEntry
import io.github.thelony97.orbit.core.model.OrbitDoc
import kotlinx.serialization.json.Json

/** The one JSON configuration for stored data (same flags as 1.0.7). */
object DocJson {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun encode(doc: OrbitDoc): String = json.encodeToString(OrbitDoc.serializer(), doc)
    fun decode(text: String): OrbitDoc = json.decodeFromString(OrbitDoc.serializer(), text)
    fun encode(e: HistoryEntry): String = json.encodeToString(HistoryEntry.serializer(), e)
    fun decodeEntry(text: String): HistoryEntry = json.decodeFromString(HistoryEntry.serializer(), text)
    fun encode(a: Attachment): String = json.encodeToString(Attachment.serializer(), a)
    fun decodeAttachment(text: String): Attachment = json.decodeFromString(Attachment.serializer(), text)
}
