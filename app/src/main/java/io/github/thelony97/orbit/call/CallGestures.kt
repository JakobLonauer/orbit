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
    /** Last moment the phone moved (linear acceleration above [STILL]). */
    private var movedAt = 0L
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
        movedAt = 0L
        sm.registerListener(this, gravity, SensorManager.SENSOR_DELAY_UI)
        sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
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
            val now = event.timestamp / 1_000_000L
            when (event.sensor.type) {
                Sensor.TYPE_PROXIMITY -> proximity(event)
                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
                    if (x * x + y * y + z * z > STILL * STILL) movedAt = now
                }
                else -> {
                    val z = event.values[2]
                    if (!turnedOver(z, now)) putDown(z, now)
                }
            }
        }
    }

    private fun proximity(event: SensorEvent) {
        val nowNear = event.values[0] < event.sensor.maximumRange
        if (near && !nowNear) leftEarAt = event.timestamp / 1_000_000L
        near = nowNear
    }

    /** Ringing + turned over (seen face up while it rang, then face down for a moment) = silent. */
    private fun turnedOver(z: Float, now: Long): Boolean {
        val ringing = CallSession.ringing() ?: return false
        if (z > -3f) armedFor = ringing
        downSince = if (z < FACE_DOWN) downSince.takeIf { it > 0 } ?: now else 0L
        val faceDown = downSince > 0 && now - downSince >= HOLD_MS
        if (ringing !== armedFor || !faceDown) return false
        armedFor = null
        runCatching { telecom?.silenceRinger() }
        return true
    }

    /** Talking at the ear, then flat on the table and still for a moment = speaker. */
    private fun putDown(z: Float, now: Long) {
        flatSince = if (z > FLAT) flatSince.takeIf { it > 0 } ?: now else 0L
        val talking = CallSession.primary()?.let { CallSession.stateOf(it) } == Call.STATE_ACTIVE
        val handset = talking && CallSession.audio.value.type == CallEndpoint.TYPE_EARPIECE
        val fromEar = !near && leftEarAt > 0 && now - leftEarAt < PUT_DOWN_MS
        val lying = flatSince > 0 && now - flatSince >= LIE_MS && now - movedAt >= LIE_MS
        if (handset && fromEar && lying) {
            leftEarAt = 0L
            CallSession.routeTo(CallEndpoint.TYPE_SPEAKER)
        }
    }

    private const val PUT_DOWN_MS = 4_000L
    private const val LIE_MS = 700L
    private const val STILL = 0.35f
}
