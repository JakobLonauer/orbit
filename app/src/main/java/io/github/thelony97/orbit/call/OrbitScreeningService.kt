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

    /** Off the main thread; any error lets the call ring as usual. */
    override fun onScreenCall(details: Call.Details) {
        val app = applicationContext
        Thread {
            val allow = CallResponse.Builder().build()
            var silenced: Pair<String, String>? = null
            val response = runCatching {
                if (details.callDirection != Call.Details.DIRECTION_INCOMING) return@runCatching allow
                val number = details.handle?.schemeSpecificPart.orEmpty()
                val rule = ruleFor(app, number) ?: return@runCatching allow
                silenced = number.ifBlank { "Unterdrückt" } to rule
                CallResponse.Builder()
                    .setDisallowCall(false)
                    .setSilenceCall(true)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build()
            }.getOrDefault(allow)
            runCatching { respondToCall(details, response) }
            // The trace after the answer – the phone does not wait for the file.
            silenced?.let { (n, r) ->
                runCatching { Silenced.add(app, n, r) }
                io.github.thelony97.orbit.live.AutoCount.bump(app, io.github.thelony97.orbit.live.AutoCount.SCREEN)
            }
        }.start()
    }

    private fun ruleFor(ctx: Context, number: String): String? {
        val iso = Phones.countryIso(ctx)
        val index = CallerIndexFile.read(ctx)
        val rules = index?.screening ?: return null
        val normalize: (String) -> String? = { Phones.normalize(it, iso) }
        val known = number.isNotBlank() && index.lookup(number, normalize) != null
        val info = if (number.isBlank()) null else NumberInfo.of(number, iso)
        val hour = java.time.LocalTime.now().hour
        val rule = Screening.rule(rules, info?.e164 ?: normalize(number), info, known, hour, iso) ?: return null
        // Remembered for a moment: the call screen does not light up for a silenced call.
        Silenced.mark(number)
        return rule
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

    @Volatile private var last: Pair<String, Long>? = null

    /** The filter just silenced [number] (same process, a few seconds). */
    fun mark(number: String) {
        last = number to System.currentTimeMillis()
    }

    fun justNow(number: String): Boolean {
        val l = last ?: return false
        return l.first == number && System.currentTimeMillis() - l.second < 10_000L
    }
}
