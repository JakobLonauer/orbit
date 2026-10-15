package io.github.thelony97.orbit.ui.list

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Spacing
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.domain.today
import io.github.thelony97.orbit.ui.Photos
import io.github.thelony97.orbit.ui.components.GhostButton
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.SpaceField
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.launch

/** The event running today, or null (PLAN 9 E24: it ends with the day by itself). */
fun activeEvent(data: OrbitData): String? = data.settings.eventName?.takeIf { data.settings.eventDay == today().toString() }

fun startEvent(name: String) = OrbitRepository.editSettings { it.copy(eventName = name, eventDay = today().toString()) }

/** The bar while an event runs: „Hochzeit Tom · 4 neu“, „+ Mensch“ opens the quick capture, × ends it. */
@Composable
fun EventBar(name: String, data: OrbitData, modifier: Modifier = Modifier) {
    var capture by remember { mutableStateOf(false) }
    val met = data.contacts.count { it.deletedAt == null && it.howWeMet.startsWith(name) }
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 10.dp).clip(CircleShape)
            .background(Space.Orange.copy(alpha = 0.14f)).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Celebration, null, tint = Space.Orange, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(name + if (met > 0) " · $met neu" else "", style = MaterialTheme.typography.labelLarge, color = Space.Text, modifier = Modifier.weight(1f))
        GlowButton("+ Mensch", { capture = true }, color = Space.Orange)
        Text(
            "×", style = MaterialTheme.typography.titleLarge, color = Space.TextDim,
            modifier = Modifier.clip(CircleShape).clickable { OrbitRepository.editSettings { it.copy(eventName = null, eventDay = null) } }.padding(horizontal = 12.dp),
        )
    }
    if (capture) EventCaptureSheet(name, onDismiss = { capture = false })
}

/**
 * Schnellerfassung (PLAN 9 E24): photo, name, one sentence – „kennengelernt bei Hochzeit Tom“
 * goes in by itself. Speichern keeps the sheet open for the next person.
 */
@Composable
fun EventCaptureSheet(event: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val name = rememberTextFieldState()
    val sentence = rememberTextFieldState()
    var photo by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var saved by remember { mutableIntStateOf(0) }
    val target = remember {
        val dir = java.io.File(ctx.cacheDir, "camera").apply { mkdirs() }
        androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".share", java.io.File(dir, "event.jpg"))
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch { photo = Photos.saveFromUri(ctx, target) }
    }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun save() {
        val full = name.text.toString().trim()
        if (full.isEmpty()) return
        val first = full.substringBefore(' ')
        val last = full.substringAfter(' ', "").trim()
        val day = today()
        val c = Contact(
            newId(), firstName = first, lastName = last, photo = photo,
            howWeMet = "$event · ${day.dayOfMonth}.${day.monthValue}.${day.year}", notes = sentence.text.toString().trim(),
            createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(),
        )
        OrbitRepository.edit { d -> d.copy(contacts = d.contacts + c) }
        haptics.perform(Haptic.Confirm)
        name.clearText()
        sentence.clearText()
        photo = null
        saved++
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Space.PanelHigh) {
        Column(
            modifier.fillMaxWidth().padding(horizontal = Spacing.L).navigationBarsPadding().padding(bottom = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(event + if (saved > 0) " · $saved" else "", style = MaterialTheme.typography.titleMediumEmphasized, color = Space.Orange)
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(Space.Panel).clickable {
                    io.github.thelony97.orbit.security.AppLock.allowExternal()
                    camera.launch(target)
                },
                contentAlignment = Alignment.Center,
            ) {
                val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, photo) {
                    val p = photo
                    value = p?.let { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { android.graphics.BitmapFactory.decodeFile(OrbitRepository.photoFile(it).path) } }
                }
                val shown = bmp
                if (shown != null) Image(shown.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(Icons.Rounded.PhotoCamera, "Foto", tint = Space.Orange, modifier = Modifier.size(32.dp))
            }
            SpaceField(name, "Name", placeholder = "Lena Maier")
            SpaceField(sentence, "Ein Satz", singleLine = false, placeholder = "Cousine der Braut, Tierärztin")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GhostButton("Fertig", onDismiss)
                Spacer(Modifier.weight(1f))
                GlowButton("Speichern", { save() }, color = Space.Orange, enabled = name.text.isNotBlank())
            }
        }
    }
}
