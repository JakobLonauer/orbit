package io.github.thelony97.orbit.ui.call

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.github.thelony97.orbit.ui.components.GlowButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Motion
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.core.domain.CallerCard
import io.github.thelony97.orbit.core.domain.CallerNotice
import io.github.thelony97.orbit.ui.components.CheckDot
import io.github.thelony97.orbit.ui.components.GhostButton
import io.github.thelony97.orbit.ui.components.SpaceBackground
import io.github.thelony97.orbit.ui.components.glow
import io.github.thelony97.orbit.ui.components.rememberPhoto
import io.github.thelony97.orbit.ui.theme.Grotesk
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

/** What the call screen shows (PLAN 3.12). Built from the caller index; no telecom types here. */
data class CallUi(
    val number: String,
    val card: CallerCard?,
    val incoming: Boolean = true,
    val active: Boolean = false,
    /** Seconds since the call was answered. */
    val elapsed: Long = 0,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val onHold: Boolean = false,
    /** Phone unlocked: notes, topics and customer number may show (PLAN 3.12 „App-Sperre blockiert Anrufe nie“). */
    val phoneUnlocked: Boolean = false,
    val doneTopics: Set<String> = emptySet(),
    /** Hung up: the screen stays for the note line (PLAN 3.12 „Danach“). */
    val ended: Boolean = false,
    /** Outgoing, not answered yet. */
    val dialing: Boolean = false,
    /** Dual SIM „ask every time“: pick one, or the call cannot start. */
    val accounts: List<String> = emptyList(),
    /** Unknown number that called before: „3 Anrufe · zuletzt vor 2 Tagen“ (PLAN 3.12). */
    val unknownLine: String? = null,
    /** Where the sound goes; [routeName] is a Bluetooth device's name. */
    val route: AudioRoute = AudioRoute.EARPIECE,
    val routeName: String = "",
    /** More than earpiece and speaker to choose from (headset, Bluetooth). */
    val moreRoutes: Boolean = false,
    /** Orbit may block numbers (only as the phone app). */
    val canBlock: Boolean = false,
    /** Anklopfen: the other call, „Tom Huber · gehalten“; [canMerge] when both can become one. */
    val otherCall: String? = null,
    val canMerge: Boolean = false,
)

enum class AudioRoute { EARPIECE, SPEAKER, WIRED, BLUETOOTH }

interface CallActions {
    fun answer() {}
    fun decline() {}
    /** Ablehnen mit SMS-Vorlage (PLAN 3.12): declines and sends [text] to the caller. */
    fun declineWithSms(text: String) {}
    fun remindLater() {}
    fun hangUp() {}
    fun toggleMute() {}
    fun toggleSpeaker() {}
    fun toggleHold() {}
    fun dialpad() {}
    /** A key of the in-call keypad (DTMF). */
    fun dtmf(digit: Char) {}
    /** Which SIM places this call. */
    fun pickAccount(index: Int) {}
    /** Nummer sperren (PLAN 3.12): into the system's block list, then decline. */
    fun block() {}
    /** Two calls: switch to the held one, or merge them into a conference. */
    fun swap() {}
    fun merge() {}
    fun toggleTopic(topic: String) {}
    /** Archived caller (PLAN 3.11): back into the list with one tap. */
    fun unarchive() {}
    /** After the call: the note goes into this call's Verlauf entry. */
    fun saveNote(text: String) {}
    /** Every change of the note line, so nothing typed is lost when the screen goes away. */
    fun noteDraft(text: String) {}
    fun close() {}
}

/** The caller's colour: a stable pick per contact, violet for unknown numbers. */
private fun callerColor(card: CallerCard?, unlocked: Boolean): Color {
    if (card == null) return Space.Violet
    // Archived: a distant, colourless star (PLAN 1.3) – only on an unlocked phone, the lock
    // screen must not tell that a caller is archived (name, photo, role only).
    if (card.archived && unlocked) return Space.TextDim
    if (card.org) return Space.Blue
    val palette = listOf(Color(0xFFA78BFF), Color(0xFF5EE6CF), Color(0xFFFFB45E), Color(0xFF6FB7FF), Color(0xFFFF8FB1), Color(0xFF9BE37A))
    return palette[(card.contactId.hashCode() and 0x7FFFFFFF) % palette.size]
}

