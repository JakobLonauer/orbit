package io.github.thelony97.orbit

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.notify.Notifications
import io.github.thelony97.orbit.security.AppLock
import io.github.thelony97.orbit.sync.Sync
import io.github.thelony97.orbit.ui.OrbitRoot
import io.github.thelony97.orbit.ui.theme.OrbitTheme
import io.github.thelony97.orbit.update.Updates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Cold start measurement (PLAN 8.2), per process. */
object StartTime {
    @Volatile var measured = false
    @Volatile var lastMs: Long? = null
}

class MainActivity : FragmentActivity() {

    companion object {
        const val EXTRA_CONTACT = "contact_id"

        /** Contact to open, set by notifications and the widget. */
        val pendingContact = MutableStateFlow<String?>(null)

        /** „Als Kontakt / Organisation anlegen“ after a call from an unknown number (PLAN 3.12). */
        const val EXTRA_NEW_NUMBER = "new_number"
        const val EXTRA_NEW_ORG = "new_org"
        const val EXTRA_NOTIF = "notif_id"
        val pendingNew = MutableStateFlow<Pair<String, Boolean>?>(null)

        /** Teilen-Eingang (PLAN 3.8): shown as a sheet once Orbit is unlocked. */
        val pendingShare = MutableStateFlow<io.github.thelony97.orbit.ui.sheets.PendingShare?>(null)

        /** App-Shortcut or QS tile action (see [io.github.thelony97.orbit.actions.AppShortcuts]). */
        val pendingShortcut = MutableStateFlow<String?>(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            OrbitTheme {
                OrbitRoot(activity = this)
            }
        }
        lifecycleScope.launch {
            OrbitRepository.data.filterNotNull()
                .map { it.settings.lockEnabled }
                .distinctUntilChanged()
                .collect { lockEnabled ->
                    setRecentsScreenshotEnabled(!lockEnabled)
                    if (!lockEnabled) AppLock.unlock()
                }
        }
        lifecycleScope.launch { Updates.checkIfDue() }
        measureColdStart()
    }

    /**
     * W8 / PLAN 8.2: cold start until the first frame with data (budget 400 ms). Logged, handed to
     * the system (reportFullyDrawn) and shown in Über Orbit. Only the first activity of a process.
     */
    private fun measureColdStart() {
        if (StartTime.measured) return
        StartTime.measured = true
        // A process started for a call, widget or worker earlier is no cold start of the app.
        if (android.os.SystemClock.uptimeMillis() - android.os.Process.getStartUptimeMillis() > 5_000) return
        lifecycleScope.launch {
            OrbitRepository.data.filterNotNull().first()
            window.decorView.post {
                val ms = android.os.SystemClock.uptimeMillis() - android.os.Process.getStartUptimeMillis()
                StartTime.lastMs = ms
                android.util.Log.i("Orbit", "Kaltstart bis Liste: $ms ms (Budget 400 ms)")
                runCatching { reportFullyDrawn() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE) {
            val images = buildList {
                if (intent.type?.startsWith("image/") == true || intent.type == "application/pdf") {
                    // One picture comes as a Uri, several as a list – never read one as the other.
                    if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
                        runCatching { intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java) }.getOrNull()?.let { addAll(it) }
                    } else {
                        runCatching { intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java) }.getOrNull()?.let { add(it) }
                    }
                }
            }.distinct()
            val item = io.github.thelony97.orbit.core.domain.ShareIn.parse(
                intent.getStringExtra(Intent.EXTRA_TEXT), intent.getStringExtra(Intent.EXTRA_SUBJECT),
            )
            val pdf = intent.type == "application/pdf"
            if (images.isNotEmpty() || item != null) pendingShare.value = io.github.thelony97.orbit.ui.sheets.PendingShare(if (pdf) null else item, images, documents = pdf)
            // Handled once: a rotation must not bring the sheet back.
            intent.action = Intent.ACTION_MAIN
            return
        }
        when (intent?.action) {
            io.github.thelony97.orbit.actions.AppShortcuts.ACTION_NEW,
            io.github.thelony97.orbit.actions.AppShortcuts.ACTION_VERLAUF,
            io.github.thelony97.orbit.actions.AppShortcuts.ACTION_LAST_CALL -> {
                when (intent.action) {
                    io.github.thelony97.orbit.actions.AppShortcuts.ACTION_NEW -> io.github.thelony97.orbit.actions.AppShortcuts.used(this, "new")
                    io.github.thelony97.orbit.actions.AppShortcuts.ACTION_VERLAUF -> io.github.thelony97.orbit.actions.AppShortcuts.used(this, "verlauf")
                }
                pendingShortcut.value = intent.action
                intent.action = Intent.ACTION_MAIN
                return
            }
        }
        intent?.getStringExtra(EXTRA_NEW_NUMBER)?.let { number ->
            pendingNew.value = number to intent.getBooleanExtra(EXTRA_NEW_ORG, false)
            intent.getIntExtra(EXTRA_NOTIF, -1).takeIf { it >= 0 }?.let { io.github.thelony97.orbit.notify.Notifications.cancel(this, it) }
            intent.removeExtra(EXTRA_NEW_NUMBER)
            return
        }
        val id = intent?.getStringExtra(EXTRA_CONTACT) ?: return
        if (intent.data?.toString()?.startsWith("orbit://contact/") == true && intent.action == Intent.ACTION_VIEW) {
            io.github.thelony97.orbit.actions.AppShortcuts.used(this, "fav-$id")
        }
        pendingContact.value = id
        intent.removeExtra(EXTRA_CONTACT)
    }

    override fun onStart() {
        super.onStart()
        AppLock.onStart()
        Notifications.cancel(this, Notifications.ID_DIGEST)
        if (OrbitRepository.data.value?.settings?.onboardingDone == true) Sync.requestNow(this)
    }

    override fun onResume() {
        super.onResume()
        AppLock.onResume()
        // Phone app switched back in the system settings (or declined): Orbit's dial parts go off.
        io.github.thelony97.orbit.call.PhoneRole.tidy(this)
    }

    override fun onStop() {
        super.onStop()
        val settings = OrbitRepository.data.value?.settings
        AppLock.onStop(isChangingConfigurations, settings?.lockEnabled ?: true, settings?.lockDelaySeconds ?: 0)
    }
}
