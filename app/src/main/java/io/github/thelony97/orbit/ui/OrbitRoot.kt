package io.github.thelony97.orbit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.github.thelony97.orbit.MainActivity
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.domain.Index
import io.github.thelony97.orbit.security.AppLock
import io.github.thelony97.orbit.ui.detail.ContactDetailScreen
import io.github.thelony97.orbit.ui.edit.ContactEditScreen
import io.github.thelony97.orbit.ui.home.HomeScreen
import io.github.thelony97.orbit.ui.lock.LockScreen
import io.github.thelony97.orbit.ui.onboarding.OnboardingScreen
import io.github.thelony97.orbit.ui.settings.EntryTypesScreen
import io.github.thelony97.orbit.ui.settings.GroupsScreen
import io.github.thelony97.orbit.ui.settings.TrashScreen
import io.github.thelony97.orbit.ui.suggestions.SuggestionsScreen
import io.github.thelony97.orbit.ui.theme.Space

/** Callbacks every screen needs for moving around. */
class Nav(
    val open: (Route) -> Unit,
    val back: () -> Unit,
    val replace: (Route) -> Unit,
)

@Composable
fun OrbitRoot(activity: FragmentActivity) {
    val data by OrbitRepository.data.collectAsStateWithLifecycle()
    val locked by AppLock.locked.collectAsStateWithLifecycle()
    val current = data

    Box(Modifier.fillMaxSize().background(Space.Void)) {
        when {
            current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator(modifier = Modifier.size(56.dp), color = Space.Violet)
            }
            !current.settings.onboardingDone -> OnboardingScreen(current)
            else -> MainNavigation(current)
        }
        val showLock = current != null && current.settings.onboardingDone && current.settings.lockEnabled && locked
        AnimatedVisibility(visible = showLock, enter = fadeIn(), exit = fadeOut()) {
            LockScreen(activity)
        }
    }
}

@Composable
private fun MainNavigation(data: OrbitData) {
    val backStack = rememberNavBackStack(Route.Home)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val idx = remember(data) { Index(data) }
    val nav = remember(backStack) {
        Nav(
            open = { route -> backStack.add(route) },
            back = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
            replace = { route ->
                if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                backStack.add(route)
            },
        )
    }

    val pending by MainActivity.pendingContact.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        val id = pending ?: return@LaunchedEffect
        if (data.contacts.any { it.id == id }) {
            while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
            backStack.add(Route.Detail(id))
        }
        MainActivity.pendingContact.value = null
    }

    NavDisplay(
        backStack = backStack,
        onBack = { nav.back() },
        entryProvider = entryProvider {
            entry<Route.Home> {
                HomeScreen(data = data, idx = idx, nav = nav, tab = tab, onTab = { tab = it })
            }
            entry<Route.Detail> { key ->
                ContactDetailScreen(id = key.id, data = data, idx = idx, nav = nav)
            }
            entry<Route.Edit> { key ->
                ContactEditScreen(id = key.id, prefill = key.prefill, data = data, idx = idx, nav = nav)
            }
            entry<Route.Groups> { GroupsScreen(data = data, nav = nav) }
            entry<Route.EntryTypes> { EntryTypesScreen(data = data, nav = nav) }
            entry<Route.Trash> { TrashScreen(data = data, idx = idx, nav = nav) }
            entry<Route.Suggestions> { SuggestionsScreen(data = data, nav = nav) }
        },
    )
}
