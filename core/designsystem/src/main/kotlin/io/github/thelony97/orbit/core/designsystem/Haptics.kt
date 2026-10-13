package io.github.thelony97.orbit.core.designsystem

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** One vocabulary of haptics for the whole app (PLAN W-details). */
enum class Haptic(val constant: Int) {
    /** Scrubbing across list items, alphabet rail, year ruler. */
    Tick(HapticFeedbackConstants.CLOCK_TICK),
    /** A swipe crosses its commit threshold. */
    Threshold(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE),
    /** Something was saved, checked off, assigned. */
    Confirm(HapticFeedbackConstants.CONFIRM),
    /** Not possible / undone. */
    Reject(HapticFeedbackConstants.REJECT),
}

class Haptics internal constructor(private val view: View) {
    fun perform(h: Haptic) {
        view.performHapticFeedback(h.constant)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
