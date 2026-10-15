package io.github.thelony97.orbit.phonebook

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import android.util.Log
import io.github.thelony97.orbit.core.domain.PhoneBook
import io.github.thelony97.orbit.data.OrbitData
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.ui.components.AppWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** A new data row of one MIME type for the card being written. */
private typealias RowOf = (String) -> ContentProviderOperation.Builder

/**
 * Orbit im Telefonbuch des Handys (PLAN 9 A1): Orbit's own account „Orbit“ in the Android address
 * book. After every save (and at start) the difference is written – only ever Orbit → phone book,
 * never back, and only rows of Orbit's own account: other accounts are never touched. Turning it
 * off removes the account and with it every row Orbit wrote.
 */
object PhoneBookSync {

    private const val TAG = "PhoneBook"
    const val ACCOUNT_TYPE = "io.github.thelony97.orbit.account"
    const val ACCOUNT_NAME = "Orbit"

    /** Orbit rows in a system contact card (PLAN 9 A3): „In Orbit öffnen“ and own fields. */
    const val MIME_PROFILE = "vnd.android.cursor.item/vnd.io.github.thelony97.orbit.profile"
    const val MIME_FIELD = "vnd.android.cursor.item/vnd.io.github.thelony97.orbit.field"

    val account: Account get() = Account(ACCOUNT_NAME, ACCOUNT_TYPE)

    private val mutex = Mutex()
    private var pending: Job? = null
    /** The visible first fill after switching on; cancelled when switched off again. */
    private var filling: Job? = null

    /** Key of the one-time offer in the Heute row (PLAN 9 A1 „einmalig als Geist“). */
    const val OFFER_KEY = "phonebook-offer"

    /** How many Orbit contacts are in the phone book right now (status line), null = unknown. */
    private val _count = MutableStateFlow<Int?>(null)
    val count: StateFlow<Int?> = _count.asStateFlow()

    /** The first fill after switching on, counted live (PLAN 9 A1 Moment); null when nothing runs. */
    data class Live(val count: Int, val total: Int, val done: Boolean)

    private val _live = MutableStateFlow<Live?>(null)
    val live: StateFlow<Live?> = _live.asStateFlow()

    /**
     * Switch on: the setting, the mark for „Neu im Telefonbuch“, then the first fill – visible as
     * it counts up. The live state is there at once, so the offer turns into it without a gap.
     */
    fun turnOn(ctx: Context) {
        val app = ctx.applicationContext
        _live.value = Live(0, 0, done = false)
        synchronized(this) { filling?.cancel() }
        val job = AppWork.launch {
            // On, and the offer has done its job: it never comes back, even after switching off.
            OrbitRepository.update { d ->
                d.copy(settings = d.settings.copy(phoneBook = true, dismissedGhosts = (d.settings.dismissedGhosts + OFFER_KEY).distinct()))
            }
            PhoneBookFresh.start(app)
            val result = writeNow(app, counted = true)
            val now = _live.value
            // Nothing written (switch off again, no permission, unreadable data): no card at all.
            if (result.isFailure || now == null || now.total == 0) {
                _live.value = null
                return@launch
            }
            _live.value = now.copy(done = true)
            // Long enough to read the result, then the card goes (the count stays in the settings).
            delay(2_400)
            _live.value = null
        }
        synchronized(this) { filling = job }
    }

