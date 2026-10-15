package io.github.thelony97.orbit.call

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.telecom.Call
import android.telecom.CallEndpoint
import android.telecom.TelecomManager

/**
 * Bewegungsgesten während eines Anrufs (PLAN 9 B12, einschaltbar): ringing + turned over = the
 * ringer goes silent; talking at the ear + put down flat on the table = speaker. Sensors only while
 * a call exists, never in battery saver; each gesture once per moment, nothing is ever hung up.
 */
object CallGestures : SensorEventListener {

    private const val FACE_DOWN = -8.5f
    private const val FLAT = 9.0f
    private const val HOLD_MS = 350L

    private var sensors: SensorManager? = null
    private var telecom: TelecomManager? = null
    private var running = false

    private var near = false
    private var leftEarAt = 0L
    private var downSince = 0L
    private var flatSince = 0L
    /** The ringing call seen face up (or held): only then is face down a turn. */
    private var armedFor: Call? = null

    fun start(ctx: Context) {
        if (running) return
        val power = ctx.getSystemService(android.os.PowerManager::class.java)
        if (power?.isPowerSaveMode == true) return
        val sm = ctx.getSystemService(SensorManager::class.java) ?: return
        val gravity = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensors = sm
        telecom = ctx.getSystemService(TelecomManager::class.java)
        running = true
        near = false
        leftEarAt = 0L
        downSince = 0L
        flatSince = 0L
        sm.registerListener(this, gravity, SensorManager.SENSOR_DELAY_UI)
        sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { sensors?.unregisterListener(this) }
        sensors = null
        armedFor = null
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        runCatching {
            when (event.sensor.type) {
                Sensor.TYPE_PROXIMITY -> proximity(event)
                else -> gravity(event.values[2], event.timestamp / 1_000_000L)
            }
        }
    }

    private fun proximity(event: SensorEvent) {
        val nowNear = event.values[0] < event.sensor.maximumRange
        if (near && !nowNear) leftEarAt = event.timestamp / 1_000_000L
        near = nowNear
    }

    /** [z]: gravity along the screen's normal (+9.8 face up, −9.8 face down). */
    private fun gravity(z: Float, now: Long) {
        val ringing = CallSession.ringing()
        // Turned over: seen not face down while it rang, then face down for a moment.
        if (ringing != null && z > -3f) armedFor = ringing
        downSince = if (z < FACE_DOWN) downSince.takeIf { it > 0 } ?: now else 0L
        val faceDown = downSince > 0 && now - downSince >= HOLD_MS
        if (ringing != null && ringing === armedFor && faceDown) {
            armedFor = null
            runCatching { telecom?.silenceRinger() }
            return
        }
        // From the ear onto the table: just left the ear, then flat and still for a moment.
        flatSince = if (z > FLAT) flatSince.takeIf { it > 0 } ?: now else 0L
        val talking = CallSession.primary()?.let { CallSession.stateOf(it) } == Call.STATE_ACTIVE
        val handset = talking && CallSession.audio.value.type == CallEndpoint.TYPE_EARPIECE
        val putDown = !near && leftEarAt > 0 && now - leftEarAt < PUT_DOWN_MS
        val flat = flatSince > 0 && now - flatSince >= HOLD_MS
        if (handset && putDown && flat) {
            leftEarAt = 0L
            CallSession.routeTo(CallEndpoint.TYPE_SPEAKER)
        }
    }

    private const val PUT_DOWN_MS = 4_000L
}
