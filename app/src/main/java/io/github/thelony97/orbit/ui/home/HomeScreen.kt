package io.github.thelony97.orbit.ui.home

import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import io.github.thelony97.orbit.ui.components.SpaceCamera
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import io.github.thelony97.orbit.core.designsystem.Haptic
import io.github.thelony97.orbit.core.designsystem.Motion
import io.github.thelony97.orbit.core.designsystem.glassSource
import io.github.thelony97.orbit.core.designsystem.rememberGlassState
import io.github.thelony97.orbit.core.designsystem.rememberHaptics
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.components.SpaceBackground
import io.github.thelony97.orbit.ui.components.glow
import io.github.thelony97.orbit.ui.list.ContactFabMenu
import io.github.thelony97.orbit.ui.list.ContactListScreen
import io.github.thelony97.orbit.ui.map.MapScreen
import io.github.thelony97.orbit.ui.verlauf.VerlaufScreen
import io.github.thelony97.orbit.ui.theme.Space
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import io.github.thelony97.orbit.core.designsystem.glass as hazeGlass

private data class Tab(val label: String, val icon: ImageVector, val nebula: Color)

/** Tab positions, so jumps from other screens never point at the wrong tab. */
object HomeTab {
    const val CONTACTS = 0
    const val VERLAUF = 1
    const val ANLAESSE = 2
    const val NETZ = 3
    const val KARTE = 4
}

/**
 * PLAN 9 C5: Kontakte · Verlauf · Anlässe · Karte – the net opens from a contact's relations and
 * the ICH card (Route.Network). Einstellungen hinter dem Zahnrad. [ids] are the HomeTab numbers.
 */
private val tabs = listOf(
    Tab("Kontakte", Icons.Rounded.Contacts, Space.VioletDeep),
    Tab("Verlauf", Icons.Rounded.History, Space.Teal),
    Tab("Anlässe", Icons.Rounded.Cake, Space.Coral),
    Tab("Karte", Icons.Rounded.Map, Space.Orange),
)
private val ids = listOf(HomeTab.CONTACTS, HomeTab.VERLAUF, HomeTab.ANLAESSE, HomeTab.KARTE)

/** Space reserved at the bottom of scrolling content for the floating bar. */
val BottomBarSpace = 104.dp

