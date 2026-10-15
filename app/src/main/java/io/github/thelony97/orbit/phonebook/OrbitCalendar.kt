package io.github.thelony97.orbit.phonebook

import android.Manifest
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.util.Log
import io.github.thelony97.orbit.core.domain.CalendarSync
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.domain.Logic
import io.github.thelony97.orbit.ui.components.AppWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The account „Orbit“ is shared by the phone book (A1) and the calendar (A13); it goes only when
 * neither uses it any more.
 */
object OrbitAccount {
    fun ensure(ctx: Context) {
        val am = AccountManager.get(ctx)
        if (am.getAccountsByType(PhoneBookSync.ACCOUNT_TYPE).none { it.name == PhoneBookSync.ACCOUNT_NAME }) {
            am.addAccountExplicitly(PhoneBookSync.account, null, null)
        }
    }

    fun release(ctx: Context) {
        runCatching { AccountManager.get(ctx).removeAccountExplicitly(PhoneBookSync.account) }
    }
}

/**
 * Orbit-Kalender (PLAN 9 A13): Orbit's own calendar in the phone's calendar app with every
 * occasion – written like the phone book, only ever Orbit → calendar.
 */
object OrbitCalendar {

    private const val TAG = "OrbitCalendar"
    private val mutex = Mutex()
    private var pending: Job? = null

    fun canWrite(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private fun asSync(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, PhoneBookSync.ACCOUNT_NAME)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, PhoneBookSync.ACCOUNT_TYPE)
        .build()

    fun request(ctx: Context) {
        val app = ctx.applicationContext
        synchronized(this) {
            pending?.cancel()
            pending = AppWork.launch {
                delay(2_000)
                writeNow(app)
            }
        }
    }

    suspend fun writeNow(ctx: Context): Result<Int> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val data = OrbitRepository.snapshot()
                // Never from a state that could not be read (it would empty the calendar).
                if (OrbitRepository.loadError.value != null) return@runCatching 0
                if (!data.settings.orbitCalendar || !canWrite(ctx)) return@runCatching 0
                OrbitAccount.ensure(ctx)
                val r = ctx.contentResolver
                val calendar = calendarId(r) ?: return@runCatching 0
                val byId = data.contacts.associateBy { it.id }
                val items = CalendarSync.items(data, LocalDate.now()) { id -> byId[id]?.let { Logic.shortName(it) }.orEmpty() }
                val plan = CalendarSync.plan(items, existing(r, calendar))
                if (!plan.isEmpty) apply(r, calendar, plan)
                plan.insert.size + plan.update.size + plan.delete.size
            }.onFailure { e ->
                Log.w(TAG, "calendar sync failed", e)
                (ctx.applicationContext as? io.github.thelony97.orbit.OrbitApp)?.errorLog?.log("Orbit-Kalender", e)
            }
        }
    }

    /** Switched off: the calendar and its events go; the account only if the phone book is off too. */
    suspend fun remove(ctx: Context, keepAccount: Boolean) {
        // A write waiting or running must not bring the calendar back.
        synchronized(this) { pending?.cancel() }
        withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    ctx.contentResolver.delete(
                        asSync(Calendars.CONTENT_URI),
                        "${Calendars.ACCOUNT_NAME} = ? AND ${Calendars.ACCOUNT_TYPE} = ?",
                        arrayOf(PhoneBookSync.ACCOUNT_NAME, PhoneBookSync.ACCOUNT_TYPE),
                    )
                }.onFailure { Log.w(TAG, "remove calendar", it) }
                if (!keepAccount) OrbitAccount.release(ctx)
            }
        }
    }

    private fun calendarId(r: ContentResolver): Long? {
        r.query(
            Calendars.CONTENT_URI, arrayOf(Calendars._ID),
            "${Calendars.ACCOUNT_NAME} = ? AND ${Calendars.ACCOUNT_TYPE} = ?",
            arrayOf(PhoneBookSync.ACCOUNT_NAME, PhoneBookSync.ACCOUNT_TYPE), null,
        )?.use { c -> if (c.moveToFirst()) return c.getLong(0) }
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, PhoneBookSync.ACCOUNT_NAME)
            put(Calendars.ACCOUNT_TYPE, PhoneBookSync.ACCOUNT_TYPE)
            put(Calendars.NAME, "orbit")
            put(Calendars.CALENDAR_DISPLAY_NAME, "Orbit")
            put(Calendars.CALENDAR_COLOR, 0xFFA78BFF.toInt())
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_READ)
            put(Calendars.OWNER_ACCOUNT, PhoneBookSync.ACCOUNT_NAME)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, "UTC")
        }
        return r.insert(asSync(Calendars.CONTENT_URI), values)?.let { ContentUris.parseId(it) }
    }

    private fun existing(r: ContentResolver, calendar: Long): List<CalendarSync.Existing> {
        val out = ArrayList<CalendarSync.Existing>()
        r.query(
            asSync(Events.CONTENT_URI), arrayOf(Events._ID, Events.SYNC_DATA1, Events.SYNC_DATA2, Events.DELETED),
            "${Events.CALENDAR_ID} = ?", arrayOf(calendar.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                out += if (c.getInt(3) == 1) CalendarSync.Existing(c.getLong(0), null, null)
                else CalendarSync.Existing(c.getLong(0), c.getString(1), c.getString(2))
            }
        }
        return out
    }

    private fun values(calendar: Long, item: CalendarSync.Item) = ContentValues().apply {
        val start = item.day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        put(Events.CALENDAR_ID, calendar)
        put(Events.TITLE, item.title)
        put(Events.DESCRIPTION, item.description)
        put(Events.ALL_DAY, 1)
        put(Events.EVENT_TIMEZONE, "UTC")
        put(Events.DTSTART, start)
        if (item.rrule != null) {
            put(Events.RRULE, item.rrule)
            put(Events.DURATION, "P1D")
            putNull(Events.DTEND)
        } else {
            put(Events.DTEND, start + 86_400_000L)
            putNull(Events.RRULE)
            putNull(Events.DURATION)
        }
        put(Events.AVAILABILITY, Events.AVAILABILITY_FREE)
        put(Events.SYNC_DATA1, item.key)
        put(Events.SYNC_DATA2, item.hash)
    }

    private fun apply(r: ContentResolver, calendar: Long, plan: CalendarSync.Plan) {
        val ops = ArrayList<ContentProviderOperation>()
        fun flush() {
            if (ops.isEmpty()) return
            r.applyBatch(CalendarContract.AUTHORITY, ops)
            ops.clear()
        }
        plan.delete.forEach { id ->
            ops += ContentProviderOperation.newDelete(asSync(ContentUris.withAppendedId(Events.CONTENT_URI, id))).build()
            if (ops.size >= 200) flush()
        }
        plan.insert.forEach { item ->
            ops += ContentProviderOperation.newInsert(asSync(Events.CONTENT_URI)).withValues(values(calendar, item)).build()
            if (ops.size >= 200) flush()
        }
        plan.update.forEach { (id, item) ->
            ops += ContentProviderOperation.newUpdate(asSync(ContentUris.withAppendedId(Events.CONTENT_URI, id))).withValues(values(calendar, item)).build()
            if (ops.size >= 200) flush()
        }
        flush()
    }
}
