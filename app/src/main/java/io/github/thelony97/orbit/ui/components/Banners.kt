package io.github.thelony97.orbit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.security.AppLock
import io.github.thelony97.orbit.ui.theme.Space
import io.github.thelony97.orbit.update.UpdateState
import io.github.thelony97.orbit.update.Updates
import kotlinx.coroutines.launch

/** Compact strip for an available update, with install button and download progress. */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier, showWhenIdle: Boolean = false) {
    val state by Updates.state.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val visible = state is UpdateState.Available || state is UpdateState.Downloading ||
        state is UpdateState.Installing || (showWhenIdle && state is UpdateState.Failed)
    if (!visible) return
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .glow(Space.Violet, 16.dp, RoundedCornerShape(18.dp), 0.35f)
            .glass(RoundedCornerShape(18.dp), tint = Space.PanelHigh, border = Space.Violet.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SystemUpdate, null, tint = Space.Violet, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            val title = when (val s = state) {
                is UpdateState.Available -> "Orbit ${s.info.versionName} ist da"
                is UpdateState.Downloading -> "Lade ${s.info.versionName} …"
                is UpdateState.Installing -> "Installiere …"
                is UpdateState.Failed -> "Update fehlgeschlagen: ${s.message}"
                else -> ""
            }
            Text(title, style = MaterialTheme.typography.titleSmallEmphasized, color = Space.Text, modifier = Modifier.weight(1f))
            val s = state
            if (s is UpdateState.Available) {
                GlowButton(
                    text = if (Updates.canInstall(ctx)) "Installieren" else "Erlauben",
                    onClick = {
                        if (Updates.canInstall(ctx)) {
                            scope.launch { Updates.downloadAndInstall(ctx, s.info) }
                        } else {
                            AppLock.allowExternal()
                            ctx.startActivity(Updates.installPermissionIntent(ctx))
                        }
                    },
                )
            }
        }
        when (val s = state) {
            is UpdateState.Downloading -> LinearWavyProgressIndicator(
                progress = { s.progress },
                modifier = Modifier.fillMaxWidth(),
                color = Space.Violet,
                trackColor = Space.PanelHighest,
            )
            is UpdateState.Installing -> LinearWavyProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Space.Violet,
                trackColor = Space.PanelHighest,
            )
            else -> Unit
        }
    }
}
