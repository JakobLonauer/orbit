package io.github.thelony97.orbit.call

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.Call
import android.telecom.DisconnectCause
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.thelony97.orbit.R
import io.github.thelony97.orbit.core.domain.Redial
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.notify.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Dranbleiben (PLAN 9 B5): „Ordination Huber · um 7:30 anrufen“. At the time a full-screen card
 * like an alarm. As the phone app Orbit then dials again after 15 s while the line is busy or an
 * announcement hangs up (max. 30 tries, any time stoppable); once through the phone vibrates and
 * the call screen is in front. Without the phone role only the card with „Anrufen“.
 */
object Persist {

    const val CH_ALARM = "persist-alarm"
    const val CH_LIVE = "persist-live"
    private const val ID_LIVE = 1008
    private const val ID_GAVE_UP = 1010
    /** One alarm notification per plan (two plans at 7:30 must not replace each other). */
    private const val ID_ALARM_BASE = 11_000
    /** A placed try that Telecom never hands over is given up after this long. */
    private const val DIAL_TIMEOUT_MS = 20_000L
    private const val TEAL = 0xFF5EE6CF.toInt()

    /** A call Jakob wants to make at [at]. */
    @Serializable
    data class Plan(val id: String, val number: String, val name: String, val contactId: String? = null, val at: Long)

    enum class Phase {
        /** A try is being placed or rings at the other end. */
        DIALING,
        /** Someone answered (announcements hang up within 20 s and count as busy). */
        TALKING,
        /** Busy: the next try in [Run.nextAt]. */
        PAUSE,
    }

    data class Run(
        val plan: Plan,
        /** Tries placed so far. */
        val tries: Int,
        val phase: Phase,
        /** PAUSE: when the next try dials. */
        val nextAt: Long = 0L,
        val startedAt: Long,
        /** How the last try ended („Besetzt“, „Bandansage“) for the pause card. */
        val last: String = "",
    )

