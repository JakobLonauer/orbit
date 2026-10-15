package io.github.thelony97.orbit.call

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.thelony97.orbit.ui.call.PersistAlarmCard
import io.github.thelony97.orbit.ui.theme.OrbitTheme

/**
 * Dranbleiben (PLAN 9 B5): the card at the planned time – over the lock screen, the screen turns
 * on like for an alarm clock. „Anrufen“ starts the tries (as the phone app) or one call.
 */
class PersistActivity : ComponentActivity() {

    companion object {
        const val ACTION_CALL = "io.github.thelony97.orbit.PERSIST_CALL"
        private const val EXTRA_PLAN = "plan"

        fun intent(ctx: Context, plan: Persist.Plan): Intent = Intent(ctx, PersistActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_PLAN, Persist.encode(plan))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        val plan = Persist.decode(intent?.getStringExtra(EXTRA_PLAN))
        if (plan == null) {
            finish()
            return
        }
        // „Anrufen“ straight from the notification.
        if (intent?.action == ACTION_CALL && savedInstanceState == null) {
            Persist.start(this, plan)
            finish()
            return
        }
        val phoneApp = PhoneRole.held(this)
        setContent {
            OrbitTheme {
                PersistAlarmCard(
                    name = plan.name,
                    number = plan.number,
                    at = plan.at,
                    phoneApp = phoneApp,
                    onCall = {
                        Persist.start(this, plan)
                        finish()
                    },
                    onLater = {
                        Persist.snooze(this, plan, 10)
                        finish()
                    },
                    onCancel = {
                        Persist.dismissAlarm(this)
                        finish()
                    },
                )
            }
        }
    }
}