@Composable
fun HomeScreen(data: OrbitData, idx: Index, nav: Nav, tab: Int, onTab: (Int) -> Unit) {
    val glass = rememberGlassState()
    // Tabs keep their scroll position and saved state when you come back (PLAN 3.1).
    val saved = rememberSaveableStateHolder()
    // A tab number from before 3.0 (Netz) lands on Kontakte.
    val current = ids.indexOf(tab).coerceAtLeast(0)
    // Living space (PLAN 1.2): scrolling drags the star layers (parallax), a tab change is a
    // short sideways camera move, every tab has its own nebula tint.
    val camera = remember { SpaceCamera().apply { pan = current.toFloat() } }
    val pan = remember { Animatable(current.toFloat()) }
    LaunchedEffect(current) {
        pan.animateTo(current.toFloat(), tween(Motion.EMPHASIZED_MS, easing = Motion.Emphasized)) { camera.pan = value }
    }
    val nebula by animateColorAsState(tabs[current].nebula, tween(Motion.EMPHASIZED_MS), label = "nebula")
    val parallax = remember(camera) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                camera.scroll -= consumed.y
                return Offset.Zero
            }
        }
    }
    // Start-Moment (PLAN 1.3): a ring draws itself, the planet swings round it and lands as the
    // Orbit-Leiste indicator. Once per app start, after the lock is open; longer than 400 ms is
    // allowed for this sequence (W9 exception).
    val locked by io.github.thelony97.orbit.security.AppLock.locked.collectAsState()
    val waiting = data.settings.lockEnabled && locked
    val intro = remember { Animatable(if (io.github.thelony97.orbit.ui.Session.startPlayed) 1f else 0f) }
    val haptics = rememberHaptics()
    LaunchedEffect(waiting) {
        if (waiting || intro.value >= 1f) return@LaunchedEffect
        intro.animateTo(0.86f, tween(1000, easing = LinearEasing))
        haptics.perform(Haptic.Confirm) // the planet lands
        intro.animateTo(1f, tween(Motion.MEDIUM_MS))
        io.github.thelony97.orbit.ui.Session.startPlayed = true
    }
    var planetTarget by remember { mutableStateOf(Offset.Unspecified) }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { overlayOrigin = it.positionInRoot() }) {
        // The whole opaque space (stars + content) is the glass source; the bar is a sibling
        // outside it, so it never blurs itself and transparent rows never punch holes.
        // PLAN 9 C2: the Live-Insel sits above the tab, the content makes room for it.
        val live = io.github.thelony97.orbit.ui.live.rememberLive(data)
        val islandSpace by androidx.compose.animation.core.animateDpAsState(
            if (live != null) 44.dp else 0.dp, tween(Motion.MEDIUM_MS, easing = Motion.Emphasized), label = "island",
        )
        SpaceBackground(Modifier.glassSource(glass).nestedScroll(parallax), camera = camera, nebula = { nebula }) {
            AnimatedContent(
                targetState = current,
                transitionSpec = { fadeIn(tween(Motion.MEDIUM_MS)) togetherWith fadeOut(tween(Motion.FAST_MS)) },
                label = "tab",
                modifier = Modifier.fillMaxSize().padding(top = islandSpace),
            ) { shown ->
                saved.SaveableStateProvider(shown) {
                    when (ids[shown]) {
                        HomeTab.VERLAUF -> VerlaufScreen(data, idx, nav)
                        HomeTab.ANLAESSE -> io.github.thelony97.orbit.ui.occasions.BirthdaysScreen(data, idx, nav)
                        HomeTab.KARTE -> MapScreen(data, idx, nav)
                        else -> ContactListScreen(data, idx, nav)
                    }
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // On an empty Orbit the list's own „Kontakt anlegen“ is the one action (W6).
            if (ids[current] == HomeTab.CONTACTS && data.contacts.any { !it.isMe && it.deletedAt == null }) {
                Box(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentAlignment = Alignment.BottomEnd) {
                    ContactFabMenu(nav)
                }
            }
            OrbitNavBar(
                glass = glass, selected = current, onSelect = { onTab(ids[it]) },
                planetVisible = { intro.value >= 0.86f },
                onPlanetAt = { planetTarget = it },
            )
        }
        io.github.thelony97.orbit.ui.live.LiveIsland(
            live, nav,
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 4.dp),
        )
        // Only flips once (true → false); the per-frame progress is read while drawing.
        val introRunning by remember { androidx.compose.runtime.derivedStateOf { intro.value < 1f } }
        if (introRunning) StartMoment({ intro.value }, { planetTarget - overlayOrigin })
    }
}

/** Ring, swinging planet and the flight down to the bar; [progress] 0..1 is read while drawing. */
@Composable
private fun StartMoment(progress: () -> Float, target: () -> Offset) {
    Canvas(Modifier.fillMaxSize()) {
        val p = progress()
        // The sky stays dark while ring and planet are on stage and clears as the planet lands,
        // so they never cross readable rows (W4).
        drawRect(Space.Void.copy(alpha = 0.88f * (1f - ((p - 0.62f) / 0.3f).coerceIn(0f, 1f))))
        val ringR = 84.dp.toPx()
        val c = Offset(size.width / 2f, size.height * 0.42f)
        val draw = (p / 0.45f).coerceIn(0f, 1f)
        val ringAlpha = 1f - ((p - 0.45f) / 0.35f).coerceIn(0f, 1f)
        if (ringAlpha > 0f) {
            drawArc(
                Space.Violet.copy(alpha = 0.75f * ringAlpha), startAngle = -90f, sweepAngle = 360f * draw, useCenter = false,
                topLeft = c - Offset(ringR, ringR), size = androidx.compose.ui.geometry.Size(ringR * 2, ringR * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()),
            )
            drawCircle(Space.Orange.copy(alpha = ringAlpha), radius = 7.dp.toPx() * draw, center = c)
        }
        // Planet: rides the ring's drawing head, then flies in an arc to the bar indicator.
        val onRing = run {
            val a = Math.toRadians((-90.0 + 360.0 * draw))
            Offset(c.x + ringR * kotlin.math.cos(a).toFloat(), c.y + ringR * kotlin.math.sin(a).toFloat())
        }
        val t = target().takeIf { it.isSpecified } ?: Offset(size.width / 2f, size.height - 40.dp.toPx())
        val fly = ((p - 0.45f) / 0.41f).coerceIn(0f, 1f)
        val e = Motion.Emphasized.transform(fly)
        val pos = Offset(onRing.x + (t.x - onRing.x) * e, onRing.y + (t.y - onRing.y) * e - kotlin.math.sin(e * PI).toFloat() * 60.dp.toPx())
        if (p < 0.86f + 0.14f) {
            val fade = 1f - ((p - 0.86f) / 0.14f).coerceIn(0f, 1f)
            drawCircle(
                Brush.radialGradient(listOf(Space.Violet.copy(alpha = 0.6f * fade), Space.Violet.copy(alpha = 0f)), pos, 18.dp.toPx()),
                radius = 18.dp.toPx(), center = pos,
            )
            drawCircle(Space.Violet.copy(alpha = fade), radius = 4.5.dp.toPx(), center = pos)
            drawCircle(Space.Text.copy(alpha = fade), radius = 2.4.dp.toPx(), center = pos - Offset(0.8.dp.toPx(), 0.8.dp.toPx()))
        }
    }
}

