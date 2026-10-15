package io.github.thelony97.orbit.call

import android.content.Context
import android.provider.CallLog
import io.github.thelony97.orbit.core.domain.CallbackStack
import io.github.thelony97.orbit.core.domain.Herold
import io.github.thelony97.orbit.core.domain.NumberInfo
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.domain.PhoneMatcher
import io.github.thelony97.orbit.domain.Phones
import io.github.thelony97.orbit.sync.SyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Rückruf-Stapel (PLAN 9 B3), the phone side: reads call log, SMS and the „Später“ list, and turns
 * the open call-backs into cards with who it is (contact, Herold or the number's own line).
 */
object Callbacks {

    /** One card as the stack shows it. */
    data class Item(
        val card: CallbackStack.Card,
        val contact: Contact?,
        val title: String,
        val info: NumberInfo.Info?,
        val hit: Herold.Hit?,
        /** PLAN 9 B2: costs or a possible ping call – orange, a second swipe to call. */
        val warn: Boolean,
    )

    /** The open call-backs right now (empty without call-log permission). */
    suspend fun load(ctx: Context, data: OrbitData): List<Item> = withContext(Dispatchers.IO) {
        if (!SyncEngine.canReadCalls(ctx)) return@withContext emptyList()
        val now = System.currentTimeMillis()
        val since = now - CallbackStack.DAYS * 86_400_000L
        val iso = Phones.countryIso(ctx)
        val calls = SyncEngine.callsSince(ctx, since).mapNotNull { c ->
            val type = when (c.type) {
                CallLog.Calls.MISSED_TYPE -> CallbackStack.CallType.MISSED
                CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> CallbackStack.CallType.REJECTED
                CallLog.Calls.OUTGOING_TYPE -> CallbackStack.CallType.OUTGOING
                CallLog.Calls.INCOMING_TYPE, CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> CallbackStack.CallType.INCOMING
                else -> null
            }
            val n = c.number?.takeIf { it.isNotBlank() }
            if (type == null || n == null) null else CallbackStack.Call(n, c.date, type)
        }
        val sms = if (SyncEngine.canReadSms(ctx)) {
            SyncEngine.smsSince(ctx, since).mapNotNull { s -> s.address?.let { CallbackStack.Sms(it, s.date, sent = !s.incoming, body = s.body) } }
        } else {
            emptyList()
        }
        val done = data.settings.dismissedGhosts.filter { it.startsWith("cb:") }.toSet()
        val cards = CallbackStack.build(calls, sms, LaterCalls.all(ctx), done, now) { Phones.normalize(it, iso) }
        val matcher = PhoneMatcher(data.contacts.filter { it.deletedAt == null && !it.archived }, iso)
        cards.map { card ->
            val contact = matcher.match(card.number)?.let { id -> data.contacts.firstOrNull { it.id == id } }
            val info = runCatching { NumberInfo.of(card.number, iso) }.getOrNull()
            val hit = if (contact == null) info?.let { CallerLookup.known(ctx, it.e164)?.hit } else null
            val title = contact?.let { io.github.thelony97.orbit.domain.Logic.displayName(it, data.settings.sortByLastName) }
                ?: hit?.name
                ?: Phones.pretty(card.number, iso)
            val ping = NumberInfo.pingSuspect(info, known = contact != null, missed = card.source == CallbackStack.Source.MISSED)
            Item(card, contact, title, info, hit, warn = ping || info?.costs == true)
        }
    }
}

/** „Später zurückrufen“ (PLAN 3.12 / 9 B3): who Jakob wants to call back – a small list on the phone. */
object LaterCalls {
    @Serializable
    private data class Row(val number: String, val at: Long)

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Row.serializer())

    private fun file(ctx: Context) = File(ctx.noBackupFilesDir, "later-calls.json")

    @Synchronized
    private fun read(ctx: Context): List<Row> = runCatching { json.decodeFromString(serializer, file(ctx).readText()) }.getOrDefault(emptyList())

    @Synchronized
    fun add(ctx: Context, number: String) {
        val now = System.currentTimeMillis()
        val rows = read(ctx).filter { it.number != number && now - it.at < CallbackStack.DAYS * 86_400_000L } + Row(number, now)
        runCatching { file(ctx).writeText(json.encodeToString(serializer, rows.takeLast(100))) }
    }

    fun all(ctx: Context): List<CallbackStack.Later> = read(ctx).map { CallbackStack.Later(it.number, it.at) }
}

/**
 * PLAN 9 B3: when a calendar appointment Jakob was busy in has just ended and people wait for a
 * call back, one notification „3 Rückrufe“. Checked every 15 minutes; nothing without calendar
 * permission; each appointment end nudges once.
 */
class CallbackNudgeWorker(ctx: Context, params: androidx.work.WorkerParameters) : androidx.work.CoroutineWorker(ctx, params) {

    companion object {
        private const val WORK = "orbit-callback-nudge"
        private const val WINDOW = 16 * 60_000L

        fun schedule(ctx: Context) {
            val request = androidx.work.PeriodicWorkRequestBuilder<CallbackNudgeWorker>(15, java.util.concurrent.TimeUnit.MINUTES).build()
            androidx.work.WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, androidx.work.ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun lastNudged(ctx: Context) = File(ctx.noBackupFilesDir, "callback-nudge.txt")
    }

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (ctx.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED) return Result.success()
        val now = System.currentTimeMillis()
        val ended = withContext(Dispatchers.IO) { busyEnded(ctx, now - WINDOW, now) } ?: return Result.success()
        // Once per appointment end.
        val before = runCatching { lastNudged(ctx).readText().trim().toLong() }.getOrDefault(0L)
        if (ended <= before) return Result.success()
        io.github.thelony97.orbit.data.OrbitRepository.init(ctx)
        val data = io.github.thelony97.orbit.data.OrbitRepository.ensureLoaded()
        val items = Callbacks.load(ctx, data)
        runCatching { lastNudged(ctx).writeText(ended.toString()) }
        if (items.isNotEmpty()) io.github.thelony97.orbit.notify.Notifications.callbacks(ctx, items.size, items.take(2).map { it.title })
        return Result.success()
    }

    /** End time of the latest busy, timed appointment that ended in [from]..[to]; null if none. */
    private fun busyEnded(ctx: Context, from: Long, to: Long): Long? = runCatching {
        val uri = android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, from - 12 * 3_600_000L)
            android.content.ContentUris.appendId(it, to)
        }.build()
        var latest: Long? = null
        ctx.contentResolver.query(
            uri,
            arrayOf(android.provider.CalendarContract.Instances.END, android.provider.CalendarContract.Instances.ALL_DAY, android.provider.CalendarContract.Instances.AVAILABILITY),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val end = c.getLong(0)
                val allDay = c.getInt(1) == 1
                val busy = c.getInt(2) == android.provider.CalendarContract.Events.AVAILABILITY_BUSY
                if (!allDay && busy && end in from..to && (latest == null || end > latest!!)) latest = end
            }
        }
        latest
    }.getOrNull()
}
