package io.github.thelony97.orbit.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.thelony97.orbit.data.Mutations
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.notify.DigestScheduler
import io.github.thelony97.orbit.security.AppLock
import io.github.thelony97.orbit.sync.Sync
import io.github.thelony97.orbit.ui.Permissions
import io.github.thelony97.orbit.ui.detail.rhythmPresets
import io.github.thelony97.orbit.util.Fmt
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import io.github.thelony97.orbit.ui.components.GhostButton
import io.github.thelony97.orbit.ui.components.GlowBar
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.SpaceBackground
import io.github.thelony97.orbit.ui.components.SpaceChip
import io.github.thelony97.orbit.ui.components.glass
import io.github.thelony97.orbit.ui.components.glow
import io.github.thelony97.orbit.ui.edit.spaceSwitch
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val STEPS = 5

@Composable
fun OnboardingScreen(data: OrbitData) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    fun finish() {
        AppLock.unlock()
        scope.launch {
            OrbitRepository.update { d -> d.copy(settings = d.settings.copy(onboardingDone = true)) }
            OrbitRepository.flush()
            DigestScheduler.schedule(ctx)
            Sync.requestNow(ctx)
        }
    }

    SpaceBackground {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 22.dp, vertical = 16.dp)) {
        GlowBar((step + 1f) / STEPS, Space.Orange)
        Spacer(Modifier.size(16.dp))
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState > initialState
                slideInHorizontally { if (forward) it else -it } togetherWith slideOutHorizontally { if (forward) -it else it }
            },
            label = "onboarding",
            modifier = Modifier.weight(1f),
        ) { current ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (current) {
                    0 -> Welcome()
                    1 -> PermissionStep()
                    2 -> GroupStep(data)
                    3 -> BackupStep(data)
                    else -> LockStep(data)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) GhostButton("Zurück", { step-- })
            Spacer(Modifier.weight(1f))
            GlowButton(
                when (step) {
                    0 -> "Los geht's"
                    STEPS - 1 -> "Fertig"
                    else -> "Weiter"
                },
                { if (step < STEPS - 1) step++ else finish() },
            )
        }
    }
    }
}

@Composable
private fun Title(text: String, sub: String) {
    Text(text, style = MaterialTheme.typography.headlineMediumEmphasized, color = Space.Text)
    Spacer(Modifier.size(8.dp))
    Text(sub, style = MaterialTheme.typography.bodyMedium, color = Space.TextDim)
    Spacer(Modifier.size(20.dp))
}

@Composable
private fun Welcome() {
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { seconds = (it - start) / 1e9f }
    }
    Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
        listOf(70f, 106f, 142f).forEach { r ->
            Box(
                Modifier
                    .size((r * 2).dp)
                    .border(1.dp, Space.Line, CircleShape),
            )
        }
        Box(
            Modifier
                .size(64.dp)
                .graphicsLayer {
                    val pulse = 1f + 0.05f * sin(seconds * 1.6f)
                    scaleX = pulse
                    scaleY = pulse
                }
                .glow(Space.Orange, 34.dp, CircleShape, 0.8f)
                .background(Brush.radialGradient(listOf(Color(0xFFFFE2B8), Space.Orange, Color(0xFFE07A2E))), CircleShape),
        )
        val colors = listOf(Space.Violet, Space.Teal, Space.Coral, Space.Blue, Space.Orange)
        colors.forEachIndexed { i, c ->
            val radius = listOf(70f, 106f, 142f, 106f, 142f)[i]
            Box(
                Modifier
                    .offset {
                        val a = seconds * (0.8f - (i % 3) * 0.2f) + i * 2.1f
                        IntOffset(
                            (cos(a) * radius * density).roundToInt(),
                            (sin(a) * radius * density).roundToInt(),
                        )
                    }
                    .size((16 - i * 2).dp)
                    .glow(c, 10.dp, CircleShape, 0.8f)
                    .background(c, CircleShape),
            )
        }
    }
    Title(
        "Willkommen bei Orbit",
        "Dein persönliches Beziehungs-CRM: Wer ist dir wichtig, wann hast du zuletzt gesprochen, und was wolltest du beim nächsten Mal ansprechen? " +
            "Alles bleibt auf deinem Handy.",
    )
}

