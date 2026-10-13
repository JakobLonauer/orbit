package io.github.thelony97.orbit.call

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.Call
import android.telecom.CallEndpoint
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import io.github.thelony97.orbit.OrbitApp
import io.github.thelony97.orbit.R
import io.github.thelony97.orbit.domain.Phones
import io.github.thelony97.orbit.notify.Notifications
import io.github.thelony97.orbit.store.CallerIndexFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Telecom hands Orbit its calls here once Jakob made Orbit the phone app (PLAN 3.12). The
 * system keeps ringing (IN_CALL_SERVICE_RINGING = false); Orbit only shows and controls.
 * Every callback is guarded: an error is logged, never thrown back into the call.
 */
class OrbitInCallService : InCallService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = safe {
            CallSession.changed()
            CallNotification.update(this@OrbitInCallService)
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) = safe { CallSession.changed() }
    }

    override fun onCreate() {
        super.onCreate()
        CallSession.service = this
        safe { Notifications.createChannels(this) }
    }

    override fun onDestroy() {
        if (CallSession.service === this) CallSession.service = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) = safe {
        call.registerCallback(callback)
        CallSession.add(call)
        lookup(call)
        CallNotification.update(this)
        // Ringing: the notification's full-screen intent opens the screen. Without notification
        // permission (or for an outgoing call) open it directly – a call must never go unseen.
        if (CallSession.stateOf(call) != Call.STATE_RINGING || !Notifications.canPost(this)) {
            startActivity(CallActivity.intent(this))
        }
    }

    override fun onCallRemoved(call: Call) = safe {
        call.unregisterCallback(callback)
        CallSession.remove(call, CallSession.cards.value[CallSession.numberOf(call)])
        CallNotification.update(this)
    }

    override fun onMuteStateChanged(isMuted: Boolean) = safe {
        CallSession.audio.value = CallSession.audio.value.copy(muted = isMuted)
    }

    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) = safe {
        CallSession.audio.value = CallSession.audio.value.copy(speaker = callEndpoint.endpointType == CallEndpoint.TYPE_SPEAKER)
    }

    /** Who is calling, from the small caller index (no full load), then refresh screen and notification. */
    private fun lookup(call: Call) {
        val number = CallSession.numberOf(call)
        if (number.isBlank()) return
        scope.launch {
            val card = withContext(Dispatchers.IO) {
                runCatching {
                    val iso = Phones.countryIso(this@OrbitInCallService)
                    CallerIndexFile.read(this@OrbitInCallService)?.lookup(number) { Phones.normalize(it, iso) }
                }.getOrNull()
            }
            CallSession.cards.value = CallSession.cards.value + (number to card)
            CallSession.changed()
            safe { CallNotification.update(this@OrbitInCallService) }
        }
    }

    private inline fun safe(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            runCatching { (application as? OrbitApp)?.errorLog?.log("Anruf", t) }
        }
    }
}

/** The call as a notification: answer/decline while ringing, hang up while talking. */
object CallNotification {
    const val ID = 1004

    fun update(ctx: Context) {
        val call = CallSession.primary()
        if (call == null) {
            NotificationManagerCompat.from(ctx).cancel(ID)
            return
        }
        val state = CallSession.stateOf(call)
        val number = CallSession.numberOf(call)
        val card = CallSession.cards.value[number]
        val name = card?.name?.takeIf { it.isNotBlank() } ?: number.ifBlank { "Unbekannt" }
        val person = Person.Builder().setName(name).setImportant(card != null).build()
        val screen = PendingIntent.getActivity(ctx, ID, CallActivity.intent(ctx), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val style = if (state == Call.STATE_RINGING) {
            NotificationCompat.CallStyle.forIncomingCall(person, CallActionReceiver.pending(ctx, CallActionReceiver.DECLINE), CallActivity.answerIntent(ctx))
        } else {
            NotificationCompat.CallStyle.forOngoingCall(person, CallActionReceiver.pending(ctx, CallActionReceiver.HANG_UP))
        }
        val line = when (state) {
            Call.STATE_RINGING -> card?.role?.takeIf { it.isNotBlank() } ?: "Anruf"
            Call.STATE_HOLDING -> "Gehalten"
            Call.STATE_ACTIVE -> "Im Gespräch"
            else -> "Wählt …"
        }
        // Lock screen: name and role only (PLAN 3.12), whatever the user's lock-screen setting says.
        val public = NotificationCompat.Builder(ctx, Notifications.CH_INCALL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(name)
            .setContentText(card?.role.orEmpty())
        val builder = NotificationCompat.Builder(ctx, Notifications.CH_INCALL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFA78BFF.toInt())
            .setStyle(style)
            .setContentText(line)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public.build())
        val connectedAt = runCatching { call.details.connectTimeMillis }.getOrDefault(0L)
        if (state == Call.STATE_ACTIVE && connectedAt > 0) builder.setUsesChronometer(true).setWhen(connectedAt).setShowWhen(true)
        if (!Notifications.canPost(ctx)) return
        try {
            NotificationManagerCompat.from(ctx).notify(ID, builder.build())
        } catch (_: Exception) {
            // Never let a notification problem reach the call; the screen still opens.
        }
    }
}

/** Decline / hang up from the notification (answering opens the screen, see [CallActivity]). */
class CallActionReceiver : BroadcastReceiver() {
    companion object {
        const val DECLINE = "io.github.thelony97.orbit.CALL_DECLINE"
        const val HANG_UP = "io.github.thelony97.orbit.CALL_HANG_UP"

        fun pending(ctx: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            ctx,
            if (action == DECLINE) 1 else 2,
            Intent(ctx, CallActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DECLINE -> CallSession.decline()
            HANG_UP -> CallSession.hangUp()
        }
    }
}
