package io.github.thelony97.orbit.phonebook

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.RawContacts
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.domain.PhoneMatcher
import io.github.thelony97.orbit.domain.Phones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * „Neu im Telefonbuch“ (PLAN 9 A1, Rückweg): people another app added to the address book after
 * Orbit went in – with a number Orbit doesn't know yet. Fixed rule over raw-contact ids (they only
 * grow): everything up to the mark is known; messengers' own rows never count as new people.
 */
object PhoneBookFresh {

    /** Messengers add their own rows for people who are already there (also used by the import). */
    val messengers = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.thoughtcrime.securesms", "org.telegram.messenger",
        "ch.threema.app", "com.viber.voip", "com.skype.contacts.sync", "com.google.android.apps.tachyon",
        PhoneBookSync.ACCOUNT_TYPE,
    )

    data class Item(val contactId: Long, val name: String, val rawId: Long)

    private fun file(ctx: Context) = File(ctx.noBackupFilesDir, "phonebook-seen.txt")

    private fun seen(ctx: Context): Long = runCatching { file(ctx).readText().trim().toLong() }.getOrDefault(-1L)

    /** Everything up to [rawId] counts as known from now on. */
    fun markSeen(ctx: Context, rawId: Long) {
        if (rawId > seen(ctx)) runCatching { file(ctx).writeText(rawId.toString()) }
    }

    private fun canRead(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Orbit just went into the phone book: what is there now is known. */
    suspend fun start(ctx: Context) = withContext(Dispatchers.IO) {
        if (!canRead(ctx) || seen(ctx) >= 0) return@withContext
        val max = runCatching {
            ctx.contentResolver.query(
                RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY, "1").build(),
                arrayOf(RawContacts._ID), null, null, "${RawContacts._ID} DESC",
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else 0L } ?: 0L
        }.getOrDefault(0L)
        runCatching { file(ctx).writeText(max.toString()) }
    }

    suspend fun list(ctx: Context, data: OrbitData): List<Item> = withContext(Dispatchers.IO) {
        val since = seen(ctx)
        if (!data.settings.phoneBook || !canRead(ctx) || since < 0) return@withContext emptyList()
        runCatching {
            val r = ctx.contentResolver
            val newer = newerPeople(r, since)
            if (newer.isEmpty()) return@runCatching emptyList<Item>()
            val ids = newer.keys.joinToString(",")
            val ours = withOrbitRow(r, ids)
            val unknown = withUnknownNumber(r, ids, PhoneMatcher(data.contacts, Phones.countryIso(ctx)))
            val dismissed = data.settings.dismissedGhosts.toSet()
            newer.values.filter { it.contactId !in ours && it.contactId in unknown && "pb:${it.rawId}" !in dismissed }
        }.getOrDefault(emptyList())
    }

    /** Raw rows of real address books newer than the mark, one per person (messengers left out). */
    private fun newerPeople(r: android.content.ContentResolver, since: Long): Map<Long, Item> {
        val byContact = LinkedHashMap<Long, Item>()
        r.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.CONTACT_ID, RawContacts.ACCOUNT_TYPE, RawContacts.DISPLAY_NAME_PRIMARY),
            "${RawContacts._ID} > ? AND ${RawContacts.DELETED} = 0",
            arrayOf(since.toString()), "${RawContacts._ID} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val contactId = c.getLong(1)
                val name = c.getString(3)?.trim().orEmpty()
                val counts = c.getString(2) !in messengers && contactId > 0 && name.isNotEmpty()
                if (counts) byContact.putIfAbsent(contactId, Item(contactId, name, c.getLong(0)))
            }
        }
        return byContact
    }

    /** People that already carry an Orbit row: Orbit knows them. */
    private fun withOrbitRow(r: android.content.ContentResolver, ids: String): Set<Long> {
        val out = HashSet<Long>()
        r.query(
            RawContacts.CONTENT_URI, arrayOf(RawContacts.CONTACT_ID),
            "${RawContacts.ACCOUNT_TYPE} = ? AND ${RawContacts.CONTACT_ID} IN ($ids)",
            arrayOf(PhoneBookSync.ACCOUNT_TYPE), null,
        )?.use { c -> while (c.moveToNext()) out += c.getLong(0) }
        return out
    }

    /** People with at least one number Orbit doesn't know (fixed rule: same normalized number). */
    private fun withUnknownNumber(r: android.content.ContentResolver, ids: String, matcher: PhoneMatcher): Set<Long> {
        val out = HashSet<Long>()
        r.query(
            CommonDataKinds.Phone.CONTENT_URI, arrayOf(CommonDataKinds.Phone.CONTACT_ID, CommonDataKinds.Phone.NUMBER),
            "${CommonDataKinds.Phone.CONTACT_ID} IN ($ids)", null, null,
        )?.use { c -> while (c.moveToNext()) if (matcher.match(c.getString(1)) == null) out += c.getLong(0) }
        return out
    }
}
