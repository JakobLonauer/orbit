package io.github.thelony97.orbit.telecom

import android.telecom.Call
import android.telecom.CallEndpoint
import android.telecom.InCallService
import android.telecom.VideoProfile

/**
 * Test-build probe for R2 (Telefon-App). Deliberately NOT registered in the manifest yet:
 * it only proves the platform API compiles against compileSdk 37 with minSdk 34.
 * R2 registers it behind ROLE_DIALER, which Jakob has to grant himself.
 */
class OrbitInCallService : InCallService() {

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            CallProbe.lastState = state
        }
    }

    override fun onCallAdded(call: Call) {
        call.registerCallback(callback)
        CallProbe.lastNumber = call.details.handle?.schemeSpecificPart
        CallProbe.lastState = call.details.state
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
    }

    override fun onAvailableCallEndpointsChanged(availableEndpoints: MutableList<CallEndpoint>) {
        CallProbe.endpoints = availableEndpoints.map { it.endpointName.toString() }
    }

    fun answer(call: Call) = call.answer(VideoProfile.STATE_AUDIO_ONLY)

    fun decline(call: Call) = call.reject(false, null)

    fun hangUp(call: Call) = call.disconnect()
}

internal object CallProbe {
    @Volatile var lastNumber: String? = null
    @Volatile var lastState: Int = Call.STATE_NEW
    @Volatile var endpoints: List<String> = emptyList()
}