@Composable
fun CallScreen(
    ui: CallUi,
    actions: CallActions,
    now: Long = System.currentTimeMillis(),
    /** Opens with the SMS templates shown (screenshots). */
    smsOpen: Boolean = false,
) {
    var templates by remember { mutableStateOf(smsOpen) }
    var keys by remember { mutableStateOf(false) }
    val color = callerColor(ui.card, ui.phoneUnlocked)
    val notice = ui.card?.let { CallerNotice.of(it, now, java.time.ZoneId.systemDefault()) }
    SpaceBackground(nebula = { color.copy(alpha = 1f) }, camera = rememberTiltCamera()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            CallerPortrait(ui.card, color, faded = ui.card?.archived == true && ui.phoneUnlocked)
            Spacer(Modifier.height(18.dp))
            // The name alone; the archive hint sits in the details with „Zurückholen“.
            Text(
                notice?.publicTitle ?: ui.number,
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = Space.Text,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = when {
                ui.ended -> "Beendet · " + timer(ui.elapsed)
                ui.dialing -> "Wählt …"
                ui.active -> listOfNotNull(timer(ui.elapsed), "gehalten".takeIf { ui.onHold }).joinToString(" · ")
                notice != null -> notice.publicLine.ifBlank { ui.number }
                else -> ui.unknownLine ?: "Unbekannt"
            }
            Text(
                sub,
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                color = if (ui.active && !ui.ended) Space.Teal else Space.TextDim,
                modifier = Modifier.padding(top = 4.dp),
            )
            // Anklopfen while ringing: who is waiting on the other line (held when answering).
            if (!ui.active && ui.otherCall != null) {
                Text(
                    "Anklopfen · " + ui.otherCall,
                    style = MaterialTheme.typography.labelLarge,
                    color = Space.Teal,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            // Details only on an unlocked phone (PLAN 3.12); the lock screen sees name and role.
            // The templates take the details' place, so everything fits without scrolling.
            if (ui.phoneUnlocked && notice != null && !(templates && !ui.active) && !(keys && ui.active && !ui.ended)) {
                Spacer(Modifier.height(16.dp))
                Details(notice, ui, actions)
            }
            Spacer(Modifier.weight(1f))
            if (ui.ended) {
                // The note needs a contact to land on, and an unlocked phone.
                AfterCall(ui.phoneUnlocked && ui.card != null, actions)
            } else if (ui.active) {
                ui.otherCall?.let { other ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Space.Panel.copy(alpha = 0.8f))
                            .border(1.dp, Space.Line, RoundedCornerShape(16.dp))
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(other, style = MaterialTheme.typography.bodyMedium, color = Space.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (ui.canMerge) GhostButton("Zusammen", actions::merge)
                        GlowButton("Wechseln", actions::swap, color = Space.Teal)
                    }
                    Spacer(Modifier.height(20.dp))
                }
                if (ui.accounts.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ui.accounts.forEachIndexed { i, label -> GlowButton(label, { actions.pickAccount(i) }, color = Space.Teal) }
                    }
                    Spacer(Modifier.height(20.dp))
                }
                if (keys) {
                    Keypad(onKey = actions::dtmf)
                    Spacer(Modifier.height(20.dp))
                }
                ActiveControls(ui, actions, keys) { keys = !keys; actions.dialpad() }
            } else {
                if (templates) {
                    SmsTemplates { text -> templates = false; actions.declineWithSms(text) }
                    Spacer(Modifier.height(12.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    SmallAction(Icons.Rounded.Sms, "SMS", on = templates) { templates = !templates }
                    SmallAction(Icons.Rounded.Schedule, "Später", onClick = actions::remindLater)
                    // Unknown numbers only: block and decline, with a second tap to be sure.
                    if (ui.card == null && ui.canBlock) {
                        var sure by remember { mutableStateOf(false) }
                        SmallAction(Icons.Rounded.Block, if (sure) "Sicher?" else "Sperren", on = sure) {
                            if (sure) actions.block() else sure = true
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                AnswerOrbit(color, onAnswer = actions::answer, onDecline = actions::decline)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Fixed for now; editable templates come with the settings for the phone role. */
private val SMS_TEMPLATES = listOf(
    "Kann gerade nicht – ich rufe zurück.",
    "Bin im Termin, melde mich später.",
    "Schreib mir bitte kurz.",
)

@Composable
private fun SmsTemplates(onPick: (String) -> Unit) {
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SMS_TEMPLATES.forEach { t ->
            Text(
                t,
                style = MaterialTheme.typography.bodyLarge,
                color = Space.Text,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Space.Panel.copy(alpha = 0.8f))
                    .border(1.dp, Space.Line, RoundedCornerShape(16.dp))
                    .clickable { haptics.perform(Haptic.Confirm); onPick(t) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

/**
 * After hanging up: one note line into the Verlauf. On a locked phone only „Schließen“ – notes
 * outside the call stay behind the phone's lock (PLAN 3.12).
 */
@Composable
private fun AfterCall(unlocked: Boolean, actions: CallActions, slideIn: Boolean = true) {
    val note = rememberTextFieldState()
    LaunchedEffect(note) { snapshotFlow { note.text.toString() }.collect { actions.noteDraft(it) } }
    // PLAN 1.3: after hanging up the note line glides up with the keyboard already open.
    val shown = remember { androidx.compose.animation.core.MutableTransitionState(!slideIn).apply { targetState = true } }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    if (unlocked && slideIn) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(io.github.thelony97.orbit.core.designsystem.Motion.MEDIUM_MS.toLong())
            runCatching { focus.requestFocus() }
        }
    }
    androidx.compose.animation.AnimatedVisibility(
        visibleState = shown,
        enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(io.github.thelony97.orbit.core.designsystem.Motion.MEDIUM_MS)) { it / 2 } +
            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(io.github.thelony97.orbit.core.designsystem.Motion.MEDIUM_MS)),
    ) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (unlocked) {
            OutlinedTextField(
                state = note,
                placeholder = { Text("Notiz zum Anruf") },
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 1, maxHeightInLines = 4),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            GhostButton("Schließen", actions::close)
            if (unlocked) {
                GlowButton("Speichern", { actions.saveNote(note.text.toString().trim()) }, color = Space.Teal, enabled = note.text.isNotBlank())
            }
        }
    }
    }
}

/**
 * Sternenhimmel mit Neigung (PLAN 1.3): the stars drift a little as the phone tilts. Values go
 * into the camera's draw-phase state only – no recomposition; off in power saving mode.
 */
@Composable
private fun rememberTiltCamera(): io.github.thelony97.orbit.ui.components.SpaceCamera {
    val camera = remember { io.github.thelony97.orbit.ui.components.SpaceCamera() }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        val sm = ctx.getSystemService(android.hardware.SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        val saver = ctx.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true
        if (sm == null || sensor == null || saver) return@DisposableEffect onDispose { }
        var fx = Float.NaN
        var fy = Float.NaN
        var bx = 0f
        var by = 0f
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                if (fx.isNaN()) {
                    fx = e.values[0]; fy = e.values[1]; bx = fx; by = fy
                }
                // Low-pass: calm drift, no jitter from the hand.
                fx += (e.values[0] - fx) * 0.06f
                fy += (e.values[1] - fy) * 0.06f
                camera.pan = ((bx - fx) / 9.81f).coerceIn(-1f, 1f) * 0.25f
                camera.scroll = ((fy - by) / 9.81f).coerceIn(-1f, 1f) * 160f
            }

            override fun onAccuracyChanged(s: android.hardware.Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }
    return camera
}

private fun timer(sec: Long): String = "%d:%02d".format(sec / 60, sec % 60)

@Composable
private fun CallerPortrait(card: CallerCard?, color: Color, faded: Boolean) {
    val photo = rememberPhoto(card?.photo)
    Box(
        Modifier
            .size(132.dp)
            .glow(color, 40.dp, CircleShape, 0.55f)
            .clip(CircleShape)
            // Inside the clip, so the glow outside is not cut by the layer.
            .then(
                if (faded) Modifier.graphicsLayer {
                    renderEffect = android.graphics.RenderEffect.createColorFilterEffect(
                        android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(0f) }),
                    ).asComposeRenderEffect()
                } else Modifier,
            )
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.6f), color.copy(alpha = 0.22f))))
            .border(2.5.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(photo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            val initials = card?.name?.split(' ')?.filter { it.isNotBlank() }?.take(2)?.joinToString("") { it.take(1).uppercase() }
            Text(initials?.ifBlank { null } ?: "?", fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 46.sp, color = Space.Text)
        }
    }
}

@Composable
private fun Details(notice: CallerNotice, ui: CallUi, actions: CallActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(io.github.thelony97.orbit.core.designsystem.Corners.Card)
            .background(Space.Panel.copy(alpha = 0.8f))
            .border(1.dp, Space.Line, io.github.thelony97.orbit.core.designsystem.Corners.Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (ui.card?.archived == true) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Im Archiv", style = MaterialTheme.typography.labelLarge, color = Space.TextDim, modifier = Modifier.weight(1f))
                GhostButton("Zurückholen", actions::unarchive, icon = Icons.Rounded.Unarchive, color = Space.Teal)
            }
        }
        if (notice.line.isNotBlank() && notice.line != notice.publicLine) {
            Text(notice.line.removePrefix(notice.publicLine).removePrefix(" · "), style = MaterialTheme.typography.labelLarge, color = Space.TextDim)
        }
        ui.card?.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Space.Text) }
        // Topics can be ticked off during the call (PLAN 3.12 „Themen abhaken“).
        ui.card?.topics?.forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                CheckDot(t in ui.doneTopics, { actions.toggleTopic(t) })
                Spacer(Modifier.width(10.dp))
                Text(t, style = MaterialTheme.typography.bodyMedium, color = if (t in ui.doneTopics) Space.TextFaint else Space.Text)
            }
        }
        // Anstehender Anlass (PLAN 3.12), worked out now – not when the index was written.
        notice.occasion?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = Space.Orange) }
        ui.card?.customerNo?.takeIf { it.isNotBlank() }?.let {
            // Copy button also here (PLAN 1.4): the number is what the hotline asks for.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Kundennummer $it", style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"), color = Space.TextDim, modifier = Modifier.weight(1f))
                io.github.thelony97.orbit.ui.detail.CopyButton(it, "Kundennummer")
            }
        }
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean = false, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (on) Space.Teal.copy(alpha = 0.22f) else Space.PanelHigh)
                .border(1.dp, if (on) Space.Teal.copy(alpha = 0.6f) else Space.Line, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = if (on) Space.Teal else Space.TextDim) }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Space.TextFaint, modifier = Modifier.padding(top = 4.dp))
    }
}

