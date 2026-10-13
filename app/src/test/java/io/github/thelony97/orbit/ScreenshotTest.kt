package io.github.thelony97.orbit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.thelony97.orbit.core.designsystem.glass
import io.github.thelony97.orbit.core.designsystem.glassSource
import io.github.thelony97.orbit.core.designsystem.holo
import io.github.thelony97.orbit.core.designsystem.rememberGlassState
import io.github.thelony97.orbit.ui.probe.SharedAvatarProbe
import io.github.thelony97.orbit.ui.theme.OrbitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String) {
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun glassOverContent() {
        compose.setContent {
            OrbitTheme {
                val glass = rememberGlassState()
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().glassSource(glass)) {
                        listOf(
                            Color(0xFF6750A4), Color(0xFFE8437A), Color(0xFF1FA67A),
                            Color(0xFFF2A93B), Color(0xFF3B82F2), Color(0xFFE8437A),
                        )
                            .forEach { c ->
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .background(c),
                                ) {
                                    Box(
                                        Modifier
                                            .padding(24.dp)
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(Color.White),
                                    )
                                }
                            }
                    }
                    // Bar sits over the colour bands so the blur has something to show.
                    Surface(
                        color = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(120.dp)
                            .glass(glass, MaterialTheme.colorScheme.surface, tint = 0.45f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("Glas", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
        shot("glass")
    }

    @Test
    fun sharedAvatarClosedAndOpen() {
        compose.setContent { OrbitTheme { SharedAvatarProbe(initiallyOpen = false) } }
        shot("shared_closed")
    }

    @Test
    fun sharedAvatarOpen() {
        compose.setContent { OrbitTheme { SharedAvatarProbe(initiallyOpen = true) } }
        shot("shared_open")
    }

    @Test
    fun holoSheen() {
        compose.setContent {
            OrbitTheme {
                Box(
                    Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(Color(0xFF2B2440))
                        .holo(phase = { 0.15f }, tiltX = { 0.2f }),
                )
            }
        }
        shot("holo")
    }
}
