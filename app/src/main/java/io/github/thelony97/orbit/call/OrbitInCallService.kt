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
            // PLAN 9 B4 „Makro wählen“: answered – the hotline gets a moment, then the keys.
            val p = MacroPlayer.pending
            if (state == Call.STATE_ACTIVE && p != null && io.github.thelony97.orbit.core.domain.Herold.sameNumber(p.number, CallSession.numberOf(call))) {
                MacroPlayer.pending = null
                MacroPlayer.start(p.macro, p.org, p.me, delayFirst = 1_500)
            }
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) = safe { CallSession.changed() }

        // Conference: parents, participants and what can be merged change the screen too.
        override fun onParentChanged(call: Call, parent: Call?) = safe { CallSession.changed() }

        override fun onChildrenChanged(call: Call, children: List<Call>) = safe { CallSession.changed() }

        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: List<Call>) = safe { CallSession.changed() }
    }

    override fun onCreate() {
        super.onCreate()
        CallSession.service = this
        safe { CallNotification.ensureChannel(this) }
    }

    /** The system asks to show the running call again (e.g. the phone button during a call). */
    override fun onBringToForeground(showDialpad: Boolean) = safe { startActivity(CallActivity.intent(this)) }

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
        // A call must never go unseen: open the screen directly unless a heads-up notification
        // will certainly appear (unlocked, screen on, channel loud enough, nothing filtered).
        // A call Do Not Disturb silenced stays silent – the screen does not light up for it.
        val silenced = runCatching {
            call.details.extras?.getBoolean("android.telecom.extra.IS_SUPPRESSED_BY_DO_NOT_DISTURB", false) == true
        }.getOrDefault(false)
        if (!silenced && (CallSession.stateOf(call) != Call.STATE_RINGING || !CallNotification.headsUpCertain(this))) {
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
        CallSession.audio.value = CallSession.audio.value.copy(
            speaker = callEndpoint.endpointType == CallEndpoint.TYPE_SPEAKER,
            type = callEndpoint.endpointType,
            name = callEndpoint.endpointName.toString(),
            current = callEndpoint,
        )
    }

    override fun onAvailableCallEndpointsChanged(availableEndpoints: List<CallEndpoint>) = safe {
        CallSession.audio.value = CallSession.audio.value.copy(available = availableEndpoints.toList())
    }

    /** Who is calling, from the small caller index (no full load), then refresh screen and notification. */
    private fun lookup(call: Call) {
        val number = CallSession.numberOf(call)
        if (number.isBlank()) return
        scope.launch {
            var extension: String? = null
            val card = withContext(Dispatchers.IO) {
                runCatching {
                    val iso = Phones.countryIso(this@OrbitInCallService)
                    val index = CallerIndexFile.read(this@OrbitInCallService)
                    val normalize: (String) -> String? = { Phones.normalize(it, iso) }
                    index?.lookup(number, normalize)?.also { extension = index.extension(number, normalize) }
                }.getOrNull()?.also { CallNotification.preload(this@OrbitInCallService, it.photo) }
            }
            extension?.let { CallSession.extensions.value = CallSession.extensions.value + (number to it) }
            CallSession.cards.value = CallSession.cards.value + (number to card)
            CallSession.changed()
            safe { CallNotification.update(this@OrbitInCallService) }
            // PLAN 9 B1: nobody Orbit knows – place and kind at once, Herold in the background.
            if (card == null) {
                Materialize.start(this@OrbitInCallService, number, scope) {
                    CallSession.changed()
                    safe { CallNotification.update(this@OrbitInCallService) }
                }
            }
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

    /** Caller photos, decoded once per call off the main thread (a quarter of the stored size). */
    private val photos = java.util.concurrent.ConcurrentHashMap<String, androidx.core.graphics.drawable.IconCompat>()

    /** Decodes the photo; call from a background thread (see the lookup in [OrbitInCallService]). */
    fun preload(ctx: Context, name: String?) {
        if (name.isNullOrBlank() || photos.containsKey(name)) return
        runCatching {
            val file = java.io.File(java.io.File(ctx.filesDir, "photos"), name)
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)?.let { photos[name] = androidx.core.graphics.drawable.IconCompat.createWithBitmap(it) }
        }
    }

    private fun photoIcon(@Suppress("UNUSED_PARAMETER") ctx: Context, name: String?): androidx.core.graphics.drawable.IconCompat? =
        name?.takeIf { it.isNotBlank() }?.let { photos[it] }

    /** The ringing call the notification last alerted for; a new one (call waiting) alerts again. */
    private var alertedFor: Call? = null

    /** Created only once Orbit is used as the phone app, so it does not show up before. */
    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(Notifications.CH_INCALL) != null) return
        nm.createNotificationChannel(
            android.app.NotificationChannel(Notifications.CH_INCALL, "Anrufe", android.app.NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Laufende und eingehende Anrufe, wenn Orbit die Telefon-App ist"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            },
        )
    }

    /**
     * Only true when a ringing call will surely show as heads-up on an unlocked, lit screen.
     * Locked, screen off, Do Not Disturb, a muted channel, no full-screen permission or a second
     * call: the screen is opened directly instead.
     */
    fun headsUpCertain(ctx: Context): Boolean = runCatching {
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java) ?: return@runCatching false
        val keyguard = ctx.getSystemService(android.app.KeyguardManager::class.java)
        val power = ctx.getSystemService(android.os.PowerManager::class.java)
        Notifications.canPost(ctx) &&
            keyguard?.isKeyguardLocked == false &&
            power?.isInteractive == true &&
            nm.canUseFullScreenIntent() &&
            nm.currentInterruptionFilter == android.app.NotificationManager.INTERRUPTION_FILTER_ALL &&
            (nm.getNotificationChannel(Notifications.CH_INCALL)?.importance ?: 0) >= android.app.NotificationManager.IMPORTANCE_HIGH &&
            CallSession.calls.value.size <= 1
    }.getOrDefault(false)

    // POST_NOTIFICATIONS is checked by Notifications.canPost right before notify (lint cannot see it).
    @android.annotation.SuppressLint("MissingPermission")
    fun update(ctx: Context) {
        val call = CallSession.primary()
        if (call == null) {
            alertedFor = null
            photos.clear()
            NotificationManagerCompat.from(ctx).cancel(ID)
            return
        }
        val state = CallSession.stateOf(call)
        val number = CallSession.numberOf(call)
        val card = CallSession.cards.value[number]
        // PLAN 9 B1: an unknown number shows what Herold or the number itself tells.
        val unknown = if (card == null) Materialize.of(number) else null
        val name = card?.name?.takeIf { it.isNotBlank() } ?: unknown?.hit?.name ?: number.ifBlank { "Unbekannt" }
        // The tel: URI lets Do Not Disturb recognise starred contacts like the system dialer does.
        val person = Person.Builder().setName(name).setImportant(card != null)
            .apply { if (number.isNotBlank()) setUri("tel:$number") }
            .apply { photoIcon(ctx, card?.photo)?.let { setIcon(it) } }
            .build()
        val screen = PendingIntent.getActivity(ctx, ID, CallActivity.intent(ctx), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val style = if (state == Call.STATE_RINGING) {
            NotificationCompat.CallStyle.forIncomingCall(person, CallActionReceiver.pending(ctx, CallActionReceiver.DECLINE), CallActivity.answerIntent(ctx))
        } else {
            NotificationCompat.CallStyle.forOngoingCall(person, CallActionReceiver.pending(ctx, CallActionReceiver.HANG_UP))
        }
        val line = when (state) {
            Call.STATE_RINGING -> card?.role ?: listOfNotNull(unknown?.hit?.category, unknown?.info?.line).filter { it.isNotBlank() }.joinToString(" · ")
            Call.STATE_HOLDING -> "Gehalten"
            Call.STATE_ACTIVE -> "Im Gespräch"
            else -> "Wählt …"
        }
        // Lock screen: name and role only (PLAN 3.12), whatever the user's lock-screen setting says –
        // but with the same buttons, so a call can be answered or ended from there too.
        val publicPerson = Person.Builder().setName(name).apply { if (number.isNotBlank()) setUri("tel:$number") }.build()
        val publicStyle = if (state == Call.STATE_RINGING) {
            NotificationCompat.CallStyle.forIncomingCall(publicPerson, CallActionReceiver.pending(ctx, CallActionReceiver.DECLINE), CallActivity.answerIntent(ctx))
        } else {
            NotificationCompat.CallStyle.forOngoingCall(publicPerson, CallActionReceiver.pending(ctx, CallActionReceiver.HANG_UP))
        }
        val public = NotificationCompat.Builder(ctx, Notifications.CH_INCALL)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(publicStyle)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentText(card?.role.orEmpty())
        val builder = NotificationCompat.Builder(ctx, Notifications.CH_INCALL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFA78BFF.toInt())
            .setStyle(style)
            .setContentText(line)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            // Updates of the same call stay quiet; a new ringing call (call waiting) alerts again.
            .setOnlyAlertOnce(!(state == Call.STATE_RINGING && alertedFor !== call))
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public.build())
        val connectedAt = runCatching { call.details.connectTimeMillis }.getOrDefault(0L)
        if (state == Call.STATE_ACTIVE && connectedAt > 0) builder.setUsesChronometer(true).setWhen(connectedAt).setShowWhen(true)
        if (!Notifications.canPost(ctx)) return
        try {
            ensureChannel(ctx)
            if (state == Call.STATE_RINGING && alertedFor !== call) {
                NotificationManagerCompat.from(ctx).cancel(ID)
                alertedFor = call
            }
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
