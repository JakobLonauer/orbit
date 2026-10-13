package io.github.thelony97.orbit.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * Frosted glass for bars and sheets that float over content.
 * The content behind is marked with [glassSource], the floating surface with [glass].
 */
@Composable
fun rememberGlassState(): HazeState = remember { HazeState() }

fun Modifier.glassSource(state: HazeState): Modifier = hazeSource(state)

fun Modifier.glass(
    state: HazeState,
    surface: Color,
    tint: Float = 0.62f,
    blur: Dp = 24.dp,
): Modifier = hazeEffect(
    state = state,
    style = HazeStyle(
        backgroundColor = surface,
        tints = listOf(HazeTint(surface.copy(alpha = tint))),
        blurRadius = blur,
        noiseFactor = 0.06f,
    ),
)
