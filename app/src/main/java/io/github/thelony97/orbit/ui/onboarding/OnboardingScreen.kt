package io.github.thelony97.orbit.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.core.designsystem.Motion
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.notify.DigestScheduler
import io.github.thelony97.orbit.security.AppLock
import io.github.thelony97.orbit.sync.Sync
import io.github.thelony97.orbit.ui.PhoneImport
import io.github.thelony97.orbit.ui.Photos
import io.github.thelony97.orbit.ui.components.Avatar
import io.github.thelony97.orbit.ui.components.GhostButton
import io.github.thelony97.orbit.ui.components.GlowBar
import io.github.thelony97.orbit.ui.components.GlowButton
import io.github.thelony97.orbit.ui.components.SpaceBackground
import io.github.thelony97.orbit.ui.components.contactColor
import io.github.thelony97.orbit.ui.components.glow
import io.github.thelony97.orbit.ui.list.PhoneBookList
import io.github.thelony97.orbit.ui.list.takeOverAll
import io.github.thelony97.orbit.ui.theme.Space
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Erster Start (PLAN 1.3): zwei Schritte – ICH (Name, Foto) → Handy-Kontakte auswählen – dann
 * „Dein Orbit füllt sich“. Permissions, backup folder and lock are asked where they are needed
 * (Verlauf, Einstellungen), never all at once (PLAN 1.2).
 */
@Composable
fun OnboardingScreen(data: OrbitData, preview: List<PhoneImport.Item>? = null, initialStep: Int = 0) {
    var step by rememberSaveable { mutableIntStateOf(initialStep) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var arrived by remember { mutableStateOf<List<Contact>?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun finish() {
        AppLock.unlock()
        // „Dein Orbit füllt sich“ was the start moment – the ring would only cover the new list.
        if (!arrived.isNullOrEmpty()) io.github.thelony97.orbit.ui.Session.startPlayed = true
        // The screen leaves the composition right after the update – the rest must not be cancelled.
        scope.launch(kotlinx.coroutines.NonCancellable) {
            DigestScheduler.schedule(ctx)
            Sync.requestNow(ctx)
            OrbitRepository.update { d -> d.copy(settings = d.settings.copy(onboardingDone = true)) }
            OrbitRepository.flush()
        }
    }

    val me = Logic.me(data)
    val first = rememberTextFieldState(me?.firstName?.takeIf { it != io.github.thelony97.orbit.data.Defaults.ME_DEFAULT_NAME }.orEmpty())
    val last = rememberTextFieldState(me?.lastName.orEmpty())
    var photo by rememberSaveable { mutableStateOf(me?.photo) }
    var items by remember { mutableStateOf(preview) }
    var selected by remember { mutableStateOf(setOf<Long>()) }

    fun saveMe() {
        val id = me?.id ?: return
        val f = first.text.toString().trim()
        val l = last.text.toString().trim()
        OrbitRepository.editContact(id) { it.copy(firstName = f.ifEmpty { if (l.isEmpty()) io.github.thelony97.orbit.data.Defaults.ME_DEFAULT_NAME else "" }, lastName = l, photo = photo) }
    }

    fun takeOver() {
        val picked = items.orEmpty().filter { it.id in selected }
        if (busy) return
        if (picked.isEmpty()) return finish()
        busy = true
        scope.launch {
            arrived = takeOverAll(ctx, picked)
            busy = false
            if (arrived.isNullOrEmpty()) finish()
        }
    }

    val landed = arrived
    if (landed != null && landed.isNotEmpty()) {
        OrbitFills(me, landed, remember(data) { Index(data) }, onDone = ::finish)
        return
    }

    androidx.activity.compose.BackHandler(enabled = step > 0) { step-- }
    SpaceBackground {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(vertical = 16.dp)) {
            GlowBar((step + 1f) / 2f, Space.Orange, Modifier.padding(horizontal = 16.dp))
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
                if (current == 0) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                        MeStep(me, first, last, photo, onPhoto = { photo = it })
                    }
                } else {
                    PeopleStep(data, items, { items = it }, selected, { selected = it })
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) GhostButton("Zurück", { step-- })
                Spacer(Modifier.weight(1f))
                if (step == 0) {
                    GlowButton("Weiter", { saveMe(); step = 1 })
                } else {
                    GlowButton(if (selected.isEmpty()) "Ohne Kontakte starten" else "Übernehmen (${selected.size})", ::takeOver, enabled = !busy)
                }
            }
        }
    }
}

