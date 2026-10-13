package io.github.thelony97.orbit.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.ui.theme.Space

/**
 * Platzhalter in exakter Endform (PLAN 1.2): grey shapes exactly where the content will be,
 * breathing slowly, instead of spinners. The breath is read in the draw phase only.
 */
@Composable
fun rememberBreath(): State<Float> = rememberInfiniteTransition(label = "breath").animateFloat(
    initialValue = 0.45f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
    label = "breath",
)

fun Modifier.placeholder(breath: State<Float>, shape: Shape = RoundedCornerShape(6.dp)): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(outline, Space.PanelHighest.copy(alpha = 0.75f * breath.value))
}

/** A text line placeholder: [width] of the row, at the height of the given text size. */
@Composable
fun PlaceholderLine(breath: State<Float>, width: Float, height: Dp = 12.dp, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(width).height(height).placeholder(breath, RoundedCornerShape(height / 2)))
}

/** Shape of one mail row (subject + date) while the mailbox is asked. */
@Composable
fun MailRowPlaceholder(breath: State<Float>, subjectWidth: Float) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        PlaceholderLine(breath, subjectWidth, 13.dp, Modifier.weight(1f))
        Spacer(Modifier.width(24.dp))
        Box(Modifier.width(36.dp).height(11.dp).placeholder(breath, RoundedCornerShape(6.dp)))
    }
}

/** Shape of the contact list while the data file loads (header, search, rows). */
@Composable
fun ListPlaceholder() {
    val breath = rememberBreath()
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(11.dp).placeholder(breath, CircleShape))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(72.dp).height(22.dp).placeholder(breath, RoundedCornerShape(11.dp)))
        }
        Box(Modifier.padding(horizontal = 16.dp, vertical = 16.dp).fillMaxWidth().height(42.dp).placeholder(breath, RoundedCornerShape(50)))
        val widths = listOf(0.42f, 0.55f, 0.36f, 0.6f, 0.47f, 0.4f, 0.52f, 0.33f)
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            widths.forEach { w ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).placeholder(breath, CircleShape))
                    Spacer(Modifier.width(12.dp))
                    PlaceholderLine(breath, w, 14.dp)
                }
            }
        }
    }
}