@Composable
private fun PermissionStep() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var askedOnce by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        tick++
        askedOnce = true
    }
    val states = remember(tick) { Permissions.core.map { it to Permissions.granted(ctx, it.permission) } }

    Title("Berechtigungen", "Damit Orbit Anrufe und SMS automatisch in den Verlauf schreiben und dich erinnern kann.")
    states.forEach { (p, granted) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                null,
                tint = if (granted) Space.Teal else Space.TextFaint,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text(p.title, style = MaterialTheme.typography.titleSmallEmphasized, color = Space.Text)
                Text(p.why, style = MaterialTheme.typography.bodySmall, color = Space.TextDim)
            }
        }
    }
    Spacer(Modifier.size(12.dp))
    val missing = states.filterNot { it.second }
    if (missing.isNotEmpty()) {
        GlowButton("Alle erlauben", { launcher.launch(missing.map { it.first.permission }.toTypedArray()) })
    }
    if (askedOnce && missing.isNotEmpty()) {
        Spacer(Modifier.size(16.dp))
        Column(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp), border = Space.Orange.copy(alpha = 0.5f)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(Permissions.RESTRICTED_HELP, style = MaterialTheme.typography.bodySmall, color = Space.TextDim)
            GhostButton("App-Info öffnen", {
                AppLock.allowExternal()
                ctx.startActivity(Permissions.appInfoIntent(ctx))
            })
        }
    }
}

@Composable
private fun GroupStep(data: OrbitData) {
    Title("Deine Gruppen", "Jede Gruppe hat einen Rhythmus, wie oft du dich melden möchtest. Du kannst alles später ändern.")
    data.groups.forEach { g ->
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).glass(RoundedCornerShape(20.dp))) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).glow(Color(g.color), 6.dp).background(Color(g.color), CircleShape))
                    Text(g.name, style = MaterialTheme.typography.titleSmallEmphasized, color = Space.Text, modifier = Modifier.padding(start = 10.dp).weight(1f))
                    Icon(
                        Icons.Rounded.Close, "Entfernen",
                        tint = Space.TextFaint,
                        modifier = Modifier.size(30.dp).clip(CircleShape).clickable { OrbitRepository.edit { d -> Mutations.deleteGroup(d, g.id) } }.padding(6.dp),
                    )
                }
                FlowRow(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rhythmPresets.take(6).forEach { days ->
                        SpaceChip(
                            Fmt.rhythm(days),
                            g.rhythmDays == days,
                            { OrbitRepository.edit { d -> Mutations.upsertGroup(d, g.copy(rhythmDays = days)) } },
                            accent = Color(g.color),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupStep(data: OrbitData) {
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            OrbitRepository.editSettings { it.copy(backupTreeUri = uri.toString()) }
        }
    }
    Title(
        "Backup",
        "Orbit speichert einmal pro Woche eine Sicherung in einen Ordner deiner Wahl – am besten einen Google-Drive-Ordner, " +
            "dann überlebt sie auch einen Handywechsel.",
    )
    val folder = data.settings.backupTreeUri?.let {
        runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(it)).substringAfter(':') }.getOrNull()
    }
    if (folder != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = Space.Teal, modifier = Modifier.size(20.dp))
            Text("Ordner: ${folder.ifBlank { "Hauptordner" }}", style = MaterialTheme.typography.bodyMedium, color = Space.Text, modifier = Modifier.padding(start = 10.dp))
        }
        Spacer(Modifier.size(12.dp))
    }
    GlowButton(if (folder == null) "Ordner wählen" else "Anderen Ordner wählen", {
        AppLock.allowExternal()
        picker.launch(null)
    }, color = Space.Blue)
    Spacer(Modifier.size(8.dp))
    Text(
        "Kannst du auch später in den Einstellungen machen.",
        style = MaterialTheme.typography.bodySmall,
        color = Space.TextFaint,
    )
}

@Composable
private fun LockStep(data: OrbitData) {
    val ctx = LocalContext.current
    val available = remember { AppLock.isAvailable(ctx) }
    Title("App-Sperre", "Orbit sperrt sich sofort, wenn du die App verlässt, und öffnet sich mit Fingerabdruck, Gesicht oder PIN.")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (available) "Sperre aktiv" else "Keine Displaysperre eingerichtet – Sperre nicht möglich",
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = Space.Text,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = data.settings.lockEnabled && available,
            onCheckedChange = { v -> OrbitRepository.editSettings { it.copy(lockEnabled = v) } },
            enabled = available,
            colors = spaceSwitch(),
        )
    }
    Spacer(Modifier.size(24.dp))
    Text(
        "Tipp: Leg dir das Orbit-Widget auf den Startbildschirm – es zeigt, wer heute fällig ist.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
    )
}
