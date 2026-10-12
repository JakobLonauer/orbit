package io.github.thelony97.orbit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.security.AppLock
import io.github.thelony97.orbit.update.UpdateState
import io.github.thelony97.orbit.update.Updates
import kotlinx.coroutines.launch

/** Shows an available update with an install button and download progress. */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier, showWhenIdle: Boolean = false) {
    val state by Updates.state.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val visible = state is UpdateState.Available || state is UpdateState.Downloading ||
        state is UpdateState.Installing || (showWhenIdle && state is UpdateState.Failed)
    if (!visible) return
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                val title = when (val s = state) {
                    is UpdateState.Available -> "Orbit ${s.info.versionName} ist da"
                    is UpdateState.Downloading -> "Lade ${s.info.versionName} …"
                    is UpdateState.Installing -> "Installiere …"
                    is UpdateState.Failed -> "Update fehlgeschlagen: ${s.message}"
                    else -> ""
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
            }
            when (val s = state) {
                is UpdateState.Available -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (Updates.canInstall(ctx)) {
                            scope.launch { Updates.downloadAndInstall(ctx, s.info) }
                        } else {
                            AppLock.allowExternal()
                            ctx.startActivity(Updates.installPermissionIntent(ctx))
                        }
                    }) { Text(if (Updates.canInstall(ctx)) "Jetzt installieren" else "Installieren erlauben") }
                    TextButton(onClick = { Updates.reset() }) { Text("Später") }
                }
                is UpdateState.Downloading -> LinearWavyProgressIndicator(
                    progress = { s.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                is UpdateState.Installing -> LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                else -> Unit
            }
        }
    }
}
