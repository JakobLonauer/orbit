package io.github.thelony97.orbit.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.ui.theme.Space

/** One-line glass banner with an icon and an optional action word. */
@Composable
fun Banner(icon: ImageVector, color: Color, text: String, onClick: () -> Unit, action: String? = null, edge: androidx.compose.ui.unit.Dp = 16.dp) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = edge, vertical = io.github.thelony97.orbit.core.designsystem.Spacing.XS)
            .glass(io.github.thelony97.orbit.core.designsystem.Corners.Card, tint = Space.Panel, border = color.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(horizontal = io.github.thelony97.orbit.core.designsystem.Spacing.M, vertical = io.github.thelony97.orbit.core.designsystem.Spacing.M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(io.github.thelony97.orbit.core.designsystem.Spacing.M))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Space.Text, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(action, style = MaterialTheme.typography.labelLarge, color = color)
        } else {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Space.TextDim)
        }
    }
}
