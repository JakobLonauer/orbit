package io.github.thelony97.orbit.ui.probe

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Test-build probe: shared element "avatar flies from list row into contact header" (PLAN W3).
 * Used by the screenshot test; the real transition is built in R1a on top of Navigation3.
 */
@Composable
fun SharedAvatarProbe(initiallyOpen: Boolean = false) {
    var open by remember { mutableStateOf(initiallyOpen) }
    SharedTransitionLayout {
        AnimatedContent(targetState = open, label = "probe") { isOpen ->
            val avatar = Modifier.sharedElement(
                rememberSharedContentState(key = "avatar"),
                animatedVisibilityScope = this@AnimatedContent,
            )
            if (!isOpen) {
                Box(
                    Modifier
                        .padding(16.dp)
                        .clickable { open = true },
                ) {
                    Box(
                        avatar
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clickable { open = false },
                ) {
                    Box(
                        avatar
                            .size(120.dp)
                            .clip(RoundedCornerShape(36.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Text("Kontakt", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    }
}
