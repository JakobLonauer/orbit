package io.github.thelony97.orbit.links

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Echte Bilder (PLAN 9 E16): for a contact without a picture – the Gravatar of their mail address,
 * or for an organisation the big icon of its website – offered as a ghost; only from 192 px on.
 */
object RealPhotos {

    data class Found(val file: String, val source: String)

    private const val MIN_PX = 192
    private const val MAX_BYTES = 2_000_000
    private const val TIMEOUT_MS = 5_000
    /** Contacts already asked in this run (each once) and what was found for them. */
    private val asked = ConcurrentHashMap.newKeySet<String>()
    // A ConcurrentHashMap takes no null values – „nothing found“ is simply no entry (15.10.: the
    // null put crashed every contact page without a photo on the phone; Robolectric skipped the path).
    private val found = ConcurrentHashMap<String, Found>()

    /** Tests turn the network on with a fake [fetch]; Robolectric has none by default. */
    @androidx.annotation.VisibleForTesting
    internal var offline: Boolean = android.os.Build.FINGERPRINT == "robolectric"

    @androidx.annotation.VisibleForTesting
    internal var fetch: (String) -> ByteArray? = ::download

    @androidx.annotation.VisibleForTesting
    internal fun reset() {
        asked.clear()
        found.clear()
    }

    private fun md5(s: String): String = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun candidates(c: Contact): List<Pair<String, String>> {
        val mails = c.emails.map { it.value.trim().lowercase() }.filter { '@' in it }
        val gravatar = mails.map { "https://www.gravatar.com/avatar/${md5(it)}?s=256&d=404" to "Gravatar" }
        if (!c.isOrg) return gravatar
        val hosts = (c.links.mapNotNull { runCatching { URL(if (it.url.startsWith("http")) it.url else "https://${it.url}").host }.getOrNull() } +
            c.emailDomains + mails.map { it.substringAfter('@') }).filter { it.isNotBlank() }.distinct()
        return hosts.map { "https://$it/apple-touch-icon.png" to it } + gravatar
    }

    /** The first picture that is big enough, stored as a candidate file; null when there is none (asked once per run). */
    // NUR ZUR PRÜFUNG: der alte Fehler aus 1.0.282–305 (null in die ConcurrentHashMap).
    private val tried = ConcurrentHashMap<String, Found?>()

    suspend fun find(c: Contact): Found? = withContext(Dispatchers.IO) {
        if (offline || c.photo != null) return@withContext null
        if (tried.containsKey(c.id)) return@withContext tried[c.id]
        val hit = search(c)
        tried[c.id] = hit
        hit
    }

    private fun search(c: Contact): Found? = candidates(c).firstNotNullOfOrNull { (url, source) ->
        val bmp = runCatching { fetch(url)?.let(::decode) }.getOrNull() ?: return@firstNotNullOfOrNull null
        if (minOf(bmp.width, bmp.height) < MIN_PX) return@firstNotNullOfOrNull null
        val name = "cand-${c.id}.jpg"
        val side = minOf(bmp.width, bmp.height)
        val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
        OrbitRepository.photoFile(name).outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        Found(name, source)
    }

    /** Taken: the candidate becomes the profile picture (its own file name). */
    fun take(c: Contact, f: Found) {
        val name = "p-" + io.github.thelony97.orbit.data.newId() + ".jpg"
        runCatching { OrbitRepository.photoFile(f.file).renameTo(OrbitRepository.photoFile(name)) }
        found.remove(c.id)
        OrbitRepository.editContact(c.id) { it.copy(photo = name) }
    }

    private fun download(address: String): ByteArray? {
        val conn = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Orbit (Android; persönliches Adressbuch)")
        }
        return try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { input -> input.readNBytes(MAX_BYTES + 1).takeIf { it.size <= MAX_BYTES } }
        } finally {
            conn.disconnect()
        }
    }

    private fun decode(bytes: ByteArray): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { d, _, _ -> d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
    }.getOrNull() ?: runCatching { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
}
