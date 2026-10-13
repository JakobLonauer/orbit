package io.github.thelony97.orbit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Motion
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * „Speichern“ morphs into a check, the haptic lands on the peak, then the page closes
 * (PLAN 1.2, Physik statt Animation). [onSave] returns false when nothing may be saved yet.
 */
@Composable
fun SaveMorphButton(onSave: () -> Boolean, onDone: () -> Unit, label: String = "Speichern") {
    var done by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val pop by animateFloatAsState(if (done) 1f else 0f, tween(Motion.MEDIUM_MS, easing = Motion.Emphasized), label = "savePop")
    val pill = RoundedCornerShape(50)
    Row(
        Modifier
            .height(38.dp)
            .glow(if (done) Space.Teal else Space.Violet, 14.dp, pill, 0.45f)
            .clip(pill)
            .background(
                if (done) Brush.horizontalGradient(listOf(Space.Teal, Space.Teal.copy(alpha = 0.8f)))
                else Brush.horizontalGradient(listOf(Space.Violet, Space.Violet.copy(alpha = 0.78f))),
            )
            .clickable(enabled = !done) {
                if (onSave()) {
                    done = true
                    scope.launch {
                        delay(Motion.MEDIUM_MS.toLong())
                        haptics.perform(Haptic.Confirm)
                        delay(Motion.FAST_MS.toLong())
                        onDone()
                    }
                } else {
                    haptics.perform(Haptic.Reject)
                }
            }
            .animateContentSize(tween(Motion.MEDIUM_MS, easing = Motion.Emphasized))
            .padding(horizontal = if (done) 11.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Check, null,
            tint = Space.Void,
            modifier = Modifier.size(16.dp).graphicsLayer { val s = 1f + 0.35f * pop; scaleX = s; scaleY = s },
        )
        AnimatedVisibility(!done, exit = shrinkHorizontally(tween(Motion.MEDIUM_MS)) + fadeOut(tween(Motion.FAST_MS))) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Space.Void,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
