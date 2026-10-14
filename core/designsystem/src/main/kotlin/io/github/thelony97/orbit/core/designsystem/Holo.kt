package io.github.thelony97.orbit.core.designsystem

import android.graphics.RuntimeShader
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ShaderBrush

/**
 * Holographic sheen (birthday hero card, archive farewell). AGSL, API 33+.
 * [phase] in 0..1 shifts the band; [tiltX]/[tiltY] come from the gyroscope later.
 */
private const val HOLO_AGSL = """
uniform float2 size;
uniform float phase;
uniform float2 tilt;
half4 main(float2 p) {
    float2 uv = p / size;
    float band = uv.x * 0.8 + uv.y * 0.6 + tilt.x * 0.3 - tilt.y * 0.2 + phase;
    half3 c = half3(
        0.5 + 0.5 * sin(6.2831 * band),
        0.5 + 0.5 * sin(6.2831 * (band + 0.33)),
        0.5 + 0.5 * sin(6.2831 * (band + 0.66))
    );
    float glint = smoothstep(0.92, 1.0, sin(6.2831 * (band * 1.7)));
    return half4(c * 0.22 + glint * 0.35, 0.22 + glint * 0.3);
}
"""

/** [behind] = the sheen goes under the content (Ich-Karte: never over the QR code). */
fun Modifier.holo(phase: () -> Float, tiltX: () -> Float = { 0f }, tiltY: () -> Float = { 0f }, behind: Boolean = false): Modifier =
    drawWithCache {
        val shader = RuntimeShader(HOLO_AGSL)
        val brush = ShaderBrush(shader)
        onDrawWithContent {
            if (!behind) drawContent()
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("phase", phase())
            shader.setFloatUniform("tilt", tiltX(), tiltY())
            drawRect(brush)
            if (behind) drawContent()
        }
    }
