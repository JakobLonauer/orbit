package io.github.thelony97.orbit.links

import android.content.Context
import io.github.thelony97.orbit.core.domain.OsmPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Nominatim (PLAN 3.8), only on a tap: at most one request per second, Orbit's own User-Agent,
 * no autocomplete, answers cached (30 days) so the same question never goes out twice.
 */
object Osm {
    private const val GAP_MS = 1100L
    private const val KEEP_MS = 30L * 24 * 60 * 60 * 1000
    private val gate = Mutex()
    private var lastAt = 0L

    sealed interface Result {
        data class Hit(val found: OsmPlace.Found) : Result
        data object None : Result
        data object Offline : Result
    }

    suspend fun lookup(ctx: Context, query: String): Result = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 3) return@withContext Result.None
        val cache = File(File(ctx.cacheDir, "osm").apply { mkdirs() }, sha1(q) + ".json")
        val cached = cache.takeIf { it.exists() && System.currentTimeMillis() - it.lastModified() < KEEP_MS }?.readText()
        val body = cached ?: gate.withLock {
            val wait = lastAt + GAP_MS - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            lastAt = System.currentTimeMillis()
            try {
                fetch(q)?.also { cache.writeText(it) }
            } catch (_: java.io.IOException) {
                return@withContext Result.Offline
            }
        } ?: return@withContext Result.Offline
        OsmPlace.parse(body)?.let { Result.Hit(it) } ?: Result.None
    }

    private fun fetch(q: String): String? {
        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&extratags=1&addressdetails=1&accept-language=de&q=" +
            URLEncoder.encode(q, "UTF-8")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "Orbit/1 (Android; persönliches Adressbuch; github.com/JakobLonauer/orbit)")
        }
        return try {
            if (conn.responseCode !in 200..299) null else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun sha1(s: String): String =
        java.security.MessageDigest.getInstance("SHA-1").digest(s.lowercase().toByteArray()).joinToString("") { "%02x".format(it) }
}
