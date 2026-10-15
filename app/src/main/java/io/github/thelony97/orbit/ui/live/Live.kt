package io.github.thelony97.orbit.ui.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.thelony97.orbit.call.CallSession
import io.github.thelony97.orbit.call.Persist
import io.github.thelony97.orbit.core.domain.LiveMeeting
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import kotlinx.coroutines.delay

/** What the island shows – one source at a time, by PLAN 9.2's order (Anruf > Dranbleiben > Treffen). */
sealed interface Live {
    val key: String

    data class Call(val name: String, val since: Long) : Live {
        override val key = "call"
    }

    data class Redial(val name: String, val tries: Int, val nextAt: Long) : Live {
        override val key = "persist"
    }

    data class Meeting(val phase: LiveMeeting.Phase, val contact: Contact) : Live {
        override val key = "meet:" + phase.meeting.id
    }

    /** PLAN 9 D8: Ankommen – at a place with content. */
    data class Here(val contact: Contact, val since: Long) : Live {
        override val key = "here:" + contact.id
    }
}

/** The one live thing right now, or null. */
@Composable
fun rememberLive(data: OrbitData): Live? {
    val calls by CallSession.calls.collectAsState()
    val cards by CallSession.cards.collectAsState()
    val run by Persist.run.collectAsState()
    val here by io.github.thelony97.orbit.live.Arrive.here.collectAsState()
    // A slow clock for the meeting minutes (the island never ticks faster than it shows).
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_MS)
            now = System.currentTimeMillis()
        }
    }
    val call = calls.firstOrNull()
    if (call != null) {
        val number = CallSession.numberOf(call)
        val since = runCatching { call.details.connectTimeMillis }.getOrDefault(0L)
        return Live.Call(cards[number]?.name?.takeIf { it.isNotBlank() } ?: number.ifBlank { "Anruf" }, since)
    }
    run?.let { r -> return Live.Redial(r.plan.name, r.tries, if (r.phase == Persist.Phase.PAUSE) r.nextAt else 0L) }
    val byId = remember(data.contacts) { data.contacts.associateBy { it.id } }
    val phase = LiveMeeting.now(data.meetings, now) { id -> byId[id]?.let { it.deletedAt == null && !it.archived } == true }
    phase?.let { p -> byId[p.meeting.contactId]?.let { return Live.Meeting(p, it) } }
    return here?.let { h -> byId[h.contactId]?.takeIf { data.settings.arrive }?.let { Live.Here(it, h.since) } }
}

private const val TICK_MS = 20_000L
