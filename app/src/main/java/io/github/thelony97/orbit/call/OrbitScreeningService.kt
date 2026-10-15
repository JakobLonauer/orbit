package io.github.thelony97.orbit.call

import android.content.Context
import android.telecom.Call
import android.telecom.CallScreeningService
import io.github.thelony97.orbit.core.domain.NumberInfo
import io.github.thelony97.orbit.core.domain.Screening
import io.github.thelony97.orbit.domain.Phones
import io.github.thelony97.orbit.store.CallerIndexFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Anruf-Filter (PLAN 9 B8): silences unknown callers by Jakob's fixed rules – never rejects, never
 * hides from the call log. Only the small caller index is read (it carries the rules), so the
 * answer comes in milliseconds; any error lets the call ring normally.
 */
class OrbitScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        val allow = CallResponse.Builder().build()
        val response = runCatching {
            if (details.callDirection != Call.Details.DIRECTION_INCOMING) return@runCatching allow
            val number = details.handle?.schemeSpecificPart.orEmpty()
            val iso = Phones.countryIso(this)
            val index = CallerIndexFile.read(this)
            val rules = index?.screening ?: return@runCatching allow
            val normalize: (String) -> String? = { Phones.normalize(it, iso) }
            val known = number.isNotBlank() && index.lookup(number, normalize) != null
            val info = if (number.isBlank()) null else NumberInfo.of(number, iso)
            val hour = java.time.LocalTime.now().hour
            val rule = Screening.rule(rules, info?.e164 ?: normalize(number), info, known, hour) ?: return@runCatching allow
            Silenced.add(this, number.ifBlank { "Unterdrückt" }, rule)
            CallResponse.Builder()
                .setDisallowCall(false)
                .setSilenceCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()
        }.getOrDefault(allow)
        runCatching { respondToCall(details, response) }
    }
}

/** The calls the filter silenced (PLAN 9 B8): shown in the Verlauf as a thin coral trace with the rule. */
object Silenced {
    @Serializable
    data class Row(val number: String, val at: Long, val rule: String)

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Row.serializer())

    private fun file(ctx: Context) = File(ctx.noBackupFilesDir, "silenced.json")

    @Synchronized
    fun all(ctx: Context): List<Row> = runCatching { json.decodeFromString(serializer, file(ctx).readText()) }.getOrDefault(emptyList())

    @Synchronized
    fun add(ctx: Context, number: String, rule: String) {
        val rows = (all(ctx) + Row(number, System.currentTimeMillis(), rule)).takeLast(300)
        runCatching { file(ctx).writeText(json.encodeToString(serializer, rows)) }
    }
}
