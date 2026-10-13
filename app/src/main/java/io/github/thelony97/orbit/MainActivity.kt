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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_NEW_NUMBER)?.let { number ->
            pendingNew.value = number to intent.getBooleanExtra(EXTRA_NEW_ORG, false)
            intent.getIntExtra(EXTRA_NOTIF, -1).takeIf { it >= 0 }?.let { io.github.thelony97.orbit.notify.Notifications.cancel(this, it) }
            intent.removeExtra(EXTRA_NEW_NUMBER)
            return
        }
        val id = intent?.getStringExtra(EXTRA_CONTACT) ?: return
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
    }

    override fun onStop() {
        super.onStop()
        val settings = OrbitRepository.data.value?.settings
        AppLock.onStop(isChangingConfigurations, settings?.lockEnabled ?: true, settings?.lockDelaySeconds ?: 0)
    }
}
