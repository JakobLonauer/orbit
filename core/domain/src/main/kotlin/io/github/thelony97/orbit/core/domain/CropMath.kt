package io.github.thelony97.orbit.core.domain

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Profile picture cropping (PLAN 3.3). The image is shown so that it just fills a square frame
 * of side [frame] (view pixels), times the user's [zoom] (≥ 1), moved by the pan offset from the
 * frame centre. All functions are pure so the mapping view ↔ image is unit-tested.
 */
object CropMath {

    data class Square(val left: Int, val top: Int, val side: Int)

    /** View pixels per image pixel at zoom 1: the shorter side fills the frame. */
    fun baseScale(width: Int, height: Int, frame: Float): Float = frame / min(width, height)

    /** Largest pan in each direction so the image always covers the frame. */
    fun maxPan(width: Int, height: Int, frame: Float, zoom: Float): Pair<Float, Float> {
        val s = baseScale(width, height, frame) * zoom
        return ((width * s - frame) / 2f).coerceAtLeast(0f) to ((height * s - frame) / 2f).coerceAtLeast(0f)
    }

    fun clampPan(width: Int, height: Int, frame: Float, zoom: Float, panX: Float, panY: Float): Pair<Float, Float> {
        val (mx, my) = maxPan(width, height, frame, zoom)
        return panX.coerceIn(-mx, mx) to panY.coerceIn(-my, my)
    }

    /** The square of the image (in image pixels) that is inside the frame. */
    fun square(width: Int, height: Int, frame: Float, zoom: Float, panX: Float, panY: Float): Square {
        val z = zoom.coerceAtLeast(1f)
        val (px, py) = clampPan(width, height, frame, z, panX, panY)
        val s = baseScale(width, height, frame) * z
        val side = (min(width, height) / z).roundToInt().coerceIn(1, min(width, height))
        val cx = width / 2f - px / s
        val cy = height / 2f - py / s
        val left = (cx - side / 2f).roundToInt().coerceIn(0, width - side)
        val top = (cy - side / 2f).roundToInt().coerceIn(0, height - side)
        return Square(left, top, side)
    }
}
