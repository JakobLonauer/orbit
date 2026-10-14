package io.github.thelony97.orbit.links

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import io.github.thelony97.orbit.data.OrbitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Logos for portal tiles (PLAN 3.7: „einmal geladen, gespeichert“): the site's touch icon or
 * favicon, fetched once and kept as a PNG next to the photos (so the backup carries it). A site
 * without one keeps its symbol; failures are not retried in the same session.
 */
object Logos {
    private const val MAX_BYTES = 512 * 1024
    private const val SIZE = 96
    private val tried = ConcurrentHashMap.newKeySet<String>()

    fun fileName(linkId: String) = "logo-$linkId.png"

    /** Returns the stored file name, or null if the site gave nothing usable. */
    suspend fun fetch(linkId: String, url: String): String? = withContext(Dispatchers.IO) {
        // Screenshot tests run without the network.
        if (android.os.Build.FINGERPRINT == "robolectric") return@withContext null
        if (!tried.add(linkId)) return@withContext null
        val host = runCatching { URL(if (url.startsWith("http")) url else "https://$url").host }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@withContext null
        val candidates = listOf("apple-touch-icon.png", "apple-touch-icon-precomposed.png", "favicon.ico").map { "https://$host/$it" }
        for (c in candidates) {
            val bmp = runCatching { download(c)?.let(::decode) }.getOrNull() ?: continue
            // Tiny 16-px favicons look worse than the symbol.
            if (bmp.width < 32 || bmp.height < 32) continue
            val name = fileName(linkId)
            val scaled = Bitmap.createScaledBitmap(bmp, SIZE, SIZE, true)
            OrbitRepository.photoFile(name).outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return@withContext name
        }
        null
    }

    private fun download(address: String): ByteArray? {
        val conn = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Orbit (Android; persönliches Adressbuch)")
        }
        return try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) return null
                }
                out.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun decode(bytes: ByteArray): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { d, _, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }.getOrNull()
}
