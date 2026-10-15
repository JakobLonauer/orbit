package io.github.thelony97.orbit.call

import android.content.Context
import android.util.Log
import io.github.thelony97.orbit.core.domain.Herold
import io.github.thelony97.orbit.data.OrbitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Anrufer materialisiert (PLAN 9 B1), online part: an unknown Austrian number is looked up on the
 * Herold website – at most once per number and week (misses count too), switchable off in the
 * settings. The answer is kept on the phone (noBackup, a cache, not data). Network errors are not
 * remembered, so the next call asks again.
 */
object CallerLookup {

    private const val TAG = "CallerLookup"
    private const val WEEK = 7L * 24 * 3_600_000L
    private const val MAX_ENTRIES = 600
    private const val MAX_BYTES = 1_500_000

    @Serializable
    data class Entry(val at: Long, val hit: Herold.Hit? = null)

    private val mutex = Mutex()
    private var memory: MutableMap<String, Entry>? = null
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), Entry.serializer())

    private fun file(ctx: Context) = File(ctx.noBackupFilesDir, "caller-lookup.json")

    private suspend fun entries(ctx: Context): MutableMap<String, Entry> = mutex.withLock {
        memory ?: withContext(Dispatchers.IO) {
            runCatching { json.decodeFromString(serializer, file(ctx).readText()).toMutableMap() }.getOrDefault(HashMap())
        }.also { memory = it }
    }

    /** A stored answer from the last week (a hit, or null for „Herold knows nobody“); null when not asked yet. */
    suspend fun known(ctx: Context, e164: String): Entry? =
        entries(ctx)[e164]?.takeIf { System.currentTimeMillis() - it.at < WEEK }

    /** Whether Jakob lets Orbit ask (Einstellungen › Telefon). */
    suspend fun enabled(): Boolean = runCatching { OrbitRepository.snapshot().settings.callerLookup }.getOrDefault(true)

    /**
     * Who is [e164]? From the week's cache, else from Herold (businesses first, then people). The
     * answer's hit is null when Herold knows nobody; the answer itself is null when Orbit did not
     * ask (not Austrian, switched off) or Herold was not reached (then nothing is remembered).
     */
    suspend fun lookup(ctx: Context, e164: String): Entry? {
        known(ctx, e164)?.let { return it }
        if (!enabled()) return null
        val national = Herold.national(e164) ?: return null
        var reached = false
        val hit = withContext(Dispatchers.IO) {
            Herold.urls(national).firstNotNullOfOrNull { url ->
                fetch(url)?.let { html ->
                    reached = true
                    runCatching { Herold.parse(html, e164) }.getOrNull()
                }
            }
        }
        if (!reached) return null
        return Entry(System.currentTimeMillis(), hit).also { store(ctx, e164, it) }
    }

    private suspend fun store(ctx: Context, e164: String, entry: Entry) {
        val map = entries(ctx)
        mutex.withLock {
            map[e164] = entry
            // Oldest out first – a cache, not an archive.
            if (map.size > MAX_ENTRIES) map.entries.sortedBy { it.value.at }.take(map.size - MAX_ENTRIES).forEach { map.remove(it.key) }
            val text = json.encodeToString(serializer, map)
            withContext(Dispatchers.IO) {
                runCatching {
                    val tmp = File(file(ctx).path + ".tmp")
                    tmp.writeText(text)
                    tmp.renameTo(file(ctx))
                }.onFailure { Log.w(TAG, "store", it) }
            }
        }
    }

    /** One page, like a browser would ask for it; null on any error. */
    private fun fetch(url: String): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 4_000
        c.readTimeout = 8_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36")
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml")
        c.setRequestProperty("Accept-Language", "de-AT,de;q=0.9")
        try {
            if (c.responseCode !in 200..299) return@runCatching null
            c.inputStream.use { input ->
                val bytes = input.readNBytes(MAX_BYTES)
                String(bytes, Charsets.UTF_8)
            }
        } finally {
            c.disconnect()
        }
    }.onFailure { Log.i(TAG, "fetch failed: ${it.javaClass.simpleName}") }.getOrNull()
}
