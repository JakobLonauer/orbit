package io.github.thelony97.orbit.ui

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
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
import io.github.thelony97.orbit.ui.mail.MailAccountsScreen
import io.github.thelony97.orbit.ui.onboarding.OnboardingScreen
import io.github.thelony97.orbit.ui.components.SpaceBackground
import io.github.thelony97.orbit.ui.components.UndoBar
import io.github.thelony97.orbit.ui.settings.ArchiveScreen
import io.github.thelony97.orbit.ui.settings.SettingsScreen
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
    /** Back to the start screen on the given tab (0 Kontakte, 1 Orbit, 2 Netz, 3 Karte, 4 Einstellungen). */
    val home: (Int) -> Unit,
)

@Composable
fun OrbitRoot(activity: FragmentActivity) {
    val data by OrbitRepository.data.collectAsStateWithLifecycle()
    val locked by AppLock.locked.collectAsStateWithLifecycle()
    val loadError by OrbitRepository.loadError.collectAsStateWithLifecycle()
    val current = data
    val failed = loadError

    Box(Modifier.fillMaxSize().background(Space.Void)) {
        when {
            failed != null -> LoadErrorScreen(failed)
            // While the file loads: the list in its final shape, no spinner (PLAN 1.2).
            current == null -> Box(Modifier.fillMaxSize().statusBarsPadding()) {
                io.github.thelony97.orbit.ui.components.ListPlaceholder()
            }
            !current.settings.onboardingDone -> OnboardingScreen(current)
            else -> MainNavigation(current)
        }
        // Undo bar above everything except the lock, just over the Orbit-Leiste.
        UndoBar(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 88.dp),
        )
        // Back to the running call (only while Orbit is the phone app and a call runs).
        io.github.thelony97.orbit.ui.call.OngoingCallChip(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 4.dp),
        )
        val showLock = current != null && current.settings.onboardingDone && current.settings.lockEnabled && locked
        // The lock is its own window above everything – also above open dialogs, sheets and the
        // long-press popup, which are separate windows and would otherwise stay visible on top.
        if (showLock) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { activity.moveTaskToBack(true) },
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
            ) {
                val window = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
                androidx.compose.runtime.SideEffect { window?.setDimAmount(0f) }
                val appear = androidx.compose.runtime.remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(visibleState = appear, enter = fadeIn(), exit = fadeOut()) {
                    Box(Modifier.fillMaxSize()) { LockScreen(activity) }
                }
            }
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
            home = { target ->
                while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                tab = target
            },
        )
    }

    val pendingNew by MainActivity.pendingNew.collectAsStateWithLifecycle()
    LaunchedEffect(pendingNew) {
        val (number, org) = pendingNew ?: return@LaunchedEffect
        val draft = io.github.thelony97.orbit.data.Contact(
            id = io.github.thelony97.orbit.data.newId(),
            kind = if (org) io.github.thelony97.orbit.data.ContactKind.ORG else io.github.thelony97.orbit.data.ContactKind.PERSON,
            phones = listOf(io.github.thelony97.orbit.data.LabeledValue(if (org) "Zentrale" else "Mobil", number)),
        )
        // An open edit page with unsaved input stays underneath (it has its own discard prompt).
        if (backStack.none { it is Route.Edit }) while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        backStack.add(Route.Edit(prefill = io.github.thelony97.orbit.data.OrbitRepository.json.encodeToString(io.github.thelony97.orbit.data.Contact.serializer(), draft)))
        MainActivity.pendingNew.value = null
    }

    // App-Shortcuts and the QS tile (PLAN 3.14).
    val shortcut by MainActivity.pendingShortcut.collectAsStateWithLifecycle()
    LaunchedEffect(shortcut) {
        val action = shortcut ?: return@LaunchedEffect
        MainActivity.pendingShortcut.value = null
        // An open edit page with unsaved input stays underneath (it has its own discard prompt).
        val editing = backStack.any { it is Route.Edit }
        fun clear() { if (!editing) while (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
        val last = if (action == io.github.thelony97.orbit.actions.AppShortcuts.ACTION_LAST_CALL) {
            io.github.thelony97.orbit.core.domain.Shortcuts.lastCall(data.contacts, io.github.thelony97.orbit.data.now())
        } else null
        val target: Route? = when {
            action == io.github.thelony97.orbit.actions.AppShortcuts.ACTION_NEW -> Route.Edit()
            last != null -> Route.Detail(last.contactId, last.id)
            else -> null
        }
        if (target != null) {
            clear()
            backStack.add(target)
        } else if (!editing) {
            nav.home(io.github.thelony97.orbit.ui.home.HomeTab.VERLAUF)
        }
    }

    val share by MainActivity.pendingShare.collectAsStateWithLifecycle()
    share?.let { s ->
        io.github.thelony97.orbit.ui.sheets.ShareSheet(s, data, idx, nav, onDone = { MainActivity.pendingShare.value = null })
    }

    val pending by MainActivity.pendingContact.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        val id = pending ?: return@LaunchedEffect
        if (data.contacts.any { it.id == id }) {
            if (backStack.none { it is Route.Edit }) while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
            backStack.add(Route.Detail(id))
        }
        MainActivity.pendingContact.value = null
    }

    // Shared elements between pages (avatar and name fly from the list into the contact page).
    androidx.compose.animation.SharedTransitionLayout {
    androidx.compose.runtime.CompositionLocalProvider(io.github.thelony97.orbit.ui.components.LocalSharedScope provides this) {
    NavDisplay(
        backStack = backStack,
        onBack = { nav.back() },
        entryProvider = entryProvider {
            entry<Route.Home> {
                HomeScreen(data = data, idx = idx, nav = nav, tab = tab, onTab = { tab = it })
            }
            entry<Route.Detail> { key ->
                ContactDetailScreen(id = key.id, data = data, idx = idx, nav = nav, openEntry = key.entryId)
            }
            entry<Route.Photos> { key ->
                io.github.thelony97.orbit.ui.detail.PhotosScreen(contactId = key.contactId, data = data, idx = idx, nav = nav)
            }
            entry<Route.MemberPage> { key ->
                io.github.thelony97.orbit.ui.detail.MemberScreen(orgId = key.orgId, memberId = key.memberId, data = data, idx = idx, nav = nav)
            }
            entry<Route.Edit> { key ->
                ContactEditScreen(id = key.id, prefill = key.prefill, data = data, idx = idx, nav = nav)
            }
            entry<Route.Groups> { GroupsScreen(data = data, nav = nav) }
            entry<Route.EntryTypes> { EntryTypesScreen(data = data, nav = nav) }
            entry<Route.Trash> { TrashScreen(data = data, idx = idx, nav = nav) }
            entry<Route.Archive> { ArchiveScreen(data = data, idx = idx, nav = nav) }
            entry<Route.Settings> { SpaceBackground { SettingsScreen(data = data, nav = nav) } }
            entry<Route.SettingsArea> { key -> SpaceBackground { SettingsScreen(data = data, nav = nav, area = key.key) } }
            entry<Route.Suggestions> { SuggestionsScreen(data = data, nav = nav) }
            entry<Route.MailAccounts> { MailAccountsScreen(nav = nav) }
        },
    )
    }
    }
}
