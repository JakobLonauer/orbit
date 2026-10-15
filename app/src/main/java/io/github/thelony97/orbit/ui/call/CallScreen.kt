package io.github.thelony97.orbit.ui.call

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.RecordVoiceOver
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.blur
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

/**
 * PLAN 9 B1/B2: an unknown caller as far as Orbit knows it – the number's own line („Linz ·
 * Festnetz“, „0900 · teuer“), whether it costs, and the Herold card (looking / found).
 */
data class UnknownCaller(
    val line: String = "",
    val costs: Boolean = false,
    val searching: Boolean = false,
    val name: String = "",
    val trade: String = "",
    val address: String = "",
) {
    val found: Boolean get() = name.isNotBlank()
}

/** PLAN 9 B4: a macro of the organisation on the line. */
data class MacroChip(val id: String, val label: String)

/** PLAN 9 B4: a macro playing – the key now, a running pause, or the wait for „Weiter“. */
data class MacroPlaying(
    val name: String,
    val key: Char? = null,
    val fromField: Boolean = false,
    val pauseMs: Long = 0,
    val pauseSince: Long = 0,
    val waiting: Boolean = false,
)

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
    /** Note text typed before the screen was left (kept in the call session). */
    val draft: String = "",
    /** Replies for „Ablehnen mit SMS“ (Einstellungen › Mitschreiben); none = no SMS button. */
    val smsTemplates: List<String> = io.github.thelony97.orbit.core.model.Settings.DEFAULT_SMS_TEMPLATES,
    /** Anklopfen: the other call, „Tom Huber · gehalten“; [canMerge] when both can become one. */
    val otherCall: String? = null,
    val canMerge: Boolean = false,
    /** PLAN 9 B1: nobody Orbit knows – what the number and Herold tell. */
    val unknown: UnknownCaller? = null,
    /** PLAN 9 B2: an organisation reached through its number range – „Durchwahl 410“. */
    val extension: String? = null,
    /** PLAN 9 B4: the organisation's macros (as the phone app, while talking). */
    val macros: List<MacroChip> = emptyList(),
    val playing: MacroPlaying? = null,
    /** PLAN 9 B4 after the call: what was typed, as a macro line („1 · 4 s · 3 · Kundennummer“). */
    val recorded: String? = null,
    /** PLAN 9 B7: numbers to read out in the call (label, value) – the caller's and Jakob's own. */
    val dictation: List<Pair<String, String>> = emptyList(),
    /** PLAN 9 B7 „Tastenfeld schreibt mit“: Vorgangsnummern typed in the call. */
    val cases: List<String> = emptyList(),
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
    /** After a call with an unknown number: create it as a person or an organisation (PLAN 3.12). */
    fun createContact(org: Boolean) {}
    /** PLAN 9 B4: play a macro, „Weiter“, take over, keep what was typed. */
    fun playMacro(id: String) {}
    fun macroNext() {}
    fun macroStop() {}
    fun saveMacro() {}
}

/** The caller's colour: a stable pick per contact, violet for unknown numbers. */
private fun callerColor(card: CallerCard?, unlocked: Boolean): Color {
    if (card == null) return Space.Violet
    // Archived: a distant, colourless star (PLAN 1.3) – only on an unlocked phone, the lock
    // screen must not tell that a caller is archived (name, photo, role only).
    if (card.archived && unlocked) return Space.TextDim
    // Same colour as in the list and the dialpad: first group colour, else the stable pick.
    val c = card.color?.let { Color(it) }
        ?: if (card.org) Space.Blue else io.github.thelony97.orbit.ui.components.fallbackColor(card.contactId)
    // The sun is orange: a caller in the same colour would vanish into it.
    return if (c == Space.Orange) Space.Violet else c
}

private enum class CallMode { INCOMING, ACTIVE, ENDED }