    /** Busy without a run: the call screen offers „Dranbleiben“ once. */
    data class Offer(val number: String, val name: String, val contactId: String?)

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Plan.serializer())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _run = MutableStateFlow<Run?>(null)
    val run: StateFlow<Run?> = _run.asStateFlow()

    private val _offer = MutableStateFlow<Offer?>(null)
    val offer: StateFlow<Offer?> = _offer.asStateFlow()

    private val _planned = MutableStateFlow<List<Plan>>(emptyList())
    /** The planned calls, soonest first (for the contact page). */
    val planned: StateFlow<List<Plan>> = _planned.asStateFlow()

    /** The try being placed now (identity: Telecom hands over the same object). */
    private var current: Call? = null
    private var timer: Job? = null
    private var watchdog: Job? = null

    private fun alarmId(plan: Plan) = ID_ALARM_BASE + (plan.id.hashCode() and 0x0FFF)

    /** Same number, loosely: the last digits (up to eight) agree; short numbers in full. */
    internal fun sameDigits(a: String, b: String): Boolean {
        val x = a.filter(Char::isDigit)
        val y = b.filter(Char::isDigit)
        if (x.length < 3 || y.length < 3) return false
        val n = minOf(8, x.length, y.length)
        return x.takeLast(n) == y.takeLast(n)
    }

    // ---------------------------------------------------------------- planned calls

    private fun file(ctx: Context) = File(ctx.noBackupFilesDir, "persist-plans.json")

    @Synchronized
    private fun read(ctx: Context): List<Plan> =
        runCatching { json.decodeFromString(serializer, file(ctx).readText()) }.getOrDefault(emptyList())

    @Synchronized
    private fun write(ctx: Context, plans: List<Plan>) {
        val sorted = plans.sortedBy { it.at }
        runCatching { file(ctx).writeText(json.encodeToString(serializer, sorted)) }
        _planned.value = sorted
    }

    /** The planned calls from the file (a widget in a fresh process has no [planned] yet). */
    fun all(ctx: Context): List<Plan> = read(ctx).sortedBy { it.at }

    fun encode(plan: Plan): String = json.encodeToString(Plan.serializer(), plan)

    fun decode(text: String?): Plan? = text?.let { runCatching { json.decodeFromString(Plan.serializer(), it) }.getOrNull() }

    /** Reads the plans and sets their alarms again (app start, boot, time change). */
    @Synchronized
    fun restore(ctx: Context) {
        val now = System.currentTimeMillis()
        // Missed by more than an hour (phone off): gone, nothing rings late.
        val plans = read(ctx).filter { it.at > now - 3_600_000L }
        write(ctx, plans)
        plans.forEach { arm(ctx, it) }
    }

    /** „um 7:30 anrufen“: kept on the phone, an exact alarm at [at]. */
    @Synchronized
    fun plan(ctx: Context, number: String, name: String, contactId: String?, at: Long): Plan {
        val plan = Plan(newId(), number, name, contactId, at)
        // One plan per contact and number: a new time replaces the old one.
        val all = read(ctx)
        val old = all.filter { it.number == number && it.contactId == contactId }
        old.forEach { disarm(ctx, it) }
        write(ctx, all - old.toSet() + plan)
        arm(ctx, plan)
        return plan
    }

    @Synchronized
    fun cancel(ctx: Context, id: String) {
        val plans = read(ctx)
        plans.filter { it.id == id }.forEach { disarm(ctx, it) }
        write(ctx, plans.filter { it.id != id })
    }

    private fun alarmIntent(ctx: Context, plan: Plan): PendingIntent = PendingIntent.getBroadcast(
        ctx,
        plan.id.hashCode(),
        Intent(ctx, PersistReceiver::class.java).setAction(PersistReceiver.ACTION_DUE).putExtra(PersistReceiver.EXTRA_ID, plan.id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun arm(ctx: Context, plan: Plan) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val show = PendingIntent.getActivity(
            ctx, plan.id.hashCode(), PersistActivity.intent(ctx, plan),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        runCatching {
            // An alarm clock: exact, through Doze, and the phone shows the time like a wake-up alarm.
            if (am.canScheduleExactAlarms()) am.setAlarmClock(AlarmManager.AlarmClockInfo(plan.at, show), alarmIntent(ctx, plan))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.at, alarmIntent(ctx, plan))
        }
    }

    private fun disarm(ctx: Context, plan: Plan) {
        runCatching { ctx.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(ctx, plan)) }
    }

    /** The alarm went off: the plan leaves the list, the card comes up. */
    fun due(ctx: Context, id: String) {
        val plan = synchronized(this) {
            val plans = read(ctx)
            plans.firstOrNull { it.id == id }?.also { write(ctx, plans.filter { p -> p.id != id }) }
        } ?: return
        alarm(ctx, plan)
    }

    /** „10 Min später“ on the card. */
    fun snooze(ctx: Context, plan: Plan, minutes: Int) {
        dismissAlarm(ctx, plan)
        plan(ctx, plan.number, plan.name, plan.contactId, System.currentTimeMillis() + minutes * 60_000L)
    }

    fun dismissAlarm(ctx: Context, plan: Plan) = NotificationManagerCompat.from(ctx).cancel(alarmId(plan))

    // ---------------------------------------------------------------- notifications

    private fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CH_ALARM) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_ALARM, "Dranbleiben", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Geplante Anrufe zur Uhrzeit"
                    setSound(
                        android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM),
                        android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).build(),
                    )
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 300, 500)
                },
            )
        }
        if (nm.getNotificationChannel(CH_LIVE) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_LIVE, "Dranbleiben läuft", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Versuche und Countdown bis zum nächsten"
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun post(ctx: Context, id: Int, builder: NotificationCompat.Builder) {
        if (!Notifications.canPost(ctx)) return
        runCatching {
            ensureChannels(ctx)
            NotificationManagerCompat.from(ctx).notify(id, builder.build())
        }
    }

    private fun alarm(ctx: Context, plan: Plan) {
        val id = alarmId(plan)
        val open = PendingIntent.getActivity(ctx, id, PersistActivity.intent(ctx, plan), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val call = PendingIntent.getActivity(
            ctx, id + 0x1000, PersistActivity.intent(ctx, plan).setAction(PersistActivity.ACTION_CALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(ctx, CH_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(TEAL)
            .setContentTitle(plan.name)
            .setContentText("Jetzt anrufen · " + io.github.thelony97.orbit.util.Fmt.time(plan.at))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .addAction(0, "Anrufen", call)
            .addAction(0, "Abbrechen", PersistReceiver.dismiss(ctx, plan, id))
            .setAutoCancel(true)
            .setTimeoutAfter(30 * 60_000L)
        post(ctx, id, builder)
    }

    /** „Versuch 7 · 07:31“ as a live chip; in the pause the 15 s count down. */
    private fun live(ctx: Context) {
        val r = _run.value
        if (r == null || r.phase == Phase.TALKING) {
            NotificationManagerCompat.from(ctx).cancel(ID_LIVE)
            return
        }
        val open = PendingIntent.getActivity(ctx, ID_LIVE, CallActivity.intent(ctx), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = when (r.phase) {
            Phase.PAUSE -> listOf(r.last, "Versuch ${r.tries + 1}").filter { it.isNotBlank() }.joinToString(" · ")
            else -> "Versuch ${r.tries} · seit " + io.github.thelony97.orbit.util.Fmt.time(r.startedAt)
        }
        val builder = NotificationCompat.Builder(ctx, CH_LIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(TEAL)
            .setContentTitle(r.plan.name)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(open)
            .addAction(0, "Abbrechen", PersistReceiver.pending(ctx, PersistReceiver.ACTION_STOP))
            .setRequestPromotedOngoing(true)
            .setShortCriticalText("Versuch ${if (r.phase == Phase.PAUSE) r.tries + 1 else r.tries}")
        if (r.phase == Phase.PAUSE) {
            builder.setUsesChronometer(true).setChronometerCountDown(true).setWhen(r.nextAt).setShowWhen(true)
        } else {
            builder.setWhen(r.startedAt).setShowWhen(true)
        }
        post(ctx, ID_LIVE, builder)
    }

    // ---------------------------------------------------------------- the run (phone app)

    /** „Anrufen“ on the card (or „Dranbleiben“ after busy): the first try. */
    fun start(ctx: Context, plan: Plan) {
        val app = ctx.applicationContext
        dismissAlarm(app, plan)
        _offer.value = null
        if (!PhoneRole.held(app)) {
            // Without the phone role Orbit cannot see the line: one ordinary call – with the
            // contact's own rules (SIM, „Nummer unterdrücken“).
            val doc = OrbitRepository.data.value
            val contact = plan.contactId?.let { id -> doc?.contacts?.firstOrNull { it.id == id } }
            if (contact != null && doc != null) io.github.thelony97.orbit.actions.ContactActions.call(ctx, contact, plan.number, doc.settings)
            else io.github.thelony97.orbit.actions.ContactActions.callNumber(ctx, plan.number)
            return
        }
        timer?.cancel()
        current = null
        _run.value = Run(plan, tries = 0, phase = Phase.DIALING, startedAt = System.currentTimeMillis())
        dial(app)
    }

    /** „Dranbleiben“ right after a busy call (the offer on the call screen). */
    fun startNow(ctx: Context, number: String, name: String, contactId: String?) =
        start(ctx, Plan(newId(), number, name, contactId, System.currentTimeMillis()))

    /** „Jetzt“ in the pause: no more waiting. */
    fun now(ctx: Context) {
        if (_run.value?.phase != Phase.PAUSE) return
        timer?.cancel()
        dial(ctx.applicationContext)
    }

    /** Jakob stops it („Abbrechen“). The running try, if any, is not touched. */
    fun stop(ctx: Context) {
        timer?.cancel()
        timer = null
        watchdog?.cancel()
        watchdog = null
        current = null
        _run.value = null
        live(ctx.applicationContext)
    }

    fun clearOffer() {
        _offer.value = null
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun dial(ctx: Context) {
        val r = _run.value ?: return
        // Never over another call: wait until the line is free.
        if (CallSession.calls.value.isNotEmpty()) {
            timer = scope.launch {
                delay(1_000)
                dial(ctx)
            }
            return
        }
        _run.value = r.copy(tries = r.tries + 1, phase = Phase.DIALING, nextAt = 0L)
        current = null
        live(ctx)
        // Telecom hands the try over within moments; if it never does, the run ends.
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(DIAL_TIMEOUT_MS)
            if (_run.value?.phase == Phase.DIALING && current == null) stop(ctx)
        }
        val placed = runCatching {
            check(ctx.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED)
            val extras = Bundle()
            // Dual-SIM pro Kontakt (PLAN 3.12) holds for every try.
            val doc = OrbitRepository.data.value
            val contact = r.plan.contactId?.let { id -> doc?.contacts?.firstOrNull { it.id == id } }
            val sim = contact?.let { Sims.forContact(it, doc?.groups.orEmpty()) }
            Sims.handle(ctx, sim)?.let { extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) }
            // Wählregeln (PLAN 9 B10) hold for every try.
            val dial = io.github.thelony97.orbit.domain.Phones.dial(ctx, r.plan.number, hide = contact?.hideNumber == true)
            ctx.getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", dial, null), extras)
        }.isSuccess
        if (!placed) stop(ctx)
    }

    /**
     * Telecom handed over a call. While a try is being placed the first outgoing call is it (the
     * number can come back in another form); the planned number calling in ends the run – they
     * called back.
     */
    fun onCallAdded(ctx: Context, call: Call) {
        val r = _run.value
        if (r == null) {
            _offer.value = null
            return
        }
        val outgoing = runCatching { call.details.callDirection == Call.Details.DIRECTION_OUTGOING }.getOrDefault(false)
        when {
            !outgoing && sameDigits(r.plan.number, CallSession.numberOf(call)) -> stop(ctx)
            outgoing && r.phase == Phase.DIALING && current == null -> {
                current = call
                watchdog?.cancel()
            }
        }
    }

    /** Someone answered: the phone tells Jakob, the call screen comes to the front. */
    fun onActive(ctx: Context, call: Call) {
        val r = _run.value ?: return
        if (call !== current || r.phase == Phase.TALKING) return
        _run.value = r.copy(phase = Phase.TALKING)
        live(ctx)
        vibrate(ctx)
        runCatching { ctx.startActivity(CallActivity.intent(ctx)) }
    }

    private fun endOf(call: Call): Redial.End = when (runCatching { call.details.disconnectCause?.code }.getOrNull()) {
        DisconnectCause.BUSY -> Redial.End.BUSY
        DisconnectCause.REMOTE -> Redial.End.REMOTE
        DisconnectCause.LOCAL, DisconnectCause.CANCELED, DisconnectCause.REJECTED -> Redial.End.LOCAL
        DisconnectCause.ERROR, DisconnectCause.RESTRICTED -> Redial.End.ERROR
        else -> Redial.End.OTHER
    }

    /**
     * A call ended. True when it was a try that leads to the next one – then there is no
     * after-call screen for it. A busy call of Jakob's own (no run) leaves the offer.
     */
    fun onRemoved(ctx: Context, call: Call): Boolean {
        val end = endOf(call)
        val connected = runCatching { call.details.connectTimeMillis }.getOrDefault(0L)
        val talked = if (connected > 0) System.currentTimeMillis() - connected else 0L
        val r = _run.value
        if (r == null || call !== current) {
            if (end == Redial.End.BUSY && connected == 0L) offerAfter(call)
            return false
        }
        current = null
        return when (Redial.next(end, talked, r.tries)) {
            Redial.Next.AGAIN -> {
                pause(ctx, r, if (end == Redial.End.BUSY) "Besetzt" else if (connected > 0) "Bandansage" else "Keine Antwort")
                true
            }
            Redial.Next.GIVE_UP -> {
                stop(ctx)
                gaveUp(ctx, r)
                true
            }
            Redial.Next.DONE, Redial.Next.STOP -> {
                stop(ctx)
                false
            }
        }
    }

    /** Jakob's own outgoing call was busy: the call screen offers „Dranbleiben“. */
    private fun offerAfter(call: Call) {
        val outgoing = runCatching { call.details.callDirection == Call.Details.DIRECTION_OUTGOING }.getOrDefault(false)
        if (!outgoing || CallSession.calls.value.size > 1 || !CallSession.screenVisible) return
        val number = CallSession.numberOf(call).ifBlank { return }
        val card = CallSession.cards.value[number]
        _offer.value = Offer(number, card?.name?.takeIf { it.isNotBlank() } ?: number, card?.contactId)
    }

    private fun pause(ctx: Context, r: Run, last: String) {
        val next = System.currentTimeMillis() + Redial.PAUSE_MS
        _run.value = r.copy(phase = Phase.PAUSE, nextAt = next, last = last)
        live(ctx)
        timer?.cancel()
        timer = scope.launch {
            delay(Redial.PAUSE_MS)
            dial(ctx.applicationContext)
        }
    }

    private fun gaveUp(ctx: Context, r: Run) {
        val builder = NotificationCompat.Builder(ctx, CH_LIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(TEAL)
            .setContentTitle(r.plan.name)
            .setContentText("Nicht durchgekommen · ${r.tries} Versuche")
            .setContentIntent(Notifications.openContact(ctx, r.plan.contactId, ID_GAVE_UP))
            .setAutoCancel(true)
        post(ctx, ID_GAVE_UP, builder)
    }

    /** „Frei“: three strong pulses, so the phone on the table is heard. */
    private fun vibrate(ctx: Context) = runCatching {
        val vibrator = ctx.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator ?: return@runCatching
        vibrator.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 350, 120, 350, 120, 700), -1))
    }
}

/** The alarm and the notification buttons of Dranbleiben. */
class PersistReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DUE = "io.github.thelony97.orbit.PERSIST_DUE"
        const val ACTION_STOP = "io.github.thelony97.orbit.PERSIST_STOP"
        const val ACTION_DISMISS = "io.github.thelony97.orbit.PERSIST_DISMISS"
        const val EXTRA_ID = "id"
        private const val EXTRA_PLAN = "plan"

        fun pending(ctx: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            ctx,
            action.hashCode(),
            Intent(ctx, PersistReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** „Abbrechen“ on one plan's alarm. */
        fun dismiss(ctx: Context, plan: Persist.Plan, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
            ctx,
            requestCode,
            Intent(ctx, PersistReceiver::class.java).setAction(ACTION_DISMISS).putExtra(EXTRA_PLAN, Persist.encode(plan)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DUE -> intent.getStringExtra(EXTRA_ID)?.let { Persist.due(context, it) }
            ACTION_STOP -> Persist.stop(context)
            ACTION_DISMISS -> Persist.decode(intent.getStringExtra(EXTRA_PLAN))?.let { Persist.dismissAlarm(context, it) }
        }
    }
}
