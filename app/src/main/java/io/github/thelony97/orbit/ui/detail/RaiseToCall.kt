package io.github.thelony97.orbit.ui.detail

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LIFT = 2.5f
private const val LIFT_WINDOW_MS = 1_500L
private const val UPRIGHT = 6f
private const val SETTLE_MS = 400L

/**
 * PLAN 9 B12 „Kontakt offen + ans Ohr = anrufen“ (einschaltbar): while the contact page is in front,
 * a lift (a short movement), then the phone upright at the ear for a moment calls. Once per lift;
 * away from the ear arms it again. Never in battery saver.
 */
@Composable
internal fun RaiseToCall(enabled: Boolean, onRaise: () -> Unit) {
    val ctx = LocalContext.current
    val raise by rememberUpdatedState(onRaise)
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(enabled) {
        val saver = ctx.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true
        val sm = ctx.getSystemService(SensorManager::class.java)
        val proximity = sm?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        val gravity = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val linear = sm?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val ready = enabled && !saver
        if (!ready || sm == null || proximity == null) return@LifecycleResumeEffect onPauseOrDispose { }
        if (gravity == null || linear == null) return@LifecycleResumeEffect onPauseOrDispose { }
        var liftAt = 0L
        var upright = false
        var fired = false
        var pending: Job? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val now = SystemClock.elapsedRealtime()
                when (e.sensor.type) {
                    Sensor.TYPE_LINEAR_ACCELERATION -> {
                        val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
                        if (x * x + y * y + z * z > LIFT * LIFT) liftAt = now
                    }
                    Sensor.TYPE_GRAVITY -> upright = e.values[1] > UPRIGHT
                    Sensor.TYPE_PROXIMITY -> {
                        val near = e.values[0] < e.sensor.maximumRange
                        if (!near) {
                            pending?.cancel()
                            fired = false
                        } else if (!fired && upright && now - liftAt <= LIFT_WINDOW_MS) {
                            pending = scope.launch {
                                delay(SETTLE_MS)
                                fired = true
                                raise()
                            }
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm.registerListener(listener, proximity, SensorManager.SENSOR_DELAY_NORMAL)
        sm.registerListener(listener, gravity, SensorManager.SENSOR_DELAY_UI)
        sm.registerListener(listener, linear, SensorManager.SENSOR_DELAY_UI)
        onPauseOrDispose {
            pending?.cancel()
            sm.unregisterListener(listener)
        }
    }
}
