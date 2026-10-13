package io.github.thelony97.orbit.core.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Motion tokens (PLAN W9): no animation is longer than [MAX_MS] except the
 * listed exceptions (first-start reveal, archive farewell, call ring pulse).
 */
object Motion {
    const val FAST_MS = 150
    const val MEDIUM_MS = 250
    const val EMPHASIZED_MS = 350
    const val MAX_MS = 400

    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Exit: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    fun <T> fast(): FiniteAnimationSpec<T> = tween(FAST_MS, easing = Standard)
    fun <T> medium(): FiniteAnimationSpec<T> = tween(MEDIUM_MS, easing = Standard)
    fun <T> emphasized(): FiniteAnimationSpec<T> = tween(EMPHASIZED_MS, easing = Emphasized)

    /** Physical feel for drags and snaps; settles well inside [MAX_MS]. */
    fun <T> snappy(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.82f, stiffness = 900f)
}
