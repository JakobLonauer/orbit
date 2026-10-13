package io.github.thelony97.orbit.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import java.time.Duration

/** The update download must survive its banner leaving the screen (bug in 1.0.54). */
@RunWith(RobolectricTestRunner::class)
class AppWorkTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun workOutlivesTheComposableThatStartedIt() {
        var appDone = false
        var screenDone = false
        var shown by mutableStateOf(true)
        compose.setContent {
            if (shown) {
                val scope = rememberCoroutineScope()
                LaunchedEffect(Unit) {
                    AppWork.launch { delay(200); appDone = true }
                    scope.launch { delay(200); screenDone = true }
                }
                Text("Banner")
            }
        }
        compose.waitForIdle()
        shown = false // the banner scrolls away
        compose.waitForIdle()
        ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS)
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        ShadowLooper.idleMainLooper(Duration.ofMillis(300))
        assertTrue("app-wide work finished", appDone)
        assertFalse("screen-scoped work was cancelled", screenDone)
    }
}
