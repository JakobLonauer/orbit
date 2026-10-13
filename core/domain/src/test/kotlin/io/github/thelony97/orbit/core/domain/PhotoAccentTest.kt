package io.github.thelony97.orbit.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoAccentTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun pureColours() {
        assertEquals(0f, PhotoAccent.hue(IntArray(100) { rgb(230, 20, 20) })!!.let { if (it > 180) it - 360 else it }, 2f)
        assertEquals(120f, PhotoAccent.hue(IntArray(100) { rgb(20, 230, 20) })!!, 2f)
        assertEquals(240f, PhotoAccent.hue(IntArray(100) { rgb(20, 20, 230) })!!, 2f)
    }

    @Test fun greyPictureHasNoAccent() {
        assertNull(PhotoAccent.hue(IntArray(400) { i -> (i % 255).let { rgb(it, it, it) } }))
        assertNull(PhotoAccent.hue(IntArray(0)))
    }

    @Test fun vividDetailBeatsSkinAndGrey() {
        // 50 % grey wall, 35 % skin (low saturation orange), 15 % bright teal jumper.
        val px = IntArray(1000) { i ->
            when {
                i < 500 -> rgb(128, 128, 128)
                i < 850 -> rgb(225, 180, 150)
                else -> rgb(20, 200, 190)
            }
        }
        val h = PhotoAccent.hue(px)
        assertNotNull(h)
        assertEquals(177f, h!!, 8f)
    }

    @Test fun transparentPixelsAreIgnored() {
        val px = IntArray(100) { i -> if (i < 90) 0x00FF0000 else rgb(20, 20, 230) }
        assertEquals(240f, PhotoAccent.hue(px)!!, 2f)
    }
}