@Composable
fun CallScreen(
    ui: CallUi,
    actions: CallActions,
    now: Long = System.currentTimeMillis(),
    /** Opens with the SMS templates shown (screenshots). */
    smsOpen: Boolean = false,
    /** Seconds talked, read only by the timer text – the rest of the screen does not tick (W8). */
    clock: (() -> Long)? = null,
) {
    var templates by remember { mutableStateOf(smsOpen) }
    var keys by remember { mutableStateOf(false) }
    // PLAN 9 B7: a number read out big while talking.
    var dictating by remember { mutableStateOf<Pair<String, String>?>(null) }
    dictating?.let { (label, value) ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { dictating = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            io.github.thelony97.orbit.ui.components.DictationView(label, value, onClose = { dictating = null })
        }
    }
    val color = callerColor(ui.card, ui.phoneUnlocked)
    val notice = remember(ui.card, now / 3_600_000L) { ui.card?.let { CallerNotice.of(it, now, java.time.ZoneId.systemDefault()) } }
    val mode = when {
        ui.ended -> CallMode.ENDED
        ui.active -> CallMode.ACTIVE
        else -> CallMode.INCOMING
    }
    // After hanging up the portrait steps back so the note line and keyboard fit (PLAN 1.3).
    val portrait by androidx.compose.animation.core.animateDpAsState(if (ui.ended) 72.dp else 132.dp, tween(Motion.MEDIUM_MS), label = "portrait")
    val top by androidx.compose.animation.core.animateDpAsState(if (ui.ended) 16.dp else 40.dp, tween(Motion.MEDIUM_MS), label = "top")
    // No tilt at the ear (the screen is dark there anyway) – saves the sensor for the whole call.
    val tilt = !(ui.active && !ui.ended && ui.route == AudioRoute.EARPIECE)
    SpaceBackground(nebula = { color.copy(alpha = 1f) }, camera = rememberTiltCamera(tilt)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(top))
            CallerPortrait(ui.card, color, faded = ui.card?.archived == true && ui.phoneUnlocked, size = portrait)
            Spacer(Modifier.height(16.dp))
            // The name alone; the archive hint sits in the details with „Zurückholen“.
            Text(
                notice?.publicTitle ?: ui.number,
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = Space.Text,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // One line under the name that cross-fades between the states (W2: no hard cuts).
            val sub = when {
                ui.ended -> "Beendet nach " + timer(ui.elapsed)
                ui.accounts.isNotEmpty() -> ui.number
                ui.dialing -> "Wählt …"
                ui.active -> null
                notice != null -> listOfNotNull(ui.extension?.let { "Durchwahl $it" }, notice.publicLine.takeIf { it.isNotBlank() })
                    .joinToString(" · ").ifBlank { ui.number }
                else -> listOfNotNull(ui.unknown?.line, ui.unknownLine).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Unbekannt" }
            }
            // B2: a number that costs (or a possible ping call) is orange from the first moment.
            val costly = ui.card == null && ui.unknown?.costs == true
            androidx.compose.animation.Crossfade(sub == null, animationSpec = tween(Motion.MEDIUM_MS), label = "sub") { live ->
                if (live) {
                    CallTimer(clock ?: { ui.elapsed }, ui.onHold)
                } else {
                    Text(
                        sub.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                        color = when {
                            costly -> Space.Orange
                            ui.dialing -> Space.Teal
                            else -> Space.TextDim
                        },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            // Anklopfen while ringing: who is waiting on the other line (held when answering).
            if (!ui.active && ui.otherCall != null) {
                Text(
                    "Anklopfen · " + ui.otherCall,
                    style = MaterialTheme.typography.labelLarge,
                    color = Space.Teal,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            // The SMS templates (ringing) and the keypad (talking) take the cards' place, so everything
            // fits without scrolling.
            val roomy = !(templates && !ui.active) && !(keys && ui.active && !ui.ended)
            // PLAN 9 B1: who an unknown number belongs to – the card is there at once, the hit sharpens in.
            val herold = ui.unknown?.takeIf { ui.card == null && !ui.ended && (it.searching || it.found) }
            if (herold != null && roomy) {
                Spacer(Modifier.height(16.dp))
                HeroldCard(herold)
            }
            // Details only on an unlocked phone (PLAN 3.12); the lock screen sees name and role.
            if (ui.phoneUnlocked && notice != null && roomy) {
                Spacer(Modifier.height(16.dp))
                Details(notice, ui, actions)
            }
            Spacer(Modifier.weight(1f))
            androidx.compose.animation.AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    (fadeIn(tween(Motion.MEDIUM_MS)) + androidx.compose.animation.slideInVertically(tween(Motion.MEDIUM_MS)) { it / 6 }) togetherWith
                        fadeOut(tween(Motion.FAST_MS))
                },
                contentAlignment = Alignment.BottomCenter,
                label = "mode",
            ) { m ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (m == CallMode.ENDED) {
                // The note needs a contact to land on, and an unlocked phone.
                AfterCall(
                    ui.phoneUnlocked && ui.card != null, actions,
                    unknown = ui.card == null && ui.phoneUnlocked,
                    // B7: a Vorgangsnummer typed in the call is already in the note.
                    initial = ui.draft.ifBlank { ui.cases.firstOrNull()?.let { "Vorgang $it" }.orEmpty() },
                    cases = ui.cases,
                    found = ui.unknown?.takeIf { it.found },
                    recorded = ui.recorded?.takeIf { ui.phoneUnlocked },
                )
            } else if (m == CallMode.ACTIVE) {
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
                    // Nothing is selected yet: equal buttons, none glowing (W5).
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ui.accounts.forEachIndexed { i, label -> GhostButton(label, { actions.pickAccount(i) }) }
                    }
                    Spacer(Modifier.height(20.dp))
                }
                val playing = ui.playing
                if (playing != null) {
                    // PLAN 9 B4 Moment: the keypad plays itself; any key Jakob presses takes over.
                    MacroBar(playing, onNext = actions::macroNext)
                    Spacer(Modifier.height(12.dp))
                    Keypad(onKey = { d -> actions.macroStop(); actions.dtmf(d) }, lit = playing.key, spark = playing.fromField)
                    Spacer(Modifier.height(20.dp))
                } else {
                    // Macros to play (B4) and numbers to read out (B7) – one row of chips.
                    val dictation = if (ui.phoneUnlocked) ui.dictation else emptyList()
                    val chips = ui.macros.isNotEmpty() || dictation.isNotEmpty()
                    if (chips && !ui.ended && !keys) {
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ui.macros.forEach { m ->
                                io.github.thelony97.orbit.ui.components.SpaceChip(
                                    m.label, false, { actions.playMacro(m.id) },
                                    leading = Icons.Rounded.Dialpad,
                                    accent = Space.Teal,
                                )
                            }
                            dictation.forEach { d ->
                                io.github.thelony97.orbit.ui.components.SpaceChip(
                                    d.first, false, { dictating = d },
                                    leading = Icons.Rounded.RecordVoiceOver,
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    if (keys) {
                        Keypad(onKey = actions::dtmf)
                        Spacer(Modifier.height(20.dp))
                    }
                }
                ActiveControls(ui, actions, keys) { keys = !keys; actions.dialpad() }
            } else {
                if (templates) {
                    SmsTemplates(ui.smsTemplates) { text -> templates = false; actions.declineWithSms(text) }
                    Spacer(Modifier.height(12.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    if (ui.smsTemplates.isNotEmpty()) SmallAction(Icons.Rounded.Sms, "SMS", on = templates) { templates = !templates }
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
            }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * PLAN 9 B1 Moment: the caller's card stands in its final shape at once; while Herold is asked a
 * light scans across it, then name and trade sharpen in (blur → sharp, Materialisieren).
 */
@Composable
private fun HeroldCard(u: UnknownCaller) {
    val haptics = rememberHaptics()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sharp by androidx.compose.animation.core.animateFloatAsState(
        if (u.found) 1f else 0f,
        tween(Motion.EMPHASIZED_MS, easing = Motion.Emphasized),
        label = "sharp",
    )
    LaunchedEffect(u.found) { if (u.found) haptics.perform(Haptic.Materialize) }
    val saver = remember { ctx.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true }
    val scan = if (u.found || saver) null else rememberInfiniteTransition(label = "scan").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1_100, easing = LinearEasing)), label = "scanX",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(io.github.thelony97.orbit.core.designsystem.Corners.Card)
            .background(Space.Panel.copy(alpha = 0.85f))
            .border(1.dp, Space.Blue.copy(alpha = 0.35f), io.github.thelony97.orbit.core.designsystem.Corners.Card)
            .drawWithContent {
                drawContent()
                val x = scan?.value ?: return@drawWithContent
                val w = size.width * 0.35f
                val center = -w + (size.width + 2 * w) * x
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Space.Blue.copy(alpha = 0.16f), Color.Transparent),
                        startX = center - w,
                        endX = center + w,
                    ),
                )
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(Space.Blue.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Business, null, tint = Space.Blue, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (u.found) {
                val blur = (1f - sharp) * 8f
                Column(Modifier.graphicsLayer { alpha = 0.35f + 0.65f * sharp }.blur(blur.dp)) {
                    Text(u.name, style = MaterialTheme.typography.titleMediumEmphasized, color = Space.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val second = listOf(u.trade, u.address).filter { it.isNotBlank() }.joinToString(" · ")
                    if (second.isNotBlank()) {
                        Text(second, style = MaterialTheme.typography.labelMedium, color = Space.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else {
                // The final shape in placeholder bars (PLAN 1.2: no spinners).
                Box(Modifier.fillMaxWidth(0.62f).height(14.dp).clip(RoundedCornerShape(50)).background(Space.PanelHighest))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(0.4f).height(10.dp).clip(RoundedCornerShape(50)).background(Space.PanelHighest))
            }
        }
    }
}

/**
 * PLAN 9 B4: what the macro does now – a segment bar running through a pause (Takt each second),
 * or a pulsing „Weiter“ while it waits for the hotline.
 */
@Composable
private fun MacroBar(p: MacroPlaying, onNext: () -> Unit) {
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(p.name, style = MaterialTheme.typography.labelLarge, color = Space.Teal)
        Spacer(Modifier.height(8.dp))
        when {
            p.waiting -> {
                // Pulses while it waits – frozen in battery saver (PLAN 1.1).
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val saver = remember { ctx.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true }
                val pulse = if (saver) null else rememberInfiniteTransition(label = "weiter").animateFloat(
                    1f, 1.06f, infiniteRepeatable(tween(Motion.EMPHASIZED_MS, easing = LinearEasing), RepeatMode.Reverse), label = "pulse",
                )
                GlowButton(
                    "Weiter", { haptics.perform(Haptic.Confirm); onNext() },
                    modifier = Modifier.graphicsLayer { val v = pulse?.value ?: 1f; scaleX = v; scaleY = v },
                    color = Space.Teal,
                )
            }
            p.pauseMs > 0 -> {
                var progress by remember(p.pauseSince) { androidx.compose.runtime.mutableFloatStateOf(0f) }
                LaunchedEffect(p.pauseSince) {
                    var lastSecond = -1L
                    while (true) {
                        val t = (System.currentTimeMillis() - p.pauseSince).coerceAtLeast(0)
                        progress = (t.toFloat() / p.pauseMs).coerceIn(0f, 1f)
                        val second = t / 1_000
                        if (second != lastSecond && lastSecond >= 0) haptics.perform(Haptic.Segment)
                        lastSecond = second
                        if (progress >= 1f) break
                        androidx.compose.runtime.withFrameMillis { }
                    }
                }
                // One segment per second, filling as the pause runs.
                val segments = (p.pauseMs / 1_000).toInt().coerceIn(1, 12)
                Row(Modifier.fillMaxWidth(0.7f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(segments) { i ->
                        val fill = (progress * segments - i).coerceIn(0f, 1f)
                        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50)).background(Space.PanelHighest)) {
                            Box(Modifier.fillMaxWidth(fill).height(6.dp).background(Space.Teal))
                        }
                    }
                }
            }
            else -> Spacer(Modifier.height(6.dp))
        }
    }
}

/** The running time; only this text recomposes every second. */
@Composable
private fun CallTimer(clock: () -> Long, onHold: Boolean) {
    Text(
        listOfNotNull(timer(clock()), "gehalten".takeIf { onHold }).joinToString(" · "),
        style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
        color = Space.Teal,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SmsTemplates(templates: List<String>, onPick: (String) -> Unit) {
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        templates.forEach { t ->
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
private fun AfterCall(
    unlocked: Boolean,
    actions: CallActions,
    slideIn: Boolean = true,
    unknown: Boolean = false,
    initial: String = "",
    found: UnknownCaller? = null,
    recorded: String? = null,
    cases: List<String> = emptyList(),
) {
    val note = rememberTextFieldState(initial)
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
        // PLAN 9 B7 „Tastenfeld schreibt mit“: every Vorgangsnummer as a chip into the note.
        if (unlocked && cases.size > 1) {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cases.forEach { c ->
                    io.github.thelony97.orbit.ui.components.SpaceChip("Vorgang $c", note.text.contains(c), {
                        if (!note.text.contains(c)) note.edit { append(if (length == 0) "Vorgang $c" else " · Vorgang $c") }
                    }, accent = Space.Teal)
                }
            }
        }
        if (unlocked) {
            OutlinedTextField(
                state = note,
                placeholder = { Text("Notiz zum Anruf") },
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 1, maxHeightInLines = 4),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                onKeyboardAction = {
                    if (note.text.isNotBlank()) {
                        actions.saveNote(note.text.toString().trim())
                        actions.close()
                    }
                },
                shape = io.github.thelony97.orbit.core.designsystem.Corners.Row,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (recorded != null) {
            // PLAN 9 B4: „einmal selbst durchwählen = aufgenommen“ – kept with one tap.
            io.github.thelony97.orbit.ui.components.Ghost(
                source = "Im Gespräch getippt",
                onAccept = actions::saveMacro,
                onDismiss = null,
                modifier = Modifier.fillMaxWidth(),
                accent = Space.Teal,
                dismissAxis = null,
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).background(Space.Teal.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Dialpad, null, tint = Space.Teal, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Als Makro speichern", style = MaterialTheme.typography.titleSmallEmphasized, color = Space.Text)
                        Text(recorded, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), color = Space.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (unknown && found != null) {
            // PLAN 9 B1: Herold knew the number – a ghost with everything, ready to keep.
            io.github.thelony97.orbit.ui.components.Ghost(
                source = "Herold · " + found.name,
                onAccept = { actions.createContact(true) },
                onDismiss = null,
                modifier = Modifier.fillMaxWidth(),
                accent = Space.Blue,
                dismissAxis = null,
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).background(Space.Blue.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Business, null, tint = Space.Blue, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Als Organisation anlegen", style = MaterialTheme.typography.titleSmallEmphasized, color = Space.Text)
                        Text(
                            listOf(found.name, found.trade).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = Space.TextDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Als Kontakt anlegen", { actions.createContact(false) }, icon = Icons.Rounded.PersonAdd)
            }
        } else if (unknown) {
            // Talked to an unknown number: keep it (PLAN 3.12 „Danach“).
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Als Kontakt anlegen", { actions.createContact(false) }, icon = Icons.Rounded.PersonAdd)
                GhostButton("Organisation", { actions.createContact(true) }, icon = Icons.Rounded.Business)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            GhostButton("Schließen", actions::close)
            if (unlocked) {
                // Speichern morphs into the check (PLAN 1.2), then the screen closes.
                val text = note.text.toString().trim()
                io.github.thelony97.orbit.ui.components.SaveMorphButton(
                    // Saved right away; only closing waits for the check morph.
                    onSave = { if (text.isNotEmpty()) { actions.saveNote(text); true } else false },
                    onDone = { actions.close() },
                )
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
private fun rememberTiltCamera(enabled: Boolean): io.github.thelony97.orbit.ui.components.SpaceCamera {
    val camera = remember { io.github.thelony97.orbit.ui.components.SpaceCamera() }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // Only while the screen is in front (paused in the background).
    androidx.lifecycle.compose.LifecycleResumeEffect(enabled) {
        val sm = ctx.getSystemService(android.hardware.SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        val saver = ctx.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true
        if (!enabled || sm == null || sensor == null || saver) return@LifecycleResumeEffect onPauseOrDispose { }
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
                val pan = ((bx - fx) / 9.81f).coerceIn(-1f, 1f) * 0.25f
                val scroll = ((fy - by) / 9.81f).coerceIn(-1f, 1f) * 160f
                // Only noticeable changes redraw the sky.
                if (abs(pan - camera.pan) > 0.002f) camera.pan = pan
                if (abs(scroll - camera.scroll) > 1f) camera.scroll = scroll
            }

            override fun onAccuracyChanged(s: android.hardware.Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_UI)
        onPauseOrDispose { sm.unregisterListener(listener) }
    }
    return camera
}

private fun timer(sec: Long): String = "%d:%02d".format(sec / 60, sec % 60)

@Composable
private fun CallerPortrait(card: CallerCard?, color: Color, faded: Boolean, size: androidx.compose.ui.unit.Dp = 132.dp) {
    val photo = rememberPhoto(card?.photo)
    Box(
        Modifier
            .size(size)
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
            Text(initials?.ifBlank { null } ?: "?", fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.35f).sp, color = Space.Text)
        }
    }
}

/** „Luki · Mia (7)“ – partner first, then the children with their age (PLAN 9 B7). */
private fun familyLine(f: List<io.github.thelony97.orbit.core.domain.FamilyMember>, today: java.time.LocalDate): String =
    f.joinToString(" · ") { m ->
        val age = m.birthday?.let { b ->
            io.github.thelony97.orbit.core.domain.DateMath.turning(b, today)?.let { t ->
                if (io.github.thelony97.orbit.core.domain.DateMath.daysUntil(b, today) == 0L) t else t - 1
            }
        }
        if (m.kind == "child" && age != null && age >= 0) "${m.name} ($age)" else m.name
    }

/** A family birthday within two weeks: „Mia: Geburtstag am Freitag · wird 7“. */
private fun familyBirthday(f: List<io.github.thelony97.orbit.core.domain.FamilyMember>, today: java.time.LocalDate): String? =
    f.firstNotNullOfOrNull { m -> m.birthday?.let { b -> CallerNotice.occasion(b, today)?.let { "${m.name}: $it" } } }

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
        // PLAN 9 B7: how to address them and who belongs to them.
        ui.card?.anrede?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = Space.Text) }
        ui.card?.family?.takeIf { it.isNotEmpty() }?.let { f ->
            val today = java.time.LocalDate.now()
            Text(familyLine(f, today), style = MaterialTheme.typography.labelLarge, color = Space.TextDim)
            familyBirthday(f, today)?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = Space.Orange) }
        }
        if (notice.line.isNotBlank() && notice.line != notice.publicLine) {
            Text(
                notice.line.removePrefix(notice.publicLine).removePrefix(" · ").replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelLarge,
                color = Space.TextDim,
            )
        }
        ui.card?.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Space.Text) }
        // Topics can be ticked off during the call (PLAN 3.12 „Themen abhaken“).
        val haptics = rememberHaptics()
        ui.card?.topics?.forEach { t ->
            val tick = { haptics.perform(Haptic.Confirm); actions.toggleTopic(t) }
            // The whole row is the target, not just the 22 dp dot.
            Row(
                Modifier.fillMaxWidth().clip(io.github.thelony97.orbit.core.designsystem.Corners.Row).clickable(onClick = tick).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckDot(t in ui.doneTopics, tick)
                Spacer(Modifier.width(12.dp))
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
    val haptics = rememberHaptics()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (on) Space.Teal.copy(alpha = 0.22f) else Space.PanelHigh)
                .border(1.dp, if (on) Space.Teal.copy(alpha = 0.6f) else Space.Line, CircleShape)
                .clickable { haptics.perform(Haptic.Tick); onClick() },
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = if (on) Space.Teal else Space.TextDim) }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Space.TextDim, modifier = Modifier.padding(top = 4.dp))
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
    val springBack = MaterialTheme.motionScheme.defaultSpatialSpec<Offset>()
    // A dot travels to the sun as a hint: 400 ms (Motion.MAX) then a pause; none in power saving.
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val saver = remember { ctx.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true }
    val hint: androidx.compose.runtime.State<Float> = if (saver) {
        remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    } else {
        rememberInfiniteTransition(label = "hint").animateFloat(
            0f, 1f,
            infiniteRepeatable(
                androidx.compose.animation.core.keyframes {
                    durationMillis = 1400
                    0f at 0 using LinearEasing
                    1f at Motion.MAX_MS
                },
                RepeatMode.Restart,
            ),
            label = "hint",
        )
    }
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
                .pointerInput(w, h) {
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
                                    scope.launch {
                                        offset.animateTo(sun - home, tween(Motion.FAST_MS, easing = Motion.Emphasized))
                                        // The haptic lands with the planet, not before.
                                        haptics.perform(Haptic.Confirm)
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
                                else -> scope.launch { offset.animateTo(Offset.Zero, springBack) }
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
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // SIM still to pick: no call yet, so nothing to mute or hold (W5).
        // Four equal slots; while dialing two of them stay centred in the same slot width.
        if (ui.accounts.isEmpty()) BoxWithConstraints(Modifier.fillMaxWidth()) {
        val slot = Modifier.width(maxWidth / 4)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            ToggleOrb(Icons.Rounded.MicOff, "Stumm", ui.muted, actions::toggleMute, slot)
            // With a headset or Bluetooth the button names where the sound goes and moves it on.
            val (icon, label) = when {
                !ui.moreRoutes -> Icons.AutoMirrored.Rounded.VolumeUp to "Lautsprecher"
                ui.route == AudioRoute.BLUETOOTH -> Icons.Rounded.Bluetooth to ui.routeName.ifBlank { "Bluetooth" }
                ui.route == AudioRoute.SPEAKER -> Icons.AutoMirrored.Rounded.VolumeUp to "Lautsprecher"
                ui.route == AudioRoute.WIRED -> Icons.Rounded.Headphones to "Kopfhörer"
                else -> Icons.Rounded.PhoneInTalk to "Hörer"
            }
            ToggleOrb(icon, label, ui.speaker || ui.route == AudioRoute.BLUETOOTH, actions::toggleSpeaker, slot)
            // Holding and keys only once the other side answered.
            if (!ui.dialing) {
                ToggleOrb(Icons.Rounded.Pause, "Halten", ui.onHold, actions::toggleHold, slot)
                ToggleOrb(Icons.Rounded.Dialpad, "Tasten", keys, onKeys, slot)
            }
        }
        }
        Box(
            Modifier.size(72.dp).glow(Space.Coral, 22.dp, CircleShape, 0.6f).clip(CircleShape).background(Space.Coral)
                .clickable { haptics.perform(Haptic.Reject); actions.hangUp() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.CallEnd, "Auflegen", tint = Space.Void, modifier = Modifier.size(32.dp)) }
    }
}

@Composable
private fun ToggleOrb(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    // On/off fades instead of jumping (W2).
    val fill by androidx.compose.animation.animateColorAsState(if (on) Space.Text else Space.PanelHigh, tween(Motion.FAST_MS), label = "orbFill")
    val tint by androidx.compose.animation.animateColorAsState(if (on) Space.Void else Space.Text, tween(Motion.FAST_MS), label = "orbTint")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(fill)
                .border(1.dp, if (on) Space.Text else Space.Line, CircleShape)
                .clickable { haptics.perform(Haptic.Tick); onClick() },
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = tint) }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Space.TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
        )
    }
}