/**
 * Annehmen: pull the planet into the sun (big target, haptic when it snaps in). Ablehnen: flick
 * it away. Positions are read only while drawing.
 */
@Composable
private fun AnswerOrbit(color: Color, onAnswer: () -> Unit, onDecline: () -> Unit) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var captured by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val hint = rememberInfiniteTransition(label = "hint").animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "hint")
    BoxWithConstraints(Modifier.fillMaxWidth().height(250.dp)) {
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val sun = Offset(w / 2f, with(density) { 52.dp.toPx() })
        val home = Offset(w / 2f, h - with(density) { 52.dp.toPx() })
        val capture = with(density) { 64.dp.toPx() }
        val tracker = remember { VelocityTracker() }
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onDrag = { change, drag ->
                            if (done) return@detectDragGestures
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            scope.launch { offset.snapTo(offset.value + drag) }
                            val pos = home + offset.value + drag
                            val inSun = hypot(pos.x - sun.x, pos.y - sun.y) < capture
                            if (inSun != captured) {
                                captured = inSun
                                if (inSun) haptics.perform(Haptic.Threshold)
                            }
                        },
                        onDragEnd = {
                            if (done) return@detectDragGestures
                            val v = tracker.calculateVelocity()
                            when {
                                captured -> {
                                    done = true
                                    haptics.perform(Haptic.Confirm)
                                    scope.launch {
                                        offset.animateTo(sun - home, tween(Motion.FAST_MS, easing = Motion.Emphasized))
                                        onAnswer()
                                    }
                                }
                                // A flick sideways or down sends the planet away: decline.
                                abs(v.x) > 2200f || v.y > 2200f -> {
                                    done = true
                                    haptics.perform(Haptic.Reject)
                                    scope.launch {
                                        val dir = Offset(v.x, v.y.coerceAtLeast(0f)).let { it / (it.getDistance().coerceAtLeast(1f)) }
                                        offset.animateTo(offset.value + dir * w, tween(Motion.MEDIUM_MS))
                                        onDecline()
                                    }
                                }
                                else -> scope.launch { offset.animateTo(Offset.Zero, spring(dampingRatio = 0.6f, stiffness = 500f)) }
                            }
                        },
                    )
                },
        ) {
            val planet = home + offset.value
            val near = (1f - (hypot(planet.x - sun.x, planet.y - sun.y) / (home.y - sun.y))).coerceIn(0f, 1f)
            // Sun: grows brighter as the planet approaches.
            drawCircle(Brush.radialGradient(listOf(Space.Orange.copy(alpha = 0.55f + 0.35f * near), Space.Orange.copy(alpha = 0f)), sun, capture * (1.4f + 0.4f * near)), radius = capture * (1.4f + 0.4f * near), center = sun)
            drawCircle(Space.Orange, radius = 26.dp.toPx(), center = sun)
            drawCircle(Color(0xFFFFE2B8), radius = 12.dp.toPx(), center = sun)
            // Orbit path from planet to sun with a travelling hint dot.
            drawLine(Space.Line, home, sun, strokeWidth = 1.dp.toPx())
            if (offset.value == Offset.Zero) {
                val t = hint.value
                val p = Offset(home.x, home.y + (sun.y - home.y) * t)
                drawCircle(color.copy(alpha = 0.5f * (1f - t)), radius = 3.dp.toPx(), center = p)
            }
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.6f), color.copy(alpha = 0f)), planet, 30.dp.toPx()), radius = 30.dp.toPx(), center = planet)
            drawCircle(color, radius = 18.dp.toPx(), center = planet)
            drawCircle(Space.Text.copy(alpha = 0.9f), radius = 6.dp.toPx(), center = planet - Offset(4.dp.toPx(), 4.dp.toPx()))
            if (captured) drawCircle(Space.Text.copy(alpha = 0.6f), radius = capture, center = sun, style = Stroke(1.5.dp.toPx()))
        }
    }
}

