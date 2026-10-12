package io.github.thelony97.orbit.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.components.SpaceBackground
import io.github.thelony97.orbit.ui.components.glass
import io.github.thelony97.orbit.ui.components.glow
import io.github.thelony97.orbit.ui.list.ContactFabMenu
import io.github.thelony97.orbit.ui.list.ContactListScreen
import io.github.thelony97.orbit.ui.orbit.OrbitScreen
import io.github.thelony97.orbit.ui.settings.SettingsScreen
import io.github.thelony97.orbit.ui.theme.Space
import io.github.thelony97.orbit.update.UpdateState
import io.github.thelony97.orbit.update.Updates

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Kontakte", Icons.Rounded.Contacts),
    Tab("Orbit", Icons.Rounded.BubbleChart),
    Tab("Einstellungen", Icons.Rounded.Tune),
)

/** Space reserved at the bottom of scrolling content for the floating bar. */
val BottomBarSpace = 104.dp

@Composable
fun HomeScreen(data: OrbitData, idx: Index, nav: Nav, tab: Int, onTab: (Int) -> Unit) {
    val update by Updates.state.collectAsState()
    SpaceBackground {
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "tab",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            when (current) {
                0 -> ContactListScreen(data, idx, nav)
                1 -> OrbitScreen(data, idx, nav)
                else -> SettingsScreen(data, nav)
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
            if (tab == 0) {
                Box(Modifier.fillMaxWidth().padding(end = 16.dp, bottom = 10.dp), contentAlignment = Alignment.BottomEnd) {
                    ContactFabMenu(nav)
                }
            }
            OrbitNavBar(selected = tab, onSelect = onTab, settingsBadge = update is UpdateState.Available)
        }
    }
}

@Composable
private fun OrbitNavBar(selected: Int, onSelect: (Int) -> Unit, settingsBadge: Boolean) {
    val pill = RoundedCornerShape(50)
    Row(
        Modifier
            .glow(Space.VioletDeep, 26.dp, pill, 0.22f)
            .glass(pill, tint = Space.PanelHigh, border = Space.LineStrong)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { i, t ->
            val isSelected = selected == i
            val bg by animateColorAsState(if (isSelected) Space.Violet.copy(alpha = 0.20f) else Color.Transparent, label = "navBg")
            Row(
                Modifier
                    .height(44.dp)
                    .clip(pill)
                    .background(bg)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 16.dp)
                    .animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    Icon(
                        t.icon, t.label,
                        tint = if (isSelected) Space.Violet else Space.TextDim,
                        modifier = Modifier.size(20.dp),
                    )
                    if (i == 2 && settingsBadge) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 3.dp, y = (-2).dp)
                                .size(8.dp)
                                .glow(Space.Coral, 6.dp)
                                .background(Space.Coral, CircleShape),
                        )
                    }
                }
                AnimatedVisibility(visible = isSelected) {
                    Text(
                        t.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = Space.Text,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
