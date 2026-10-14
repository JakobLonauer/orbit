package io.github.thelony97.orbit.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.store.StoreUpgrade
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.theme.Space
import java.io.File

/**
 * Shown instead of onboarding when stored data exists but could not be read. Saving and
 * backups are blocked meanwhile, so nothing gets overwritten; the copy can be saved out.
 */
@Composable
fun LoadErrorScreen(error: Throwable) {
    val ctx = LocalContext.current
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val dir = ctx.filesDir
            // The newest data first: the file that failed, then the copies from before each
            // conversion (schema 2, then 1.0.7).
            val source = dir.listFiles { f -> f.name.startsWith("orbit-broken-") }?.maxByOrNull { it.lastModified() }
                ?: File(dir, "orbit.json").takeIf { it.exists() }
                ?: File(dir, StoreUpgrade.BACKUP_V2).takeIf { it.exists() }
                ?: File(dir, StoreUpgrade.BACKUP)
            runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
            }
        }
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.Start,
    ) {
        Text("Daten konnten nicht geladen werden", style = MaterialTheme.typography.headlineSmallEmphasized, color = Space.Text)
        Text(
            "Nichts wurde überschrieben. Orbit nicht löschen oder neu installieren.",
            style = MaterialTheme.typography.bodyMedium,
            color = Space.TextDim,
        )
        Text(
            error.message ?: error.javaClass.simpleName,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = Space.TextFaint,
        )
        GlowButton("Kopie sichern", { saver.launch("orbit-kopie.json") }, icon = Icons.Rounded.Save)
    }
}
