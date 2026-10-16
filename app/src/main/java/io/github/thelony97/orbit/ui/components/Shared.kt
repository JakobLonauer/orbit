package io.github.thelony97.orbit.ui.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import io.github.thelony97.orbit.core.designsystem.Corners
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import io.github.thelony97.orbit.core.designsystem.Motion

/** The SharedTransitionLayout around the NavDisplay; `null` in previews and screenshot tests. */
val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/**
 * Geteilter Übergang Liste → Kontakt (PLAN W3): the same key on the list row and on the contact
 * page lets the avatar (or name) fly between the two while the pages cross-fade. Without a
 * shared scope (tests, previews) this is a no-op.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedContact(key: String, text: Boolean = false): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val scope = LocalNavAnimatedContentScope.current
    return with(shared) {
        if (text) {
            // Text changes size: scale the bounds instead of re-laying out the glyphs.
            this@sharedContact.sharedBounds(
                rememberSharedContentState(key),
                animatedVisibilityScope = scope,
                boundsTransform = { _, _ -> tween(Motion.EMPHASIZED_MS, easing = Motion.Emphasized) },
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
            )
        } else {
            this@sharedContact.sharedElement(
                rememberSharedContentState(key),
                animatedVisibilityScope = scope,
                boundsTransform = { _, _ -> tween(Motion.EMPHASIZED_MS, easing = Motion.Emphasized) },
            )
        }
    }
}

/**
 * Die Kontaktseite wächst aus ihrer Zeile und schrumpft beim Zurück wieder hinein (PLAN 9 E21,
 * 9.2 „Übergänge sind Wachsen/Schrumpfen derselben Fläche“): the same key on the list row and on
 * the page's background. The page keeps its width and is clipped from the top, so its head slides
 * into the row; with predictive back the clip follows the finger. Without a shared scope a no-op.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedPage(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val scope = LocalNavAnimatedContentScope.current
    return with(shared) {
        this@sharedPage.sharedBounds(
            rememberSharedContentState(key),
            animatedVisibilityScope = scope,
            enter = fadeIn(tween(Motion.MEDIUM_MS, easing = Motion.Standard)),
            exit = fadeOut(tween(Motion.MEDIUM_MS, easing = Motion.Standard)),
            boundsTransform = { _, _ -> tween(Motion.EMPHASIZED_MS, easing = Motion.Emphasized) },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopCenter),
            clipInOverlayDuringTransition = OverlayClip(Corners.Card),
        )
    }
}
