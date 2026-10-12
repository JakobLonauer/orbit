package io.github.thelony97.orbit.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.thelony97.orbit.security.AppLock
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.SpaceBackground
import io.github.thelony97.orbit.ui.components.glow
import io.github.thelony97.orbit.ui.theme.Space

@Composable
fun LockScreen(activity: FragmentActivity) {
    LifecycleResumeEffect(Unit) {
        AppLock.prompt(activity)
        onPauseOrDispose { }
    }
    SpaceBackground(
        // Swallow touches so nothing behind the lock reacts.
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .glow(Space.Orange, 36.dp, CircleShape, 0.7f)
                    .background(Brush.radialGradient(listOf(Color(0xFFFFE2B8), Space.Orange, Color(0xFFE07A2E))), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Fingerprint, null, tint = Color(0xFF3A1E00), modifier = Modifier.size(44.dp))
            }
            Text("Orbit ist gesperrt", style = MaterialTheme.typography.titleLargeEmphasized, color = Space.Text)
            GlowButton("Entsperren", { AppLock.prompt(activity) })
        }
    }
}