    fun canWrite(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
            ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun sync(uri: Uri): Uri =
        uri.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build()

    /** After a save: once, a moment later – several saves in a row write once. */
    fun request(ctx: Context) {
        val app = ctx.applicationContext
        synchronized(this) {
            pending?.cancel()
            pending = AppWork.launch {
                delay(1_500)
                writeNow(app)
            }
        }
    }

    /**
     * Writes the difference. Never while Orbit's own data could not be read (an empty state must
     * not wipe the phone book), never without the switch or the permission.
     */
    suspend fun writeNow(ctx: Context, counted: Boolean = false): Result<Int> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val data = OrbitRepository.snapshot()
                // Never from a state that could not be read (it would empty the phone book).
                if (OrbitRepository.loadError.value != null) return@runCatching 0
                if (!data.settings.phoneBook || !canWrite(ctx)) return@runCatching 0
                // The mark for „Neu im Telefonbuch“ before Orbit's own first rows (also after a restore).
                PhoneBookFresh.start(ctx)
                ensureAccount(ctx)
                val cards = data.contacts
                    .filter { PhoneBook.exported(it, data.settings.phoneBookOrgs) }
                    .map { PhoneBook.card(it, data.groups) }
                val plan = PhoneBook.plan(cards, existing(ctx.contentResolver))
                val base = cards.size - plan.insert.size
                val progress: (Int) -> Unit = { inserted ->
                    _count.value = base + inserted
                    if (counted) _live.value = Live(base + inserted, cards.size, done = false)
                }
                progress(0)
                if (!plan.isEmpty) apply(ctx.contentResolver, plan, progress)
                _count.value = cards.size
                if (counted) _live.value = Live(cards.size, cards.size, done = false)
                plan.insert.size + plan.rewrite.size + plan.delete.size
            }.onFailure { e ->
                Log.w(TAG, "phone book sync failed", e)
                (ctx.applicationContext as? io.github.thelony97.orbit.OrbitApp)?.errorLog?.log("Telefonbuch", e)
            }
        }
    }

    /**
     * Switched off: every row Orbit wrote goes; the account too unless the Orbit calendar still
     * uses it ([keepAccount]). Other accounts' rows are never touched.
     */
    suspend fun remove(ctx: Context, keepAccount: Boolean) {
        // A write waiting or running must not bring the rows back; the live card goes at once.
        synchronized(this) {
            pending?.cancel()
            filling?.cancel()
        }
        _live.value = null
        withContext(Dispatchers.IO) { mutex.withLock {
            runCatching {
                ctx.contentResolver.delete(
                    sync(RawContacts.CONTENT_URI),
                    "${RawContacts.ACCOUNT_TYPE} = ? AND ${RawContacts.ACCOUNT_NAME} = ?",
                    arrayOf(ACCOUNT_TYPE, ACCOUNT_NAME),
                )
                _count.value = 0
            }.onFailure { Log.w(TAG, "remove rows", it) }
            if (!keepAccount) OrbitAccount.release(ctx)
        } }
    }

    private fun ensureAccount(ctx: Context) {
        OrbitAccount.ensure(ctx)
        runCatching {
            ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1)
            ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, false)
        }
        // Visible in every contacts app without a group.
        val values = ContentValues().apply {
            put(ContactsContract.Settings.ACCOUNT_NAME, ACCOUNT_NAME)
            put(ContactsContract.Settings.ACCOUNT_TYPE, ACCOUNT_TYPE)
            put(ContactsContract.Settings.UNGROUPED_VISIBLE, 1)
            put(ContactsContract.Settings.SHOULD_SYNC, 1)
        }
        val r = ctx.contentResolver
        runCatching {
            val updated = r.update(
                sync(ContactsContract.Settings.CONTENT_URI), values,
                "${ContactsContract.Settings.ACCOUNT_NAME} = ? AND ${ContactsContract.Settings.ACCOUNT_TYPE} = ?",
                arrayOf(ACCOUNT_NAME, ACCOUNT_TYPE),
            )
            if (updated == 0) r.insert(sync(ContactsContract.Settings.CONTENT_URI), values)
        }.onFailure { Log.w(TAG, "settings row", it) }
    }

    private fun existing(r: ContentResolver): List<PhoneBook.Existing> {
        val out = ArrayList<PhoneBook.Existing>()
        r.query(
            sync(RawContacts.CONTENT_URI),
            arrayOf(RawContacts._ID, RawContacts.SOURCE_ID, RawContacts.SYNC1, RawContacts.DELETED),
            "${RawContacts.ACCOUNT_TYPE} = ? AND ${RawContacts.ACCOUNT_NAME} = ?",
            arrayOf(ACCOUNT_TYPE, ACCOUNT_NAME), null,
        )?.use { c ->
            while (c.moveToNext()) {
                // A row a contacts app deleted goes for good – Orbit writes it again (Orbit is the truth).
                out += if (c.getInt(3) == 1) PhoneBook.Existing(c.getLong(0), null, null)
                else PhoneBook.Existing(c.getLong(0), c.getString(1), c.getString(2))
            }
        }
        return out
    }

    /** Applies [plan] in batches; [progress] hears how many new cards are in after each batch. */
    private fun apply(r: ContentResolver, plan: PhoneBook.Plan, progress: (Int) -> Unit) {
        val ops = ArrayList<ContentProviderOperation>()
        var bytes = 0
        var inserted = 0
        var pendingCards = 0
        fun flush() {
            if (ops.isEmpty()) return
            r.applyBatch(ContactsContract.AUTHORITY, ops)
            ops.clear()
            bytes = 0
            if (pendingCards > 0) {
                inserted += pendingCards
                pendingCards = 0
                progress(inserted)
            }
        }
        for (id in plan.delete) {
            ops += ContentProviderOperation.newDelete(sync(ContentUris.withAppendedId(RawContacts.CONTENT_URI, id))).build()
            if (ops.size >= 200) flush()
        }
        flush()
        for (card in plan.insert) {
            val back = ops.size
            ops += ContentProviderOperation.newInsert(sync(RawContacts.CONTENT_URI))
                .withValue(RawContacts.ACCOUNT_NAME, ACCOUNT_NAME)
                .withValue(RawContacts.ACCOUNT_TYPE, ACCOUNT_TYPE)
                .withValue(RawContacts.SOURCE_ID, card.sourceId)
                .withValue(RawContacts.SYNC1, card.hash)
                .withValue(RawContacts.STARRED, if (card.starred) 1 else 0)
                .withValue(RawContacts.CUSTOM_RINGTONE, card.ringtone)
                .build()
            bytes += rows(ops, card) { b -> b.withValueBackReference(Data.RAW_CONTACT_ID, back) }
            pendingCards++
            // One card never spans two batches (back references); photos keep a batch small.
            if (ops.size >= 250 || bytes > 350_000) flush()
        }
        flush()
        for ((rawId, card) in plan.rewrite) {
            ops += ContentProviderOperation.newDelete(sync(Data.CONTENT_URI))
                .withSelection("${Data.RAW_CONTACT_ID} = ?", arrayOf(rawId.toString()))
                .build()
            ops += ContentProviderOperation.newUpdate(sync(ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId)))
                .withValue(RawContacts.SYNC1, card.hash)
                .withValue(RawContacts.STARRED, if (card.starred) 1 else 0)
                .withValue(RawContacts.CUSTOM_RINGTONE, card.ringtone)
                .build()
            bytes += rows(ops, card) { b -> b.withValue(Data.RAW_CONTACT_ID, rawId) }
            if (ops.size >= 250 || bytes > 350_000) flush()
        }
        flush()
    }

    /** The data rows of one card; returns roughly how many bytes they carry. */
    private fun rows(
        ops: MutableList<ContentProviderOperation>,
        card: PhoneBook.Card,
        owner: (ContentProviderOperation.Builder) -> ContentProviderOperation.Builder,
    ): Int {
        val row: RowOf = { mime ->
            owner(ContentProviderOperation.newInsert(sync(Data.CONTENT_URI))).withValue(Data.MIMETYPE, mime)
        }
        ops += nameRows(card, row)
        ops += reachRows(card, row)
        ops += extraRows(card, row)
        var bytes = 600
        card.photo?.let { file -> photo(OrbitRepository.photoFile(file)) }?.let { jpg ->
            ops += row(CommonDataKinds.Photo.CONTENT_ITEM_TYPE).withValue(CommonDataKinds.Photo.PHOTO, jpg).build()
            bytes += jpg.size
        }
        return bytes
    }

    private fun nameRows(card: PhoneBook.Card, row: RowOf): List<ContentProviderOperation> = buildList {
        val name = row(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        if (card.isOrg) {
            name.withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, card.display)
        } else {
            name.withValue(CommonDataKinds.StructuredName.GIVEN_NAME, card.given.ifBlank { null })
                .withValue(CommonDataKinds.StructuredName.FAMILY_NAME, card.family.ifBlank { null })
                .withValue(CommonDataKinds.StructuredName.PREFIX, card.prefix.ifBlank { null })
        }
        add(name.build())
        if (card.nickname.isNotBlank()) {
            add(row(CommonDataKinds.Nickname.CONTENT_ITEM_TYPE).withValue(CommonDataKinds.Nickname.NAME, card.nickname).build())
        }
        if (card.company.isNotBlank() || card.jobTitle.isNotBlank()) {
            add(
                row(CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Organization.COMPANY, card.company.ifBlank { null })
                    .withValue(CommonDataKinds.Organization.TITLE, card.jobTitle.ifBlank { null })
                    .withValue(CommonDataKinds.Organization.TYPE, CommonDataKinds.Organization.TYPE_WORK)
                    .build(),
            )
        }
    }

    private fun reachRows(card: PhoneBook.Card, row: RowOf): List<ContentProviderOperation> = buildList {
        card.numbers.forEach { n ->
            add(
                row(CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Phone.NUMBER, n.value)
                    .withValue(CommonDataKinds.Phone.TYPE, n.type)
                    .withValue(CommonDataKinds.Phone.LABEL, n.label)
                    .withValue(Data.IS_PRIMARY, if (n.primary) 1 else 0)
                    .build(),
            )
        }
        card.mails.forEach { m ->
            add(
                row(CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Email.ADDRESS, m.value)
                    .withValue(CommonDataKinds.Email.TYPE, m.type)
                    .withValue(CommonDataKinds.Email.LABEL, m.label)
                    .build(),
            )
        }
        val postalType = if (card.isOrg) CommonDataKinds.StructuredPostal.TYPE_WORK else CommonDataKinds.StructuredPostal.TYPE_HOME
        card.addresses.forEach { a ->
            add(
                row(CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, a)
                    .withValue(CommonDataKinds.StructuredPostal.TYPE, postalType)
                    .build(),
            )
        }
    }

    private fun extraRows(card: PhoneBook.Card, row: RowOf): List<ContentProviderOperation> = buildList {
        card.birthday?.let { b ->
            add(
                row(CommonDataKinds.Event.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Event.START_DATE, b)
                    .withValue(CommonDataKinds.Event.TYPE, CommonDataKinds.Event.TYPE_BIRTHDAY)
                    .build(),
            )
        }
        card.website?.let { w ->
            add(
                row(CommonDataKinds.Website.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Website.URL, w)
                    .withValue(CommonDataKinds.Website.TYPE, CommonDataKinds.Website.TYPE_HOMEPAGE)
                    .build(),
            )
        }
        // PLAN 9 A3: „In Orbit öffnen“ and the own fields right in the system's contact card.
        add(
            row(MIME_PROFILE)
                .withValue(Data.DATA1, card.sourceId)
                .withValue(Data.DATA2, "Orbit")
                .withValue(Data.DATA3, "In Orbit öffnen")
                .build(),
        )
        card.fields.forEach { f ->
            add(row(MIME_FIELD).withValue(Data.DATA1, card.sourceId).withValue(Data.DATA2, f.label).withValue(Data.DATA3, f.value).build())
        }
    }

    /** The profile picture at most 480 px – enough for every contact card, small for the batch. */
    private fun photo(file: File): ByteArray? = runCatching {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 480)
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    }.getOrNull()

    /** The Orbit contact behind one of Orbit's rows in a system contact card (PLAN 9 A3). */
    fun contactIdOf(ctx: Context, dataUri: Uri): String? = runCatching {
        ctx.contentResolver.query(dataUri, arrayOf(Data.DATA1, Data.MIMETYPE), null, null, null)?.use { c ->
            if (c.moveToFirst() && (c.getString(1) == MIME_PROFILE || c.getString(1) == MIME_FIELD)) c.getString(0) else null
        }
    }.getOrNull()

    /** Hook for the data: writes again whenever the contacts change (called after every save). */
    fun onSaved(ctx: Context, data: OrbitData) {
        if (data.settings.phoneBook) request(ctx)
    }
}
