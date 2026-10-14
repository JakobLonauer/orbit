package io.github.thelony97.orbit.ui.components

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the phone has a usable internet connection right now (PLAN 1.4 „Offline: sagen knapp,
 * was fehlt, und holen es nach“). Starts with the current state, follows changes live.
 */
@Composable
fun rememberOnline(): State<Boolean> {
    val ctx = LocalContext.current
    val cm = remember { ctx.getSystemService(ConnectivityManager::class.java) }
    val state = remember {
        mutableStateOf(
            cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: (cm == null),
        )
    }
    DisposableEffect(cm) {
        if (cm == null) return@DisposableEffect onDispose { }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                state.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }

            override fun onLost(network: Network) {
                state.value = false
            }
        }
        val registered = runCatching { cm.registerDefaultNetworkCallback(callback) }.isSuccess
        onDispose { if (registered) runCatching { cm.unregisterNetworkCallback(callback) } }
    }
    return state
}
