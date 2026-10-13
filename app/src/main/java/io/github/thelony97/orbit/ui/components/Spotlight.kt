package io.github.thelony97.orbit.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import io.github.thelony97.orbit.core.designsystem.Motion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Spotlight (PLAN 1.2): a glass surface lights up radially under the finger and fades softly.
 * Only watches pointer events – never consumes them, so clicks, swipes and scrolling behave as
 * before. When a parent starts scrolling (the move gets consumed) the light goes out.
 */
fun Modifier.spotlight(color: Color = Color.White): Modifier = this then SpotlightElement(color)

private data class SpotlightElement(val color: Color) : ModifierNodeElement<SpotlightNode>() {
    override fun create() = SpotlightNode(color)
    override fun update(node: SpotlightNode) {
        node.color = color
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "spotlight"
    }
}

private class SpotlightNode(var color: Color) : Modifier.Node(), DrawModifierNode, PointerInputModifierNode {
    private val glow = Animatable(0f)
    private var center = Offset.Unspecified
    private var job: Job? = null

    private fun fade(target: Float, spec: AnimationSpec<Float>) {
        if (!isAttached) return
        job?.cancel()
        job = coroutineScope.launch {
            glow.animateTo(target, spec) { invalidateDraw() }
        }
    }

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        val change = pointerEvent.changes.firstOrNull() ?: return
        when (pass) {
            PointerEventPass.Main -> when (pointerEvent.type) {
                PointerEventType.Press -> {
                    center = change.position
                    fade(1f, tween(Motion.FAST_MS))
                }
                PointerEventType.Move -> if (change.pressed && glow.targetValue > 0f) {
                    center = change.position
                    invalidateDraw()
                }
                PointerEventType.Release -> fade(0f, tween(Motion.MAX_MS))
                else -> Unit
            }
            // A scrolling parent took the gesture: no light trail across the list.
            PointerEventPass.Final -> if (pointerEvent.type == PointerEventType.Move && change.isConsumed && glow.targetValue > 0f) {
                fade(0f, tween(Motion.FAST_MS))
            }
            else -> Unit
        }
    }

    override fun onCancelPointerInput() {
        fade(0f, tween(Motion.FAST_MS))
    }

    override fun ContentDrawScope.draw() {
        val g = glow.value
        if (g > 0f && center.isSpecified) {
            val radius = maxOf(size.minDimension * 0.9f, 120f)
            drawRect(
                Brush.radialGradient(
                    listOf(color.copy(alpha = 0.11f * g), color.copy(alpha = 0.04f * g), color.copy(alpha = 0f)),
                    center = center,
                    radius = radius,
                ),
            )
        }
        drawContent()
    }
}
