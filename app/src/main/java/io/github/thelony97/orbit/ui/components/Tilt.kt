package io.github.thelony97.orbit.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext

/**
 * How the phone is tilted, x and y in -1..1 (flat = 0), smoothed. Only listens while on screen;
 * without a sensor it stays at 0. Read it in the draw phase so tilting never recomposes.
 */
@Composable
fun rememberTilt(): State<Offset> {
    val ctx = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(ctx) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val g = SensorManager.GRAVITY_EARTH
                val x = (-e.values[0] / g).coerceIn(-1f, 1f)
                val y = (e.values[1] / g).coerceIn(-1f, 1f)
                val old = tilt.value
                // Low-pass: calm, no jitter.
                tilt.value = Offset(old.x + (x - old.x) * 0.15f, old.y + (y - old.y) * 0.15f)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) sm?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(listener) }
    }
    return tilt
}
