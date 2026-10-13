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
        handle(intent)
        setContent { OrbitTheme { CallRoute(onDone = { finish() }) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        if (i?.action == ACTION_ANSWER) CallSession.answer()
    }
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
    var phoneUnlocked by remember { mutableStateOf(unlocked(ctx)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var done by remember { mutableStateOf(emptySet<String>()) }
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
    val ui = if (call != null) {
        val state = CallSession.stateOf(call)
        val number = CallSession.numberOf(call)
        val connectedAt = runCatching { call.details.connectTimeMillis }.getOrDefault(0L)
        CallUi(
            number = number,
            card = cards[number],
            active = state != Call.STATE_RINGING,
            dialing = CallPick.isOutgoing(state),
            elapsed = if (connectedAt > 0) ((now - connectedAt) / 1000).coerceAtLeast(0) else 0,
            muted = audio.muted,
            speaker = audio.speaker,
            onHold = state == Call.STATE_HOLDING,
            phoneUnlocked = phoneUnlocked,
            doneTopics = done,
        )
    } else {
        after!!
        if (phoneUnlocked && after.card != null) CallSession.noteOfferedAt = System.currentTimeMillis()
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

    fun finishAfter(note: String?) {
        val e = CallSession.ended.value
        val ticked = done
        CallSession.ended.value = null
        if (e != null) AppWork.launch { CallNotes.save(ctx.applicationContext, e, note, ticked) }
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
        override fun toggleTopic(topic: String) { done = if (topic in done) done - topic else done + topic }
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
    }
    CallScreen(ui, actions, now)
}

/** After the call: ticked topics and the note line go into Orbit (PLAN 3.12 „Danach“). */
object CallNotes {
    suspend fun save(ctx: Context, e: CallSession.Ended, note: String?, ticked: Set<String>) {
        val contactId = e.card?.contactId ?: return
        OrbitRepository.ensureLoaded()
        if (ticked.isNotEmpty()) {
            OrbitRepository.update { d ->
                d.copy(contacts = d.contacts.map { c ->
                    if (c.id != contactId) c
                    else c.copy(topics = c.topics.map { if (it.text in ticked && !it.done) it.copy(done = true) else it })
                })
            }
        }
        val text = note?.trim().orEmpty()
        if (text.isEmpty()) {
            OrbitRepository.flush()
            return
        }
        // The call log entry appears shortly after hanging up; wait for it, then add the note.
        repeat(4) { attempt ->
            delay(if (attempt == 0) 1500L else 3000L)
            runCatching { SyncEngine.syncAll(ctx) }
            val c = OrbitRepository.snapshot().contacts.firstOrNull { it.id == contactId } ?: return
            val entry = c.entries
                .filter { it.typeId == TypeIds.CALL && it.at >= e.connectedAt - 120_000 }
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
                return
            }
        }
        // No call log entry (no permission, slow phone): the note is kept as its own call entry.
        OrbitRepository.update { d ->
            d.copy(contacts = d.contacts.map { x ->
                if (x.id != contactId) x
                else x.copy(entries = x.entries + Entry(
                    id = newId(), contactId = contactId, typeId = TypeIds.CALL, at = e.connectedAt, note = text,
                    source = EntrySource.MANUAL, durationSec = (e.endedAt - e.connectedAt) / 1000,
                ))
            })
        }
        OrbitRepository.flush()
    }
}