@Composable
private fun ActiveControls(ui: CallUi, actions: CallActions, keys: Boolean, onKeys: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleOrb(Icons.Rounded.MicOff, "Stumm", ui.muted, actions::toggleMute)
            // With a headset or Bluetooth the button names where the sound goes and moves it on.
            val (icon, label) = when {
                !ui.moreRoutes -> Icons.AutoMirrored.Rounded.VolumeUp to "Lautsprecher"
                ui.route == AudioRoute.BLUETOOTH -> Icons.Rounded.Bluetooth to ui.routeName.ifBlank { "Bluetooth" }
                ui.route == AudioRoute.SPEAKER -> Icons.AutoMirrored.Rounded.VolumeUp to "Lautsprecher"
                ui.route == AudioRoute.WIRED -> Icons.Rounded.Headphones to "Kopfhörer"
                else -> Icons.Rounded.PhoneInTalk to "Hörer"
            }
            ToggleOrb(icon, label, ui.speaker || ui.route == AudioRoute.BLUETOOTH, actions::toggleSpeaker)
            ToggleOrb(Icons.Rounded.Pause, "Halten", ui.onHold, actions::toggleHold)
            ToggleOrb(Icons.Rounded.Dialpad, "Tasten", keys, onKeys)
        }
        Box(
            Modifier.size(72.dp).glow(Space.Coral, 22.dp, CircleShape, 0.6f).clip(CircleShape).background(Space.Coral).clickable(onClick = actions::hangUp),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.CallEnd, "Auflegen", tint = Space.Void, modifier = Modifier.size(32.dp)) }
    }
}

@Composable
private fun ToggleOrb(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (on) Space.Text else Space.PanelHigh)
                .border(1.dp, if (on) Space.Text else Space.Line, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = if (on) Space.Void else Space.Text) }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Space.TextDim, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
    }
}
