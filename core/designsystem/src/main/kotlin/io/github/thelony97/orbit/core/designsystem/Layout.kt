package io.github.thelony97.orbit.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Spacing tokens (PLAN W9: Abstände 4/8 dp). [Gutter] is the one outer edge of every screen:
 * cards, heads, chip rows, list rows and the timeline all start 16 dp from the screen edge.
 */
object Spacing {
    val XS = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 24.dp
    val Gutter = 16.dp
}

/** Fixed radii (W9): rows and tiles 12, cards 20, sheets and dialogs 28, chips and pills fully round. */
object Corners {
    val Row = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(20.dp)
    val Sheet = RoundedCornerShape(28.dp)
    val Pill = RoundedCornerShape(50)
}
