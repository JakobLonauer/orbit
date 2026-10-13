package io.github.thelony97.orbit.call

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.ui.call.Dialpad
import io.github.thelony97.orbit.ui.theme.OrbitTheme

/**
 * Wählen (PLAN 3.12): the dial screen Android opens for "tel:" links and the phone button while
 * Orbit is the phone app. Disabled until Jakob turns the phone app on (see [PhoneRole]). Never
 * behind the Orbit lock; emergency numbers go straight to Telecom like any other number.
 */
class DialerActivity : ComponentActivity() {

    private var pending: Uri? = null

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val uri = pending
        pending = null
        if (granted && uri != null) place(uri) else if (uri != null) Toast.makeText(this, "Anrufen nicht erlaubt", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initial = intent?.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart.orEmpty()
        setContent {
            OrbitTheme {
                val data by OrbitRepository.data.collectAsState()
                val doc = data ?: OrbitData()
                val idx = remember(doc) { Index(doc) }
                Dialpad(
                    data = doc,
                    idx = idx,
                    onCall = { number -> place(Uri.fromParts("tel", number, null)) },
                    onClose = { finish() },
                    initial = initial,
                    onVoicemail = { place(Uri.fromParts("voicemail", "", null)) },
                )
            }
        }
    }

    private fun emergency(uri: Uri): Boolean = uri.scheme == "tel" && runCatching {
        getSystemService(android.telephony.TelephonyManager::class.java)?.isEmergencyNumber(uri.schemeSpecificPart) == true
    }.getOrDefault(false)

    private fun place(uri: Uri) {
        // Emergency numbers never wait for a permission dialog: straight to Telecom.
        if (!emergency(uri) && checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            pending = uri
            permission.launch(Manifest.permission.CALL_PHONE)
            return
        }
        try {
            getSystemService(TelecomManager::class.java).placeCall(uri, Bundle())
            finish()
        } catch (e: Exception) {
            // Last resort: the preinstalled phone app with the number filled in – a number typed
            // here (above all an emergency number) must always get through.
            // Emergency numbers too: the preinstalled phone app dials them without any permission.
            val telecom = getSystemService(TelecomManager::class.java)
            val fallback = runCatching { telecom.systemDialerPackage }.getOrNull()?.let { Intent(Intent.ACTION_DIAL, uri).setPackage(it) }
            runCatching {
                startActivity(fallback!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
            }.onFailure { Toast.makeText(this, "Anruf nicht möglich", Toast.LENGTH_SHORT).show() }
        }
    }
}
