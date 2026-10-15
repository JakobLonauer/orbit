package io.github.thelony97.orbit.phonebook

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import io.github.thelony97.orbit.core.domain.CalendarBridge
import io.github.thelony97.orbit.core.model.BuiltinTypes
import io.github.thelony97.orbit.data.OrbitData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Kalender-Brücke (PLAN 9 E9): appointments of the last days with a contact (attendee or name in
 * the title) that are over and not yet in his Verlauf – as ghosts „Treffen mit Anna“.
 */
object CalendarGhosts {

    data class Item(val key: String, val contactId: String, val title: String, val begin: Long, val end: Long)

    /** An entry this close to the appointment counts as already logged. */
    private const val NEAR_MS = 3 * 3_600_000L

    suspend fun load(ctx: Context, data: OrbitData): List<Item> = withContext(Dispatchers.IO) {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return@withContext emptyList()
        val now = System.currentTimeMillis()
        val from = now - CalendarBridge.DAYS_BACK * 86_400_000L
        val events = runCatching { events(ctx, from, now) }.getOrDefault(emptyList())
        val dismissed = data.settings.dismissedGhosts.toSet()
        val byId = data.contacts.associateBy { it.id }
        events.mapNotNull { e ->
            val key = "cal:${e.id}:${e.begin}"
            if (key in dismissed) return@mapNotNull null
            val id = CalendarBridge.match(e.title, attendees(ctx, e.id), data.contacts) ?: return@mapNotNull null
            val c = byId[id] ?: return@mapNotNull null
            val logged = c.entries.any { it.at in (e.begin - NEAR_MS)..(e.end + NEAR_MS) && it.typeId != BuiltinTypes.CALL } ||
                data.meetings.any { it.contactId == id && it.start in (e.begin - NEAR_MS)..(e.end + NEAR_MS) }
            if (logged) null else Item(key, id, e.title, e.begin, e.end)
        }.distinctBy { it.contactId to it.begin }
    }

    private data class Event(val id: Long, val title: String, val begin: Long, val end: Long)

    /** Finished, timed (not all-day) appointments in [from, to]; Orbit's own calendar is skipped. */
    private fun events(ctx: Context, from: Long, to: Long): List<Event> {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, from)
            android.content.ContentUris.appendId(it, to)
        }.build()
        val out = ArrayList<Event>()
        ctx.contentResolver.query(
            uri,
            arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.CALENDAR_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val end = c.getLong(3)
                val own = c.getString(5)?.startsWith("Orbit") == true
                if (c.getInt(4) == 0 && end <= to && !own) out += Event(c.getLong(0), c.getString(1).orEmpty(), c.getLong(2), end)
            }
        }
        return out
    }

    private fun attendees(ctx: Context, eventId: Long): List<String> = runCatching {
        ctx.contentResolver.query(
            CalendarContract.Attendees.CONTENT_URI,
            arrayOf(CalendarContract.Attendees.ATTENDEE_EMAIL),
            "${CalendarContract.Attendees.EVENT_ID} = ?", arrayOf(eventId.toString()), null,
        )?.use { c -> buildList { while (c.moveToNext()) c.getString(0)?.let { add(it) } } }.orEmpty()
    }.getOrDefault(emptyList())
}
