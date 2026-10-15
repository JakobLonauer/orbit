package io.github.thelony97.orbit.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.Telephony
import android.util.Log
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.Entry
import io.github.thelony97.orbit.data.EntrySource
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.SmsMessage
import io.github.thelony97.orbit.data.TypeIds
import io.github.thelony97.orbit.data.UnknownNumber
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.data.now
import io.github.thelony97.orbit.domain.PhoneMatcher
import io.github.thelony97.orbit.domain.Phones
import io.github.thelony97.orbit.domain.toLocalDate
import java.util.concurrent.TimeUnit

/**
 * Reads the system call log and SMS store and turns them into history entries.
 * Everything is idempotent: entries carry an externalId, so running twice never duplicates.
 */
object SyncEngine {

    private const val TAG = "SyncEngine"
    private val BACKFILL = TimeUnit.DAYS.toMillis(365)
    private const val MAX_UNKNOWN = 150

    data class CallRec(
        val id: Long,
        val number: String?,
        val date: Long,
        val duration: Long,
        val type: Int,
        val cachedName: String?,
        /** PLAN 9 B9: the SIM (phone account id) the call went through. */
        val accountId: String? = null,
    )

    data class SmsRec(
        val id: Long,
        val address: String?,
        val date: Long,
        val body: String,
        val incoming: Boolean,
    )

    /** [extension] = the number when it only fell into an organisation's number range (PLAN 3.6). */
    data class NewCall(val contactId: String, val entry: Entry, val extension: String? = null)

