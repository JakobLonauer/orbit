package io.github.thelony97.orbit.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CropMathTest {
    @Test fun noZoomNoPanIsTheCentredSquare() {
        assertEquals(CropMath.Square(200, 0, 600), CropMath.square(1000, 600, 300f, 1f, 0f, 0f))
        assertEquals(CropMath.Square(0, 100, 400), CropMath.square(400, 600, 300f, 1f, 0f, 0f))
    }

    @Test fun zoomShrinksTheSquareAroundTheCentre() {
        assertEquals(CropMath.Square(350, 150, 300), CropMath.square(1000, 600, 300f, 2f, 0f, 0f))
    }

    @Test fun panningRightShowsTheLeftPart() {
        // Frame 300 px over a 600 px tall image: 0.5 view px per image px. Moving the image
        // 100 view px to the right moves the window 200 image px to the left.
        assertEquals(CropMath.Square(0, 0, 600), CropMath.square(1000, 600, 300f, 1f, 1000f, 0f))
        assertEquals(CropMath.Square(0, 0, 600), CropMath.square(1000, 600, 300f, 1f, 100f, 0f))
        assertEquals(CropMath.Square(150, 0, 600), CropMath.square(1000, 600, 300f, 1f, 25f, 0f))
    }

    @Test fun panIsClampedSoTheFrameStaysCovered() {
        val (mx, my) = CropMath.maxPan(1000, 600, 300f, 1f)
        assertEquals(100f, mx, 0.01f)
        assertEquals(0f, my, 0.01f)
        val (px, py) = CropMath.clampPan(1000, 600, 300f, 1f, 500f, -80f)
        assertEquals(100f, px, 0.01f)
        assertEquals(0f, py, 0.01f)
    }

    @Test fun zoomBelowOneCountsAsOne() {
        assertEquals(CropMath.square(800, 800, 300f, 1f, 0f, 0f), CropMath.square(800, 800, 300f, 0.5f, 0f, 0f))
    }
}
