package io.github.thelony97.orbit.call

import android.content.Context
import android.provider.CalendarContract
import android.telecom.Call
import android.telecom.DisconnectCause
import io.github.thelony97.orbit.actions.ContactActions
import io.github.thelony97.orbit.core.domain.Automations
import io.github.thelony97.orbit.core.domain.CallerCard
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.domain.zone
import io.github.thelony97.orbit.live.AutoCount

/**
 * PLAN 9 E17 „Verpasster Anruf während Termin → SMS mit Rückrufzeit“: a missed call from someone
 * in Orbit (in the chosen groups) while a busy appointment runs gets one SMS with its end time.
 */
object BusyReply {

    /** One reply per caller and appointment. */
    private val answered = HashSet<String>()

    fun onRemoved(ctx: Context, call: Call, card: CallerCard?) {
        if (card == null) return
        val cause = runCatching { call.details.disconnectCause.code }.getOrDefault(DisconnectCause.UNKNOWN)
        if (cause != DisconnectCause.MISSED) return
        io.github.thelony97.orbit.ui.components.AppWork.launch { reply(ctx, card) }
    }

    private suspend fun reply(ctx: Context, card: CallerCard) {
        val doc = runCatching { OrbitRepository.ensureLoaded() }.getOrNull() ?: return
        val s = doc.settings
        if (!s.autoBusySms) return
        val c = doc.contacts.firstOrNull { it.id == card.contactId && it.deletedAt == null && !it.archived && !it.isOrg } ?: return
        if (!Automations.busyApplies(c.groupIds, s.autoBusyGroups)) return
        val end = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { busyNow(ctx) } ?: return
        val key = c.id + ":" + end
        if (!synchronized(answered) { answered.add(key) }) return
        val text = Automations.busyText(end, zone(), formal = c.greeting?.formal == true)
        if (ContactActions.smsDirect(ctx, c, text)) AutoCount.bump(ctx, AutoCount.BUSY_SMS)
    }

    /** End of the busy, timed appointment running now; null when none. */
    private fun busyNow(ctx: Context): Long? = runCatching {
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, now - 1)
            android.content.ContentUris.appendId(it, now + 1)
        }.build()
        var end: Long? = null
        ctx.contentResolver.query(
            uri,
            arrayOf(CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.AVAILABILITY),
            null, null, null,
        )?.use { q ->
            while (q.moveToNext()) {
                val busy = q.getInt(3) == CalendarContract.Events.AVAILABILITY_BUSY && q.getInt(2) != 1
                if (busy && q.getLong(0) <= now && q.getLong(1) > now) end = maxOf(end ?: 0L, q.getLong(1))
            }
        }
        end
    }.getOrNull()
}
