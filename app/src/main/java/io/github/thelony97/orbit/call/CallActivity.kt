package io.github.thelony97.orbit.call

import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telecom.Call
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.thelony97.orbit.core.domain.CallerCard
import io.github.thelony97.orbit.data.Entry
import io.github.thelony97.orbit.data.EntrySource
import io.github.thelony97.orbit.data.Mutations
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.TypeIds
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.domain.Phones
import io.github.thelony97.orbit.notify.CallbackWorker
import io.github.thelony97.orbit.store.CallerIndexFile
import io.github.thelony97.orbit.sync.SyncEngine
import io.github.thelony97.orbit.ui.call.CallActions
import io.github.thelony97.orbit.ui.call.CallScreen
import io.github.thelony97.orbit.ui.call.CallUi
import io.github.thelony97.orbit.ui.components.AppWork
import io.github.thelony97.orbit.ui.theme.OrbitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

/**
 * The call screen while Orbit is the phone app (PLAN 3.12). Its own task, over the phone's lock
 * screen, never behind the Orbit lock („App-Sperre blockiert Anrufe nie“). Details only when the
 * phone is unlocked; the lock screen sees name, photo and role.
 */
class CallActivity : ComponentActivity() {

    companion object {
        const val ACTION_ANSWER = "io.github.thelony97.orbit.CALL_ANSWER"

        fun intent(ctx: Context): Intent = Intent(ctx, CallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** Answer from the notification: opens the screen and answers (no broadcast trampoline). */
        fun answerIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
            ctx, 3, intent(ctx).setAction(ACTION_ANSWER),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        // Only a fresh start answers: after rotation or a theme change the old intent must not
        // answer a waiting call by itself.
        if (savedInstanceState == null) handle(intent)
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(CallSession.version, CallSession.audio) { _, _ -> }.collect { updateProximity() }
        }
        setContent {
            OrbitTheme {
                CallRoute(onDone = {
                    // A new call may have arrived in between: only close when nothing is left.
                    if (CallSession.primary() == null && CallSession.ended.value == null) finish()
                })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStart() {
        super.onStart()
        CallSession.screenVisible = true
    }

    override fun onStop() {
        CallSession.screenVisible = false
        super.onStop()
    }

    /**
     * Näherungssensor (PLAN 3.12): at the ear the screen goes dark, so the cheek presses nothing.
     * Only while talking through the handset – never with speaker, headset or Bluetooth.
     */
    private var proximity: android.os.PowerManager.WakeLock? = null

    private fun updateProximity() {
        val c = CallSession.primary()
        val state = c?.let(CallSession::stateOf)
        val talking = state == Call.STATE_ACTIVE || state == Call.STATE_DIALING || state == Call.STATE_CONNECTING
        val want = talking && CallSession.audio.value.type == android.telecom.CallEndpoint.TYPE_EARPIECE
        runCatching {
            val pm = getSystemService(android.os.PowerManager::class.java) ?: return
            if (!pm.isWakeLockLevelSupported(android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
            val lock = proximity ?: pm.newWakeLock(android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "orbit:call").apply {
                setReferenceCounted(false)
                proximity = this
            }
            if (want && !lock.isHeld) lock.acquire(3 * 60 * 60 * 1000L)
            if (!want && lock.isHeld) lock.release(android.os.PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        }
    }

    override fun onDestroy() {
        // Left with Back from the after-call screen: it is done, other prompts come back.
        runCatching { proximity?.takeIf { it.isHeld }?.release() }
        if (isFinishing) {
            val e = CallSession.ended.value
            e?.card?.contactId?.let { CallSession.noteHandled = it to System.currentTimeMillis() }
            // Typed but not saved (Back): keep it rather than lose it.
            val text = CallSession.draft.trim()
            CallSession.draft = ""
            if (e != null && text.isNotEmpty()) CallNoteWorker.enqueue(applicationContext, e, text)
            CallSession.ended.value = null
        }
        super.onDestroy()
    }

    private fun handle(i: Intent?) {
        if (i?.action == ACTION_ANSWER) CallSession.answer()
        // Consumed: what stays as the activity's intent never answers again.
        setIntent(Intent(this, CallActivity::class.java))
    }
}

/** Unknown number that called before: „3 Anrufe · zuletzt vor 2 Tagen“ (PLAN 3.12), never dismissed ones. */
private fun unknownLine(doc: io.github.thelony97.orbit.data.OrbitData?, number: String, ctx: Context, now: Long): String? {
    if (doc == null || number.isBlank()) return null
    val key = Phones.normalize(number, Phones.countryIso(ctx)) ?: return null
    val u = doc.unknownNumbers.firstOrNull { it.key == key && !it.dismissed && it.calls > 0 } ?: return null
    val zone = java.time.ZoneId.systemDefault()
    val days = java.time.temporal.ChronoUnit.DAYS.between(
        java.time.Instant.ofEpochMilli(u.lastAt).atZone(zone).toLocalDate(),
        java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),
    )
    val times = if (u.calls == 1) "1 Anruf" else "${u.calls} Anrufe"
    return listOfNotNull(times, u.lastAt.takeIf { it > 0 }?.let { "zuletzt " + io.github.thelony97.orbit.core.domain.AtFormat.ago(days) }).joinToString(" · ")
}

private fun unlocked(ctx: Context): Boolean =
    ctx.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != true

@Composable
private fun CallRoute(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val version by CallSession.version.collectAsState()
    val audio by CallSession.audio.collectAsState()
    val ended by CallSession.ended.collectAsState()
    val cards by CallSession.cards.collectAsState()
    val doc by OrbitRepository.data.collectAsState()
    val canBlock = remember { CallSession.canBlock(ctx) }
    var phoneUnlocked by remember { mutableStateOf(unlocked(ctx)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Ticks survive rotation (they are saved at once, the screen must show them too).
    var done by androidx.compose.runtime.saveable.rememberSaveable(
        stateSaver = androidx.compose.runtime.saveable.listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() }),
    ) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(Unit) {
        while (true) {
            phoneUnlocked = unlocked(ctx)
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val call: Call? = remember(version) { CallSession.primary() }
    val after = ended
    if (call == null && after == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    // Rebuilt only when something changes – never by the second (W8); the timer reads [now] itself.
    val stamp = remember(version, after) { System.currentTimeMillis() }
    val connectedAt = remember(version, call) { call?.let { runCatching { it.details.connectTimeMillis }.getOrDefault(0L) } ?: 0L }
    val clock = remember(connectedAt) { { if (connectedAt > 0) ((now - connectedAt) / 1000).coerceAtLeast(0) else 0L } }
    val ui = remember(version, call, after, audio, cards, doc, phoneUnlocked, done) {
    if (call != null) {
        val state = CallSession.stateOf(call)
        val number = CallSession.numberOf(call)
        CallUi(
            number = number,
            card = cards[number],
            active = state != Call.STATE_RINGING,
            dialing = CallPick.isOutgoing(state),
            muted = audio.muted,
            speaker = audio.speaker,
            onHold = state == Call.STATE_HOLDING,
            phoneUnlocked = phoneUnlocked,
            doneTopics = done,
            route = when (audio.type) {
                android.telecom.CallEndpoint.TYPE_SPEAKER -> io.github.thelony97.orbit.ui.call.AudioRoute.SPEAKER
                android.telecom.CallEndpoint.TYPE_BLUETOOTH -> io.github.thelony97.orbit.ui.call.AudioRoute.BLUETOOTH
                android.telecom.CallEndpoint.TYPE_WIRED_HEADSET -> io.github.thelony97.orbit.ui.call.AudioRoute.WIRED
                else -> io.github.thelony97.orbit.ui.call.AudioRoute.EARPIECE
            },
            routeName = audio.name,
            moreRoutes = audio.available.any {
                it.endpointType == android.telecom.CallEndpoint.TYPE_BLUETOOTH || it.endpointType == android.telecom.CallEndpoint.TYPE_WIRED_HEADSET
            },
            unknownLine = if (cards[number] == null) unknownLine(doc, number, ctx, stamp) else null,
            canBlock = canBlock && number.isNotBlank(),
            otherCall = CallSession.other()?.let { o ->
                val n = CallSession.numberOf(o)
                val who = cards[n]?.name?.takeIf { it.isNotBlank() } ?: n.ifBlank { "Anruf" }
                who + when (CallSession.stateOf(o)) {
                    Call.STATE_HOLDING -> " · gehalten"
                    Call.STATE_ACTIVE -> " · im Gespräch"
                    else -> ""
                }
            },
            canMerge = CallSession.canMerge(),
            accounts = if (state == Call.STATE_SELECT_PHONE_ACCOUNT) {
                CallSession.accounts(ctx, call).mapIndexed { i, h -> CallSession.accountLabel(ctx, h, i) }
            } else emptyList(),
        )
    } else {
        after!!
        CallUi(
            number = after.number,
            card = after.card,
            active = true,
            ended = true,
            elapsed = ((after.endedAt - after.connectedAt) / 1000).coerceAtLeast(0),
            phoneUnlocked = phoneUnlocked,
            doneTopics = done,
        )
    }
    }

    fun finishAfter(note: String?) {
        CallSession.draft = ""
        val e = CallSession.ended.value
        e?.card?.contactId?.let { CallSession.noteHandled = it to System.currentTimeMillis() }
        CallSession.ended.value = null
        // WorkManager keeps the note even if the app process ends before the call log entry arrives.
        if (e != null && !note.isNullOrBlank()) CallNoteWorker.enqueue(ctx, e, note)
    }

    val actions = object : CallActions {
        override fun answer() { CallSession.answer() }
        override fun decline() { CallSession.decline() }
        override fun declineWithSms(text: String) { CallSession.declineWithSms(text) }
        override fun remindLater() {
            val c = CallSession.ringing() ?: return
            val number = CallSession.numberOf(c)
            val card = cards[number]
            if (number.isNotBlank()) {
                CallbackWorker.schedule(ctx, card?.name?.ifBlank { null } ?: number, number, card?.contactId, 15)
                Toast.makeText(ctx, "Erinnerung in 15 Min", Toast.LENGTH_SHORT).show()
            }
            CallSession.decline()
        }
        override fun hangUp() { CallSession.hangUp() }
        override fun toggleMute() { CallSession.toggleMute() }
        override fun toggleSpeaker() { CallSession.toggleSpeaker() }
        override fun toggleHold() { CallSession.toggleHold() }
        override fun dtmf(digit: Char) { CallSession.dtmf(digit) }
        override fun pickAccount(index: Int) { CallSession.pickAccount(ctx, index) }
        override fun swap() { CallSession.swap() }
        override fun merge() { CallSession.merge() }
        override fun block() {
            val c = CallSession.ringing() ?: return
            val number = CallSession.numberOf(c)
            AppWork.launch {
                val ok = withContext(Dispatchers.IO) { CallSession.block(ctx, number) }
                Toast.makeText(ctx, if (ok) "Gesperrt" else "Sperren nicht möglich", Toast.LENGTH_SHORT).show()
            }
            CallSession.decline()
        }
        override fun toggleTopic(topic: String) {
            val on = topic !in done
            done = if (on) done + topic else done - topic
            // Saved at once: closing, rotating or a new call never loses a tick.
            val id = ui.card?.contactId ?: return
            AppWork.launch { CallNotes.setTopic(id, topic, on) }
        }
        override fun unarchive() {
            val card = ui.card ?: return
            val number = ui.number
            AppWork.launch {
                OrbitRepository.ensureLoaded()
                OrbitRepository.update { d -> Mutations.unarchive(d, card.contactId) }
                OrbitRepository.flush()
                val fresh = withContext(Dispatchers.IO) {
                    runCatching {
                        val iso = Phones.countryIso(ctx)
                        CallerIndexFile.read(ctx)?.lookup(number) { Phones.normalize(it, iso) }
                    }.getOrNull()
                } ?: card.copy(archived = false)
                CallSession.cards.value = CallSession.cards.value + (number to fresh)
                CallSession.ended.value = CallSession.ended.value?.let { if (it.number == number) it.copy(card = fresh) else it }
                Toast.makeText(ctx, "Zurückgeholt", Toast.LENGTH_SHORT).show()
            }
        }
        override fun saveNote(text: String) = finishAfter(text)
        override fun close() = finishAfter(null)
        override fun createContact(org: Boolean) {
            val number = CallSession.ended.value?.number ?: return
            finishAfter(null)
            // Orbit's edit page with the number, behind the Orbit lock as always.
            runCatching {
                ctx.startActivity(
                    Intent(ctx, io.github.thelony97.orbit.MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra(io.github.thelony97.orbit.MainActivity.EXTRA_NEW_NUMBER, number)
                        .putExtra(io.github.thelony97.orbit.MainActivity.EXTRA_NEW_ORG, org),
                )
            }
        }
        override fun noteDraft(text: String) { CallSession.draft = text }
    }
    CallScreen(ui, actions, stamp, clock = clock)
}

/** After the call: ticked topics go into Orbit at once (PLAN 3.12 „Themen abhaken“). */
object CallNotes {
    suspend fun setTopic(contactId: String, text: String, done: Boolean) {
        OrbitRepository.ensureLoaded()
        OrbitRepository.update { d ->
            d.copy(contacts = d.contacts.map { c ->
                if (c.id != contactId) c
                else c.copy(topics = c.topics.map { if (it.text == text && it.done != done) it.copy(done = done) else it })
            })
        }
        OrbitRepository.flush()
    }
}

/**
 * The note line after a call (PLAN 3.12 „Danach“): waits for the call log entry and adds the note
 * to it. As a WorkManager job it survives the app process ending in between. No entry after a few
 * tries (no call log permission, call shorter than the minimum): the note becomes its own entry.
 */
class CallNoteWorker(ctx: Context, params: androidx.work.WorkerParameters) : androidx.work.CoroutineWorker(ctx, params) {

    companion object {
        private const val CONTACT = "contact"
        private const val CONNECTED = "connected"
        private const val ENDED = "ended"
        private const val NOTE = "note"

        fun enqueue(ctx: Context, e: CallSession.Ended, note: String) {
            val contactId = e.card?.contactId ?: return
            val request = androidx.work.OneTimeWorkRequestBuilder<CallNoteWorker>()
                .setInitialDelay(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setInputData(
                    androidx.work.workDataOf(CONTACT to contactId, CONNECTED to e.connectedAt, ENDED to e.endedAt, NOTE to note.trim()),
                )
                .build()
            androidx.work.WorkManager.getInstance(ctx).enqueueUniqueWork("call-note-${e.connectedAt}", androidx.work.ExistingWorkPolicy.KEEP, request)
        }
    }

    override suspend fun doWork(): Result {
        val contactId = inputData.getString(CONTACT) ?: return Result.success()
        val connectedAt = inputData.getLong(CONNECTED, 0L)
        val endedAt = inputData.getLong(ENDED, connectedAt)
        val text = inputData.getString(NOTE)?.trim().orEmpty()
        if (text.isEmpty()) return Result.success()
        OrbitRepository.init(applicationContext)
        OrbitRepository.ensureLoaded()
        repeat(4) { attempt ->
            if (attempt > 0) delay(3000L)
            runCatching { SyncEngine.syncAll(applicationContext) }
            val c = OrbitRepository.snapshot().contacts.firstOrNull { it.id == contactId } ?: return Result.success()
            val entry = c.entries
                .filter { it.typeId == TypeIds.CALL && it.at >= connectedAt - 120_000 }
                .maxByOrNull { it.at }
            if (entry != null) {
                OrbitRepository.update { d ->
                    d.copy(contacts = d.contacts.map { x ->
                        if (x.id != contactId) x
                        else x.copy(entries = x.entries.map { en ->
                            if (en.id != entry.id) en
                            else en.copy(note = listOf(en.note, text).filter { it.isNotBlank() }.joinToString("\n"))
                        })
                    })
                }
                OrbitRepository.flush()
                return Result.success()
            }
        }
        OrbitRepository.update { d ->
            d.copy(contacts = d.contacts.map { x ->
                if (x.id != contactId) x
                else x.copy(entries = x.entries + Entry(
                    id = newId(), contactId = contactId, typeId = TypeIds.CALL, at = connectedAt, note = text,
                    source = EntrySource.MANUAL, durationSec = (endedAt - connectedAt) / 1000,
                ))
            })
        }
        OrbitRepository.flush()
        return Result.success()
    }
}