/**
 * Orbit-Leiste (PLAN 1.2): frosted glass over the content; the active tab is a glowing planet
 * that flies to the new tab in an arc with a light trail. Haptic tick on every switch (W1).
 */
@Composable
private fun OrbitNavBar(
    glass: HazeState,
    selected: Int,
    onSelect: (Int) -> Unit,
    planetVisible: () -> Boolean = { true },
    onPlanetAt: (Offset) -> Unit = {},
) {
    val haptics = rememberHaptics()
    val pill = RoundedCornerShape(50)
    // Four tabs: a little more room each (PLAN 9 C5).
    val slot = 68.dp
    val planet = remember { Animatable(selected.toFloat()) }
    var from by remember { mutableFloatStateOf(selected.toFloat()) }
    LaunchedEffect(selected) {
        from = planet.value
        planet.animateTo(selected.toFloat(), tween(Motion.EMPHASIZED_MS, easing = Motion.Emphasized))
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(
        Modifier
            .onGloballyPositioned { lc ->
                // Where the planet sits (for the Start-Moment to land exactly on it).
                with(density) {
                    onPlanetAt(lc.positionInRoot() + Offset(6.dp.toPx() + (selected + 0.5f) * slot.toPx(), 4.dp.toPx() + 48.dp.toPx() - 5.dp.toPx()))
                }
            }
            .glow(Space.VioletDeep, 26.dp, pill, 0.22f)
            .clip(pill)
            .hazeGlass(glass, Space.PanelHigh, tint = 0.55f)
            .border(1.dp, Space.LineStrong, pill)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Row {
            tabs.forEachIndexed { i, t ->
                val tint by animateColorAsState(if (selected == i) Space.Text else Space.TextDim, tween(Motion.FAST_MS), label = "navTint")
                Box(
                    Modifier
                        .size(slot, 48.dp)
                        .clip(pill)
                        .clickable {
                            if (i != selected) haptics.perform(Haptic.Tick)
                            onSelect(i)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(t.icon, t.label, tint = tint, modifier = Modifier.size(22.dp).padding(bottom = 2.dp))
                }
            }
        }
        Canvas(Modifier.matchParentSize()) {
            val slotPx = slot.toPx()
            val v = planet.value
            val span = selected - from
            val progress = if (abs(span) < 0.001f) 1f else ((v - from) / span).coerceIn(0f, 1f)
            val lift = sin(progress * PI).toFloat() * 12.dp.toPx()
            val baseY = size.height - 5.dp.toPx()
            fun at(x: Float, p: Float) = Offset((x + 0.5f) * slotPx, baseY - sin(p * PI).toFloat() * 12.dp.toPx())
            val center = Offset((v + 0.5f) * slotPx, baseY - lift)
            // Light trail while flying.
            if (progress < 1f) {
                for (k in 1..6) {
                    val p = (progress - k * 0.06f).coerceAtLeast(0f)
                    val x = from + span * p
                    drawCircle(Space.Violet.copy(alpha = 0.32f * (1f - k / 7f)), radius = (3.2f - k * 0.35f).dp.toPx(), center = at(x, p))
                }
            }
            if (!planetVisible()) return@Canvas
            // A real little planet: wide halo, violet body, bright core (reads at a glance, PLAN 1.2).
            drawCircle(
                Brush.radialGradient(listOf(Space.Violet.copy(alpha = 0.6f), Space.Violet.copy(alpha = 0f)), center, 18.dp.toPx()),
                radius = 18.dp.toPx(),
                center = center,
            )
            drawCircle(Space.Violet, radius = 4.5.dp.toPx(), center = center)
            drawCircle(Space.Text, radius = 2.4.dp.toPx(), center = center - Offset(0.8.dp.toPx(), 0.8.dp.toPx()))
        }
    }
}
