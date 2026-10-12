package io.github.thelony97.orbit.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.ui.Nav
import io.github.thelony97.orbit.ui.list.ContactListScreen
import io.github.thelony97.orbit.ui.list.ContactFabMenu
import io.github.thelony97.orbit.ui.orbit.OrbitScreen
import io.github.thelony97.orbit.ui.settings.SettingsScreen
import io.github.thelony97.orbit.update.UpdateState
import io.github.thelony97.orbit.update.Updates

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("Kontakte", Icons.Rounded.Contacts),
    Tab("Orbit", Icons.Rounded.BubbleChart),
    Tab("Einstellungen", Icons.Rounded.Settings),
)

@Composable
fun HomeScreen(data: OrbitData, idx: Index, nav: Nav, tab: Int, onTab: (Int) -> Unit) {
    val update by Updates.state.collectAsState()
    Scaffold(
        bottomBar = {
            ShortNavigationBar {
                tabs.forEachIndexed { i, t ->
                    ShortNavigationBarItem(
                        selected = tab == i,
                        onClick = { onTab(i) },
                        icon = {
                            if (i == 2 && update is UpdateState.Available) {
                                BadgedBox(badge = { Badge() }) { Icon(t.icon, null) }
                            } else {
                                Icon(t.icon, null)
                            }
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = { if (tab == 0) ContactFabMenu(nav) },
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "tab",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                when (current) {
                    0 -> ContactListScreen(data, idx, nav)
                    1 -> OrbitScreen(data, idx, nav)
                    else -> SettingsScreen(data, nav)
                }
            }
        }
    }
}
