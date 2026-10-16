package io.github.thelony97.orbit.links

import android.graphics.Bitmap
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.ContactKind
import io.github.thelony97.orbit.data.LabeledValue
import io.github.thelony97.orbit.data.OrbitRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/**
 * Echte Bilder (PLAN 9 E16) with the network faked. 15.10.: „nothing found“ was stored as null in
 * a ConcurrentHashMap – every contact page without a photo crashed on the phone, while Robolectric
 * never took the path (no network in tests).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RealPhotosTest {

    private val anna = Contact("anna", firstName = "Anna", emails = listOf(LabeledValue("", "Anna@Example.at ")))
    private val shop = Contact("shop", kind = ContactKind.ORG, company = "Radhaus", emailDomains = listOf("radhaus.at"))
    private lateinit var realFetch: (String) -> ByteArray?

    @Before fun init() {
        val ctx = RuntimeEnvironment.getApplication()
        OrbitRepository.init(ctx)
        OrbitRepository.resetForTest(ctx)
        realFetch = RealPhotos.fetch
        RealPhotos.reset()
        RealPhotos.offline = false
    }

    @After fun done() {
        RealPhotos.offline = true
        RealPhotos.fetch = realFetch
        RealPhotos.reset()
    }

    private fun png(side: Int): ByteArray {
        val bmp = Bitmap.createBitmap(side, side + side / 4, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.rgb(120, 80, 200))
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    @Test fun nothingFoundIsNoCrash() = runBlocking {
        RealPhotos.fetch = { null }
        assertNull(RealPhotos.find(anna))
        // Asked once per run: the second visit is answered from memory, again without a crash.
        assertNull(RealPhotos.find(anna))
    }

    @Test fun brokenNetworkIsNoCrash() = runBlocking {
        RealPhotos.fetch = { throw java.io.IOException("offline") }
        assertNull(RealPhotos.find(anna))
        RealPhotos.fetch = { throw IllegalStateException("kaputt") }
        assertNull(RealPhotos.find(shop))
    }

    @Test fun brokenPictureIsNoCrash() = runBlocking {
        RealPhotos.fetch = { "<html>404</html>".toByteArray() }
        assertNull(RealPhotos.find(anna))
    }

    @Test fun bigPictureIsOfferedOnce() = runBlocking {
        val asked = mutableListOf<String>()
        RealPhotos.fetch = { url -> asked += url; png(256) }
        val found = RealPhotos.find(anna)
        assertEquals("Gravatar", found?.source)
        // The mail address is trimmed and lower-cased before it is hashed (Gravatar's rule).
        assertTrue(asked.single().startsWith("https://www.gravatar.com/avatar/"))
        assertTrue(OrbitRepository.photoFile(found!!.file).length() > 0)
        // Square: cut from the middle of the 256 × 320 picture.
        val stored = android.graphics.BitmapFactory.decodeFile(OrbitRepository.photoFile(found.file).path)
        assertEquals(stored.width, stored.height)
        assertEquals(found, RealPhotos.find(anna))
        assertEquals(1, asked.size)
    }

    @Test fun organisationTriesItsWebsiteFirst() = runBlocking {
        RealPhotos.fetch = { url -> if ("apple-touch-icon" in url) png(200) else null }
        assertEquals("radhaus.at", RealPhotos.find(shop)?.source)
    }

    @Test fun smallPictureIsNotOffered() = runBlocking {
        RealPhotos.fetch = { png(64) }
        assertNull(RealPhotos.find(anna))
    }

    @Test fun contactWithPhotoIsNotAsked() = runBlocking {
        RealPhotos.fetch = { error("must not be asked") }
        assertNull(RealPhotos.find(anna.copy(photo = "p-1.jpg")))
    }
}
