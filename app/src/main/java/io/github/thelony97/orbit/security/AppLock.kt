package io.github.thelony97.orbit.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Locks Orbit as soon as it leaves the screen, or after a chosen delay (PLAN 3.13). Pickers that
 * Orbit opens itself (photo, contact, folder) don't count as leaving. A new process always starts locked.
 */
object AppLock {

    private val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    @Volatile private var externalFlow = false
    @Volatile private var prompting = false

    fun isAvailable(ctx: Context): Boolean =
        BiometricManager.from(ctx).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** Call right before launching a system picker so returning doesn't ask again. */
    fun allowExternal() {
        externalFlow = true
    }

    /** Set when Orbit left the screen with a lock delay; the lock happens on return if it was long enough. */
    @Volatile private var awaySince = 0L
    @Volatile private var delayMs = 0L

    fun onStart() {
        externalFlow = false
        val since = awaySince
        if (since > 0L && android.os.SystemClock.elapsedRealtime() - since >= delayMs) _locked.value = true
        awaySince = 0L
    }

    /** [delaySeconds] 0 locks at once, so the recent-apps preview already shows the lock. */
    fun onStop(changingConfigurations: Boolean, lockEnabled: Boolean, delaySeconds: Int = 0) {
        if (!lockEnabled || changingConfigurations || externalFlow || prompting) return
        if (delaySeconds <= 0) {
            _locked.value = true
            return
        }
        delayMs = delaySeconds * 1000L
        awaySince = android.os.SystemClock.elapsedRealtime()
    }

    fun unlock() {
        _locked.value = false
    }

    fun prompt(activity: FragmentActivity) {
        if (prompting || !_locked.value) return
        if (!isAvailable(activity)) {
            // No screen lock on the device – nothing to verify against.
            unlock()
            return
        }
        prompting = true
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    unlock()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Orbit entsperren")
            .setSubtitle("Fingerabdruck, Gesicht oder Displaysperre")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        try {
            prompt.authenticate(info)
        } catch (e: Exception) {
            prompting = false
        }
    }
}
