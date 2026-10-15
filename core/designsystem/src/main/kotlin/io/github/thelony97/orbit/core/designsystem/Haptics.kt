package io.github.thelony97.orbit.core.designsystem

import android.annotation.SuppressLint
import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * One vocabulary of haptics for the whole app (PLAN 9.2 Haptik-Vokabular). Every new function
 * uses only these: Tick, Einrasten ([Threshold]), Bestätigen ([Confirm]), Abwehr ([Reject]),
 * Materialisieren ([Materialize]), Takt ([Segment]).
 */
enum class Haptic(val constant: Int) {
    /** Scrubbing across list items, alphabet rail, year ruler. */
    Tick(HapticFeedbackConstants.CLOCK_TICK),
    /** Einrasten: a swipe crosses its commit threshold, a drag snaps into a notch. */
    Threshold(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE),
    /** Something was saved, checked off, assigned, taken over. */
    Confirm(HapticFeedbackConstants.CONFIRM),
    /** Abwehr: not possible, undone, silenced. */
    Reject(HapticFeedbackConstants.REJECT),
    /** Materialisieren: something unknown takes shape (a caller is recognised, a ghost appears). */
    Materialize(HapticFeedbackConstants.CONFIRM),
    /** Takt: a sequence plays step by step (macro keys, redial attempts). */
    Segment(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK),
}

class Haptics internal constructor(private val view: View) {
    fun perform(h: Haptic) {
        if (h == Haptic.Materialize && materialize()) return
        view.performHapticFeedback(h.constant)
    }

    /** A soft rise into a click – only where the vibrator can play primitives, else Confirm. */
    @SuppressLint("MissingPermission") // VIBRATE is declared in the app manifest.
    private fun materialize(): Boolean = runCatching {
        val vibrator = view.context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return false
        val rise = VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
        val click = VibrationEffect.Composition.PRIMITIVE_CLICK
        if (!vibrator.areAllPrimitivesSupported(rise, click)) return false
        vibrator.vibrate(
            VibrationEffect.startComposition()
                .addPrimitive(rise, 0.45f)
                .addPrimitive(click, 0.9f, 30)
                .compose(),
        )
        true
    }.getOrDefault(false)
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