    fun canReadCalls(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    fun canReadSms(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    private fun connected(type: Int) =
        type == CallLog.Calls.INCOMING_TYPE || type == CallLog.Calls.OUTGOING_TYPE ||
            type == CallLog.Calls.ANSWERED_EXTERNALLY_TYPE

    /** Regular sync since the last watermark. Returns the newly recorded calls. */
    suspend fun syncAll(ctx: Context): List<NewCall> {
        val snapshot = OrbitRepository.snapshot()
        val iso = Phones.countryIso(ctx)
        val start = now() - BACKFILL
        val callSince = snapshot.settings.callSyncedUntil.takeIf { it > 0 } ?: start
        val smsSince = snapshot.settings.smsSyncedUntil.takeIf { it > 0 } ?: start
        val calls = if (canReadCalls(ctx)) readCalls(ctx, callSince) else emptyList()
        val sms = if (canReadSms(ctx)) readSms(ctx, smsSince) else emptyList()
        if (calls.isEmpty() && sms.isEmpty()) {
            if (snapshot.settings.callSyncedUntil == 0L && canReadCalls(ctx) ||
                snapshot.settings.smsSyncedUntil == 0L && canReadSms(ctx)
            ) {
                OrbitRepository.update { d ->
                    d.copy(
                        settings = d.settings.copy(
                            callSyncedUntil = if (canReadCalls(ctx)) maxOf(d.settings.callSyncedUntil, now()) else d.settings.callSyncedUntil,
                            smsSyncedUntil = if (canReadSms(ctx)) maxOf(d.settings.smsSyncedUntil, now()) else d.settings.smsSyncedUntil,
                        ),
                    )
                }
            }
            return emptyList()
        }
        val newCalls = ArrayList<NewCall>()
        OrbitRepository.update { d ->
            newCalls.clear()
            val matcher = PhoneMatcher(d.contacts, iso)
            var result = apply(d, calls, sms, matcher, onlyContacts = null, countUnknown = true, newCalls = newCalls)
            val callMax = calls.maxOfOrNull { it.date }
            val smsMax = sms.maxOfOrNull { it.date }
            result = result.copy(
                settings = result.settings.copy(
                    callSyncedUntil = maxOf(result.settings.callSyncedUntil, callMax ?: 0L),
                    smsSyncedUntil = maxOf(result.settings.smsSyncedUntil, smsMax ?: 0L),
                ),
            )
            result
        }
        return newCalls.toList()
    }

    /** Imports up to a year of history for one contact (after creating it or changing its numbers). */
    suspend fun backfill(ctx: Context, contactId: String) = backfill(ctx, setOf(contactId))

    /** One pass over call log and SMS for several contacts at once (Mehrfach-Import, PLAN 3.14). */
    suspend fun backfill(ctx: Context, contactIds: Set<String>) {
        if (contactIds.isEmpty()) return
        val since = now() - BACKFILL
        val calls = if (canReadCalls(ctx)) readCalls(ctx, since) else emptyList()
        val sms = if (canReadSms(ctx)) readSms(ctx, since) else emptyList()
        if (calls.isEmpty() && sms.isEmpty()) return
        val iso = Phones.countryIso(ctx)
        OrbitRepository.update { d ->
            val matcher = PhoneMatcher(d.contacts, iso)
            val result = apply(d, calls, sms, matcher, onlyContacts = contactIds, countUnknown = false, newCalls = ArrayList())
            // Numbers that now belong to these contacts are no longer "unknown".
            val keys = d.contacts.filter { it.id in contactIds }.flatMap { it.phones }
                .mapNotNull { Phones.normalize(it.value, iso) }.toSet()
            result.copy(unknownNumbers = result.unknownNumbers.filterNot { it.key in keys })
        }
    }

    private fun apply(
        d: OrbitData,
        calls: List<CallRec>,
        sms: List<SmsRec>,
        matcher: PhoneMatcher,
        onlyContacts: Set<String>?,
        countUnknown: Boolean,
        newCalls: MutableList<NewCall>,
    ): OrbitData {
        val minSeconds = d.settings.minCallSeconds.toLong()
        val added = HashMap<String, MutableList<Entry>>()
        val replaced = HashMap<String, MutableMap<String, Entry>>()
        val unknown = d.unknownNumbers.associateBy { it.key }.toMutableMap()
        val contactsById = d.contacts.associateBy { it.id }
        val knownIds = HashMap<String, MutableSet<String>>()

        fun idsOf(contactId: String): MutableSet<String> = knownIds.getOrPut(contactId) {
            contactsById[contactId]?.entries?.mapNotNull { it.externalId }?.toMutableSet() ?: mutableSetOf()
        }

        var unknownChanged = false

        fun noteUnknown(raw: String?, at: Long, name: String?, isCall: Boolean) {
            if (!countUnknown || !d.settings.suggestUnknown || raw.isNullOrBlank()) return
            val key = matcher.key(raw) ?: return
            if (key.count { it.isDigit() } < 6) return
            unknownChanged = true
            val old = unknown[key] ?: UnknownNumber(key = key, display = raw)
            unknown[key] = old.copy(
                display = raw,
                cachedName = name?.takeIf { it.isNotBlank() } ?: old.cachedName,
                calls = old.calls + if (isCall) 1 else 0,
                sms = old.sms + if (isCall) 0 else 1,
                lastAt = maxOf(old.lastAt, at),
            )
        }

        for (call in calls) {
            // A member's Durchwahl lands at the organisation with the member („Anruf · Empfang“).
            val hit = matcher.matchCall(call.number)
            val contactId = hit?.contactId
            if (contactId == null) {
                if (call.type != CallLog.Calls.BLOCKED_TYPE) noteUnknown(call.number, call.date, call.cachedName, true)
                continue
            }
            if (onlyContacts != null && contactId !in onlyContacts) continue
            if (!connected(call.type) || call.duration < minSeconds) continue
            val ext = "call:${call.date}"
            val ids = idsOf(contactId)
            if (ext in ids) continue
            ids += ext
            val entry = Entry(
                id = newId(),
                contactId = contactId,
                typeId = TypeIds.CALL,
                at = call.date,
                source = EntrySource.CALL_LOG,
                durationSec = call.duration,
                incoming = call.type != CallLog.Calls.OUTGOING_TYPE,
                externalId = ext,
                memberId = hit?.memberId,
            )
            added.getOrPut(contactId) { mutableListOf() } += entry
            newCalls += NewCall(contactId, entry, extension = call.number.takeIf { hit?.via == io.github.thelony97.orbit.core.domain.Matcher.Via.PREFIX })
        }

        // SMS are grouped into one history entry per contact and day.
        for (msg in sms) {
            val contactId = matcher.match(msg.address)
            if (contactId == null) {
                noteUnknown(msg.address, msg.date, null, false)
                continue
            }
            if (onlyContacts != null && contactId !in onlyContacts) continue
            val ext = "sms:${msg.date.toLocalDate()}"
            val message = SmsMessage(msg.id, msg.date, msg.incoming, msg.body)
            val pending = replaced.getOrPut(contactId) { mutableMapOf() }
            val existing = pending[ext] ?: contactsById[contactId]?.entries?.firstOrNull { it.externalId == ext }
            if (existing == null) {
                pending[ext] = Entry(
                    id = newId(),
                    contactId = contactId,
                    typeId = TypeIds.MESSAGE,
                    at = msg.date,
                    source = EntrySource.SMS,
                    externalId = ext,
                    messages = listOf(message),
                )
            } else if (existing.messages.none { it.at == msg.date && it.text == msg.body }) {
                val merged = (existing.messages + message).sortedBy { it.at }
                pending[ext] = existing.copy(messages = merged, at = merged.last().at)
            }
        }

        if (added.isEmpty() && replaced.values.all { it.isEmpty() } && !unknownChanged) return d

        val contacts = d.contacts.map { c ->
            val adds = added[c.id].orEmpty()
            val reps = replaced[c.id].orEmpty()
            if (adds.isEmpty() && reps.isEmpty()) return@map c
            val byExt = reps.values.associateBy { it.externalId }
            val updated = c.entries.map { e -> e.externalId?.let { byExt[it] } ?: e }
            val newOnes = reps.values.filter { r -> c.entries.none { it.externalId == r.externalId } }
            c.copy(entries = (updated + adds + newOnes).sortedByDescending { it.at })
        }
        val unknownList = unknown.values.sortedByDescending { it.lastAt }.take(MAX_UNKNOWN)
        return d.copy(contacts = contacts, unknownNumbers = unknownList)
    }

    /** PLAN 9 B3: the call log since [since] (empty without permission). */
    fun callsSince(ctx: Context, since: Long): List<CallRec> = if (canReadCalls(ctx)) readCalls(ctx, since) else emptyList()

    /** PLAN 9 B3: SMS since [since], inbox and sent (empty without permission). */
    fun smsSince(ctx: Context, since: Long): List<SmsRec> = if (canReadSms(ctx)) readSms(ctx, since) else emptyList()

    /** The newest call-log row since [since] (used after a call from an unknown number). */
    fun lastCall(ctx: Context, since: Long): CallRec? =
        if (canReadCalls(ctx)) readCalls(ctx, since).maxByOrNull { it.date } else null

    /** PLAN 3.12: only calls *from* an unknown number get the "Als Kontakt anlegen" offer. */
    fun isIncoming(call: CallRec) = call.type == CallLog.Calls.INCOMING_TYPE

    private fun readCalls(ctx: Context, since: Long): List<CallRec> {
        val out = ArrayList<CallRec>()
        val projection = arrayOf(
            CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.DATE,
            CallLog.Calls.DURATION, CallLog.Calls.TYPE, CallLog.Calls.CACHED_NAME, CallLog.Calls.PHONE_ACCOUNT_ID,
        )
        try {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI, projection,
                "${CallLog.Calls.DATE} > ?", arrayOf(since.toString()),
                "${CallLog.Calls.DATE} ASC",
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(CallLog.Calls._ID)
                val iNum = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val iDate = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val iDur = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                val iType = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val iName = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val iAccount = c.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
                while (c.moveToNext()) {
                    out += CallRec(
                        id = c.getLong(iId),
                        number = c.getString(iNum),
                        date = c.getLong(iDate),
                        duration = c.getLong(iDur),
                        type = c.getInt(iType),
                        cachedName = c.getString(iName),
                        accountId = if (iAccount >= 0) c.getString(iAccount) else null,
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "call log not readable", e)
        }
        return out
    }

    private fun readSms(ctx: Context, since: Long): List<SmsRec> {
        val out = ArrayList<SmsRec>()
        val projection = arrayOf(
            Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.DATE,
            Telephony.Sms.BODY, Telephony.Sms.TYPE,
        )
        try {
            ctx.contentResolver.query(
                Telephony.Sms.CONTENT_URI, projection,
                "${Telephony.Sms.DATE} > ?", arrayOf(since.toString()),
                "${Telephony.Sms.DATE} ASC",
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(Telephony.Sms._ID)
                val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val iType = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                while (c.moveToNext()) {
                    val type = c.getInt(iType)
                    if (type != Telephony.Sms.MESSAGE_TYPE_INBOX && type != Telephony.Sms.MESSAGE_TYPE_SENT) continue
                    out += SmsRec(
                        id = c.getLong(iId),
                        address = c.getString(iAddr),
                        date = c.getLong(iDate),
                        body = c.getString(iBody).orEmpty(),
                        incoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX,
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "sms not readable", e)
        }
        return out
    }

    /** Helper for screens: contact for a raw number. */
    fun contactFor(d: OrbitData, ctx: Context, number: String?): Contact? {
        val id = PhoneMatcher(d.contacts, Phones.countryIso(ctx)).match(number) ?: return null
        return d.contacts.firstOrNull { it.id == id }
    }
}