@Composable
private fun MeStep(
    me: Contact?,
    first: androidx.compose.foundation.text.input.TextFieldState,
    last: androidx.compose.foundation.text.input.TextFieldState,
    photo: String?,
    onPhoto: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { Photos.saveFromUri(ctx, uri)?.let(onPhoto) }
    }
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        // Infinite-animation frames: tests and battery saver may pause them.
        val start = androidx.compose.animation.core.withInfiniteAnimationFrameNanos { it }
        while (true) androidx.compose.animation.core.withInfiniteAnimationFrameNanos { seconds = (it - start) / 1e9f }
    }
    // You in the middle, the first orbits already turning (Start-Moment).
    Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
        listOf(70f, 106f, 136f).forEach { r -> Box(Modifier.size((r * 2).dp).border(1.dp, Space.Line, CircleShape)) }
        listOf(Space.Violet, Space.Teal, Space.Coral, Space.Blue).forEachIndexed { i, c ->
            val radius = listOf(70f, 106f, 136f, 106f)[i]
            Box(
                Modifier
                    .offset {
                        val a = seconds * (0.7f - (i % 3) * 0.18f) + i * 2.1f
                        IntOffset((cos(a) * radius * density).roundToInt(), (sin(a) * radius * density).roundToInt())
                    }
                    .size((14 - i * 2).dp)
                    .glow(c, 10.dp, CircleShape, 0.8f)
                    .background(c, CircleShape),
            )
        }
        val shown = me?.let { m -> m.copy(photo = photo, firstName = first.text.toString().ifBlank { m.firstName }, lastName = last.text.toString()) }
        Box(
            Modifier
                .size(96.dp)
                .graphicsLayer { val p = 1f + 0.03f * sin(seconds * 1.6f); scaleX = p; scaleY = p }
                .clip(CircleShape)
                .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            contentAlignment = Alignment.Center,
        ) {
            if (shown != null && photo != null) {
                Avatar(shown, Space.Orange, 96.dp, glowColor = Space.Orange)
            } else {
                Box(
                    Modifier.fillMaxSize().glow(Space.Orange, 30.dp, CircleShape, 0.7f)
                        .background(Brush.radialGradient(listOf(Color(0xFFFFE2B8), Space.Orange, Color(0xFFE07A2E))), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AddAPhoto, "Foto", tint = Space.Void, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
    Text("Das bist du", style = MaterialTheme.typography.headlineMediumEmphasized, color = Space.Text)
    Spacer(Modifier.size(20.dp))
    OutlinedTextField(state = first, label = { Text("Vorname") }, lineLimits = TextFieldLineLimits.SingleLine, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.size(10.dp))
    OutlinedTextField(state = last, label = { Text("Nachname") }, lineLimits = TextFieldLineLimits.SingleLine, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
}

@Composable
private fun PeopleStep(
    data: OrbitData,
    items: List<PhoneImport.Item>?,
    onItems: (List<PhoneImport.Item>) -> Unit,
    selected: Set<Long>,
    onSelected: (Set<Long>) -> Unit,
) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(granted) { if (granted && items == null) onItems(PhoneImport.list(ctx)) }
    Column(Modifier.fillMaxSize()) {
        Text("Wer gehört in deinen Orbit?", style = MaterialTheme.typography.headlineMediumEmphasized, color = Space.Text, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.size(12.dp))
        if (!granted && items == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlowButton("Handy-Kontakte zeigen", { permission.launch(Manifest.permission.READ_CONTACTS) }, color = Space.Teal)
            }
        } else {
            PhoneBookList(items, data, selected, onSelected, contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp))
        }
    }
}

/**
 * „Dein Orbit füllt sich“ (PLAN 1.3): the new contacts fly in from the edges onto orbits around
 * you, circle once and settle into the list – onto the exact rows of the contact list (Ich-Karte,
 * letter heads, 36 dp avatars), so the hand-over to the real list does not jump (W2/W3).
 * A sequence without touch answer (W9 exception); a tap skips it. The clock is read only in
 * layout/draw lambdas: one composition, no per-frame recomposition (W8).
 */
@Composable
internal fun OrbitFills(me: Contact?, contacts: List<Contact>, idx: Index, onDone: () -> Unit, frozenAt: Float? = null) {
    val t = remember { Animatable(frozenAt ?: 0f) }
    LaunchedEffect(Unit) {
        if (frozenAt != null) return@LaunchedEffect
        t.animateTo(3f, tween(2_900, easing = LinearEasing))
        onDone()
    }
    val density = LocalDensity.current
    // What the list will show: people only (organisations and the archive stay hidden there).
    val shown = remember(contacts) {
        contacts.filter { !it.isOrg && !it.archived && it.deletedAt == null }.sortedBy { Logic.sortKey(it, false) }.take(18)
    }
    val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
    fun arrangeOf(time: Float) = Motion.Emphasized.transform(((time - 2.1f) / 0.9f).coerceIn(0f, 1f))
    SpaceBackground {
        BoxWithConstraints(
            Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() },
        ) {
            val w = with(density) { maxWidth.toPx() }
            val h = with(density) { maxHeight.toPx() }
            val cx = w / 2f
            val cy = h * 0.42f
            val dp = density.density
            // Contact list geometry (ContactListScreen): Ich-Karte under the search, then per letter
            // a head (28 dp) and rows of 50 dp with the 36 dp avatar on the 16 dp gutter.
            val cardTop = statusTop + 100f * dp
            val meX = 47f * dp
            val meY = cardTop + 32f * dp
            val heads = mutableListOf<Pair<Char, Float>>()
            val rowY = run {
                var y = cardTop + 60f * dp
                var last: Char? = null
                shown.map { c ->
                    val letter = Logic.sortKey(c, false).firstOrNull()?.uppercaseChar() ?: '#'
                    if (letter != last) {
                        heads += letter to (y + 14f * dp)
                        y += 28f * dp
                        last = letter
                    }
                    (y + 25f * dp).also { y += 50f * dp }
                }
            }

            // The Ich-Karte forms around you as you move into it.
            Box(
                Modifier
                    .offset { IntOffset((16f * dp).roundToInt(), (cardTop + 4f * dp).roundToInt()) }
                    .size(maxWidth - 32.dp, 56.dp)
                    .graphicsLayer { alpha = arrangeOf(t.value) }
                    .border(1.dp, Space.Orange.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
            ) {
                Column(Modifier.padding(start = 62.dp).align(Alignment.CenterStart)) {
                    Text(me?.let { Logic.displayName(it, false) }.orEmpty(), style = MaterialTheme.typography.titleMedium, color = Space.Text, maxLines = 1)
                    Text("Das bist du", style = MaterialTheme.typography.labelMedium, color = Space.TextFaint)
                }
            }
            // You in the middle; you become the Ich-Karte's picture once the list forms.
            if (me != null) {
                Avatar(
                    me, Space.Orange, 88.dp,
                    glowColor = Space.Orange,
                    modifier = Modifier
                        .offset {
                            val a = arrangeOf(t.value)
                            val x = cx + (meX - cx) * a
                            val y = cy + (meY - cy) * a
                            IntOffset((x - 44f * dp).roundToInt(), (y - 44f * dp).roundToInt())
                        }
                        .graphicsLayer {
                            val k = 1f - (50f / 88f) * arrangeOf(t.value)
                            scaleX = k
                            scaleY = k
                        },
                )
            }
            listOf(78f, 118f, 158f).forEach { r ->
                Box(
                    Modifier
                        .offset { IntOffset((cx - r * dp).roundToInt(), (cy - r * dp).roundToInt()) }
                        .size((r * 2).dp)
                        .graphicsLayer { alpha = (t.value / 0.4f).coerceIn(0f, 1f) * (1f - arrangeOf(t.value)) }
                        .border(1.dp, Space.Line, CircleShape),
                )
            }
            // Letter heads fade in where the list will have them.
            heads.forEach { (letter, y) ->
                Row(
                    Modifier
                        .offset { IntOffset((16f * dp).roundToInt(), (y - 10f * dp).roundToInt()) }
                        .width(maxWidth - 32.dp)
                        .graphicsLayer { alpha = arrangeOf(t.value) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(letter.toString(), style = MaterialTheme.typography.labelLargeEmphasized, color = Space.Violet)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f).height(1.dp).background(Space.Line))
                }
            }
            val avatar = 36f * dp
            shown.forEachIndexed { i, c ->
                val ring = i % 3
                val radius = listOf(78f, 118f, 158f)[ring] * dp
                val base = i * 2.399963f
                val lx = 16f * dp + avatar / 2f
                val ly = rowY[i]
                // Rows below the screen edge melt away instead of sliding out of sight.
                val offscreen = ly > h + avatar
                Row(
                    Modifier
                        .offset {
                            val time = t.value
                            val arrange = arrangeOf(time)
                            val local = Motion.Emphasized.transform(((time - i * 0.045f) / 0.9f).coerceIn(0f, 1f))
                            // Spiral in from beyond the screen edge, then keep turning.
                            val far = maxOf(w, h) * 0.9f
                            val r = far + (radius - far) * local
                            val a = base - 1.4f * (1f - local) + (time * (0.55f - ring * 0.12f))
                            val ox = cx + cos(a) * r
                            val oy = cy + sin(a) * r
                            val x = ox + (lx - ox) * arrange
                            val y = oy + (ly - oy) * arrange
                            IntOffset((x - avatar / 2f).roundToInt(), (y - avatar / 2f).roundToInt())
                        }
                        .graphicsLayer { if (offscreen) alpha = 1f - arrangeOf(t.value) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(c, contactColor(c, idx), 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        Logic.displayName(c, false),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                        color = Space.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer { alpha = arrangeOf(t.value) },
                    )
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp).graphicsLayer {
                    alpha = (1f - arrangeOf(t.value)) * (t.value / 0.6f).coerceIn(0f, 1f)
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Dein Orbit füllt sich", style = MaterialTheme.typography.headlineSmallEmphasized, color = Space.Text)
                Text(if (contacts.size == 1) "1 Kontakt" else "${contacts.size} Kontakte", style = MaterialTheme.typography.labelLarge, color = Space.TextDim)
            }
        }
    }
}

