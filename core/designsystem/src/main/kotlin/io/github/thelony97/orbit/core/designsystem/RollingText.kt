package io.github.thelony97.orbit.core.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * Zahlen rollen (PLAN 1.2 „Typografie in Bewegung“): every digit that changes rolls on its own
 * slot – a digit that grows comes in from below, one that shrinks from above; words in between
 * („heute“ → „morgen“) cross-fade as a whole. Unchanged parts stand still. Tabular figures, so
 * nothing beside a rolling digit moves.
 */
@Composable
fun RollingText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current, color: Color = Color.Unspecified) {
    val tnum = style.copy(fontFeatureSettings = "tnum")
    val parts = rollingParts(text)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // Slots counted from the right: „9:55“ → „10:00“ keeps the minutes on their slots.
        parts.forEachIndexed { i, part ->
            androidx.compose.runtime.key(parts.size - i) { RollingPart(part, tnum, color) }
        }
    }
}

/** Each digit on its own, every run of other characters as one part. */
fun rollingParts(text: String): List<String> {
    val out = ArrayList<String>()
    val run = StringBuilder()
    for (c in text) {
        if (c.isDigit()) {
            if (run.isNotEmpty()) { out += run.toString(); run.clear() }
            out += c.toString()
        } else {
            run.append(c)
        }
    }
    if (run.isNotEmpty()) out += run.toString()
    return out
}

@Composable
private fun RollingPart(part: String, style: TextStyle, color: Color) {
    AnimatedContent(
        targetState = part,
        transitionSpec = {
            val a = initialState.singleOrNull()?.takeIf { it.isDigit() }
            val b = targetState.singleOrNull()?.takeIf { it.isDigit() }
            val roll = Motion.fast<androidx.compose.ui.unit.IntOffset>()
            when {
                // Up also for 9 → 0: a counter going over.
                a != null && b != null && (b > a || a == '9' && b == '0') ->
                    (slideInVertically(roll) { it } + fadeIn(tween(Motion.FAST_MS))) togetherWith (slideOutVertically(roll) { -it } + fadeOut(tween(Motion.FAST_MS)))
                a != null && b != null ->
                    (slideInVertically(roll) { -it } + fadeIn(tween(Motion.FAST_MS))) togetherWith (slideOutVertically(roll) { it } + fadeOut(tween(Motion.FAST_MS)))
                else -> fadeIn(tween(Motion.FAST_MS)) togetherWith fadeOut(tween(Motion.FAST_MS))
            }.using(SizeTransform(clip = true) { _, _ -> tween(Motion.FAST_MS, easing = Motion.Standard) })
        },
        label = "roll",
    ) { shown ->
        Text(shown, style = style, color = color, maxLines = 1)
    }
}
