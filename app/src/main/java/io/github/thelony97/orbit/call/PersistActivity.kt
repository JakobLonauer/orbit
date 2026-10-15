package io.github.thelony97.orbit.call

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.ui.call.PersistAlarmCard
import io.github.thelony97.orbit.ui.theme.OrbitTheme
import kotlinx.coroutines.launch

/**
 * Dranbleiben (PLAN 9 B5): the card at the planned time – over the lock screen, the screen turns
 * on like for an alarm clock. „Anrufen“ starts the tries (as the phone app) or one call. A second
 * plan due while the card is open takes its place.
 */
class PersistActivity : ComponentActivity() {

    companion object {
        const val ACTION_CALL = "io.github.thelony97.orbit.PERSIST_CALL"
        private const val EXTRA_PLAN = "plan"

        fun intent(ctx: Context, plan: Persist.Plan): Intent = Intent(ctx, PersistActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_PLAN, Persist.encode(plan))
    }

    private val shown = mutableStateOf<Persist.Plan?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        // The contact's rules (SIM, „Nummer unterdrücken“) need the data – loaded while the card shows.
        OrbitRepository.init(applicationContext)
        lifecycleScope.launch { runCatching { OrbitRepository.ensureLoaded() } }
        if (!handle(intent, fresh = savedInstanceState == null)) return
        val phoneApp = PhoneRole.held(this)
        setContent {
            OrbitTheme {
                val plan = shown.value ?: return@OrbitTheme
                PersistAlarmCard(
                    name = plan.name,
                    number = plan.number,
                    at = plan.at,
                    phoneApp = phoneApp,
                    onCall = { call(plan) },
                    onLater = {
                        Persist.snooze(this, plan, 10)
                        finish()
                    },
                    onCancel = {
                        Persist.dismissAlarm(this, plan)
                        finish()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent, fresh = true)
    }

    /** False when the activity is done (nothing to show, or „Anrufen“ straight from the notification). */
    private fun handle(i: Intent?, fresh: Boolean): Boolean {
        val plan = Persist.decode(i?.getStringExtra(EXTRA_PLAN))
        if (plan == null) {
            if (shown.value == null) finish()
            return false
        }
        if (i?.action == ACTION_CALL && fresh) {
            call(plan)
            return false
        }
        shown.value = plan
        return true
    }

    private fun call(plan: Persist.Plan) {
        lifecycleScope.launch {
            runCatching { OrbitRepository.ensureLoaded() }
            Persist.start(this@PersistActivity, plan)
            finish()
        }
    }
}
