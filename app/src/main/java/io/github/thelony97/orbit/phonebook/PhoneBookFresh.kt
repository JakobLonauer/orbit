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

    /** Messengers add their own rows for people who are already there. */
    private val messengers = setOf(
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
        if (!data.settings.phoneBook || !canRead(ctx)) return@withContext emptyList()
        val since = seen(ctx)
        if (since < 0) return@withContext emptyList()
        runCatching {
            val r = ctx.contentResolver
            // Newer raw rows of real address books, one per person.
            val byContact = LinkedHashMap<Long, Item>()
            r.query(
                RawContacts.CONTENT_URI,
                arrayOf(RawContacts._ID, RawContacts.CONTACT_ID, RawContacts.ACCOUNT_TYPE, RawContacts.DISPLAY_NAME_PRIMARY),
                "${RawContacts._ID} > ? AND ${RawContacts.DELETED} = 0",
                arrayOf(since.toString()), "${RawContacts._ID} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getString(2) in messengers) continue
                    val contactId = c.getLong(1)
                    if (contactId <= 0 || byContact.containsKey(contactId)) continue
                    byContact[contactId] = Item(contactId, c.getString(3)?.trim().orEmpty(), c.getLong(0))
                }
            }
            if (byContact.isEmpty()) return@runCatching emptyList<Item>()
            val ids = byContact.keys.joinToString(",")
            // Already carrying an Orbit row: Orbit knows them.
            val ours = HashSet<Long>()
            r.query(
                RawContacts.CONTENT_URI, arrayOf(RawContacts.CONTACT_ID),
                "${RawContacts.ACCOUNT_TYPE} = ? AND ${RawContacts.CONTACT_ID} IN ($ids)",
                arrayOf(PhoneBookSync.ACCOUNT_TYPE), null,
            )?.use { c -> while (c.moveToNext()) ours += c.getLong(0) }
            // Only with a number Orbit doesn't know (fixed rule: same normalized number).
            val iso = Phones.countryIso(ctx)
            val matcher = PhoneMatcher(data.contacts, iso)
            val unknown = HashSet<Long>()
            r.query(
                CommonDataKinds.Phone.CONTENT_URI, arrayOf(CommonDataKinds.Phone.CONTACT_ID, CommonDataKinds.Phone.NUMBER),
                "${CommonDataKinds.Phone.CONTACT_ID} IN ($ids)", null, null,
            )?.use { c -> while (c.moveToNext()) if (matcher.match(c.getString(1)) == null) unknown += c.getLong(0) }
            val dismissed = data.settings.dismissedGhosts.toSet()
            byContact.values.filter { it.contactId !in ours && it.contactId in unknown && it.name.isNotEmpty() && "pb:${it.rawId}" !in dismissed }
        }.getOrDefault(emptyList())
    }
}
