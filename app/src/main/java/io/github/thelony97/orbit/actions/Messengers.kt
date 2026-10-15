package io.github.thelony97.orbit.actions

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import io.github.thelony97.orbit.data.EntrySource
import io.github.thelony97.orbit.data.Mutations
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.TypeIds
import io.github.thelony97.orbit.data.newId
import io.github.thelony97.orbit.data.now
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WhatsApp und Signal mit einem Tipp (PLAN 9 A7): chat, voice and video call straight from Orbit,
 * through the rows WhatsApp and Signal put into the phone's address book themselves. Nothing is
 * read from the messengers – Orbit only finds their buttons. Works best with Orbit im Telefonbuch
 * (A1): then every Orbit number is in the address book and the messengers add their rows.
 */
object Messengers {

    enum class Kind(val mime: String, val label: String) {
        WA_CHAT("vnd.android.cursor.item/vnd.com.whatsapp.profile", "WhatsApp"),
        WA_CALL("vnd.android.cursor.item/vnd.com.whatsapp.voip.call", "WhatsApp-Anruf"),
        WA_VIDEO("vnd.android.cursor.item/vnd.com.whatsapp.video.call", "WhatsApp-Video"),
        SIGNAL_CHAT("vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.contact", "Signal"),
        SIGNAL_CALL("vnd.android.cursor.item/vnd.org.thoughtcrime.securesms.call", "Signal-Anruf"),
    }

    data class Action(val kind: Kind, val dataId: Long)

    /** The messenger buttons that exist for [number], at most one per kind. */
    suspend fun forNumber(ctx: Context, number: String?): List<Action> = withContext(Dispatchers.IO) {
        if (number.isNullOrBlank() || ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext emptyList()
        }
        runCatching {
            val r = ctx.contentResolver
            // People in the address book with this number (Android matches the formats).
            val ids = HashSet<Long>()
            r.query(
                Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                arrayOf(ContactsContract.PhoneLookup._ID), null, null, null,
            )?.use { c -> while (c.moveToNext()) ids += c.getLong(0) }
            if (ids.isEmpty()) return@runCatching emptyList<Action>()
            val mimes = Kind.entries.map { it.mime }
            val found = LinkedHashMap<Kind, Action>()
            r.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID, ContactsContract.Data.MIMETYPE),
                "${ContactsContract.Data.CONTACT_ID} IN (${ids.joinToString(",")}) AND ${ContactsContract.Data.MIMETYPE} IN (${mimes.joinToString(",") { "?" }})",
                mimes.toTypedArray(), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val kind = Kind.entries.firstOrNull { it.mime == c.getString(1) } ?: continue
                    if (kind !in found) found[kind] = Action(kind, c.getLong(0))
                }
            }
            Kind.entries.mapNotNull { found[it] }
        }.getOrDefault(emptyList())
    }

    /** Opens the chat or starts the call in the messenger; a call is noted like Orbit's own. */
    fun open(ctx: Context, a: Action, contactId: String?): Boolean {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, a.dataId), a.kind.mime)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val ok = runCatching { ctx.startActivity(intent) }.isSuccess
        if (ok && contactId != null) {
            val type = if (a.kind == Kind.WA_CHAT || a.kind == Kind.SIGNAL_CHAT) TypeIds.MESSAGE else TypeIds.CALL
            val note = a.kind.label
            val cutoff = now() - 30 * 60_000L
            OrbitRepository.edit { d ->
                val c = d.contacts.firstOrNull { it.id == contactId } ?: return@edit d
                if (c.entries.any { it.source == EntrySource.APP_ACTION && it.typeId == type && it.note == note && it.at > cutoff }) d
                else Mutations.addEntry(d, contactId, io.github.thelony97.orbit.data.Entry(id = newId(), contactId = contactId, typeId = type, at = now(), note = note, source = EntrySource.APP_ACTION))
            }
        }
        return ok
    }
}
