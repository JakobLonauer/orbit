package io.github.thelony97.orbit.ui.home

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Map
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
import io.github.thelony97.orbit.ui.network.NetworkScreen
import io.github.thelony97.orbit.ui.verlauf.VerlaufScreen
import io.github.thelony97.orbit.ui.theme.Space
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import io.github.thelony97.orbit.core.designsystem.glass as hazeGlass

private data class Tab(val label: String, val icon: ImageVector)

/** PLAN 3.1: Kontakte · Verlauf · (Anlässe ab R3) · Netz · Karte. Einstellungen hinter dem Zahnrad. */
private val tabs = listOf(
    Tab("Kontakte", Icons.Rounded.Contacts),
    Tab("Verlauf", Icons.Rounded.History),
    Tab("Netz", Icons.Rounded.Hub),
    Tab("Karte", Icons.Rounded.Map),
)

/** Space reserved at the bottom of scrolling content for the floating bar. */
val BottomBarSpace = 104.dp

@Composable
fun HomeScreen(data: OrbitData, idx: Index, nav: Nav, tab: Int, onTab: (Int) -> Unit) {
    val glass = rememberGlassState()
    // Tabs keep their scroll position and saved state when you come back (PLAN 3.1).
    val saved = rememberSaveableStateHolder()
    val current = tab.coerceIn(0, tabs.lastIndex)
    SpaceBackground {
        Box(Modifier.fillMaxSize().glassSource(glass)) {
            AnimatedContent(
                targetState = current,
                transitionSpec = { fadeIn(tween(Motion.MEDIUM_MS)) togetherWith fadeOut(tween(Motion.FAST_MS)) },
                label = "tab",
                modifier = Modifier.fillMaxSize(),
            ) { shown ->
                saved.SaveableStateProvider(shown) {
                    when (shown) {
                        1 -> VerlaufScreen(data, idx, nav)
                        2 -> NetworkScreen(data, idx, nav)
                        3 -> MapScreen(data, idx, nav)
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
            if (current == 0) {
                Box(Modifier.fillMaxWidth().padding(end = 16.dp, bottom = 10.dp), contentAlignment = Alignment.BottomEnd) {
                    ContactFabMenu(nav)
                }
            }
            OrbitNavBar(glass = glass, selected = current, onSelect = onTab)
        }
    }
}

/**
 * Orbit-Leiste (PLAN 1.2): frosted glass over the content; the active tab is a glowing planet
 * that flies to the new tab in an arc with a light trail. Haptic tick on every switch (W1).
 */
@Composable
private fun OrbitNavBar(glass: HazeState, selected: Int, onSelect: (Int) -> Unit) {
    val haptics = rememberHaptics()
    val pill = RoundedCornerShape(50)
    val slot = 64.dp
    val planet = remember { Animatable(selected.toFloat()) }
    var from by remember { mutableFloatStateOf(selected.toFloat()) }
    LaunchedEffect(selected) {
        from = planet.value
        planet.animateTo(selected.toFloat(), tween(Motion.EMPHASIZED_MS, easing = Motion.Emphasized))
    }
    Box(
        Modifier
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
            drawCircle(
                Brush.radialGradient(listOf(Space.Violet.copy(alpha = 0.55f), Color.Transparent), center, 14.dp.toPx()),
                radius = 14.dp.toPx(),
                center = center,
            )
            drawCircle(Space.Text, radius = 3.dp.toPx(), center = center)
        }
    }
}
