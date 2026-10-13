package io.github.thelony97.orbit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.thelony97.orbit.ui.theme.OrbitTheme
import io.github.thelony97.orbit.ui.theme.Space
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h400dp-xxhdpi")
class SpotlightTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun clicksStillReachTheCard() {
        var clicks = 0
        compose.setContent {
            OrbitTheme {
                Box(Modifier.size(300.dp, 120.dp).testTag("card").glass().clickable { clicks++ }) { Text("Karte") }
            }
        }
        compose.onNodeWithTag("card").performClick()
        compose.waitForIdle()
        assertEquals(1, clicks)
    }

    /** Finger held on the card: the light sits under it (screenshot spotlight.png). */
    @Test fun lightUnderTheFinger() {
        compose.setContent {
            OrbitTheme {
                Box(Modifier.fillMaxSize().background(Space.Void).padding(24.dp)) {
                    // No clickable here: the picture shows only the spotlight, not the ripple.
                    Box(Modifier.size(340.dp, 160.dp).testTag("card").glass()) { Text("Karte", Modifier.padding(16.dp), color = Space.Text) }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("card").performTouchInput { down(Offset(width * 0.2f, height * 0.3f)) }
        compose.mainClock.advanceTimeBy(300)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/spotlight.png")
        compose.onNodeWithTag("card").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
    }
}
