package io.github.thelony97.orbit.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.ContactKind
import io.github.thelony97.orbit.data.DayOfYear
import io.github.thelony97.orbit.data.LabeledValue
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.min

object Photos {
    private const val SIZE = 640

    /** Crops to a centred square, scales down and stores as JPEG. Returns the file name. */
    suspend fun saveFromUri(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching { decode(ctx, uri)?.let { store(it) } }.getOrNull()
    }

    /** Picked picture for the crop frame (PLAN 3.3), at most twice the stored size. */
    suspend fun decode(ctx: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val source = ImageDecoder.createSource(ctx.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > SIZE * 2) decoder.setTargetSampleSize(longest / (SIZE * 2))
            }
        }.getOrNull()
    }

    /** Stores the square the user framed. Returns the file name. */
    suspend fun storeSquare(bitmap: Bitmap, square: io.github.thelony97.orbit.core.domain.CropMath.Square): String? = withContext(Dispatchers.IO) {
        runCatching {
            val cut = Bitmap.createBitmap(bitmap, square.left, square.top, square.side, square.side)
            store(cut)
        }.getOrNull()
    }

    suspend fun saveFromStream(stream: InputStream): String? = withContext(Dispatchers.IO) {
        runCatching { BitmapFactory.decodeStream(stream)?.let { store(it) } }.getOrNull()
    }

    private fun store(bitmap: Bitmap): String {
        val side = min(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val scaled = if (side > SIZE) Bitmap.createScaledBitmap(square, SIZE, SIZE, true) else square
        val name = "${newId()}.jpg"
        OrbitRepository.photoFile(name).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return name
    }

    fun delete(name: String?) {
        if (name != null) OrbitRepository.photoFile(name).delete()
    }
}

/** Reads one contact from the phone book (needs READ_CONTACTS). */
object PhoneImport {

    suspend fun read(ctx: Context, contactUri: Uri): Contact? = withContext(Dispatchers.IO) {
        val resolver = ctx.contentResolver
        val contactId = resolver.query(contactUri, arrayOf(ContactsContract.Contacts._ID), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else null
        } ?: return@withContext null

        var first = ""
        var last = ""
        var display = ""
        val phones = mutableListOf<LabeledValue>()
        val emails = mutableListOf<LabeledValue>()
        var address = ""
        var birthday: DayOfYear? = null
        var company = ""
        var position = ""
        var notes = ""

        val projection = arrayOf(
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1, ContactsContract.Data.DATA2,
            ContactsContract.Data.DATA3, ContactsContract.Data.DATA4,
        )
        resolver.query(
            ContactsContract.Data.CONTENT_URI, projection,
            "${ContactsContract.Data.CONTACT_ID} = ?", arrayOf(contactId.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val mime = c.getString(0)
                val d1 = c.getString(1).orEmpty()
                val d2 = c.getString(2)
                val d3 = c.getString(3)
                val d4 = c.getString(4)
                when (mime) {
                    CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                        display = d1
                        first = d2.orEmpty()
                        last = d3.orEmpty()
                    }
                    CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> if (d1.isNotBlank() && phones.none { it.value == d1 }) {
                        phones += LabeledValue(phoneLabel(d2?.toIntOrNull(), d3), d1)
                    }
                    CommonDataKinds.Email.CONTENT_ITEM_TYPE -> if (d1.isNotBlank()) {
                        emails += LabeledValue(emailLabel(d2?.toIntOrNull()), d1)
                    }
                    CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> if (address.isBlank()) address = d1
                    CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                        company = d1
                        position = d4.orEmpty()
                    }
                    CommonDataKinds.Note.CONTENT_ITEM_TYPE -> notes = d1
                    CommonDataKinds.Event.CONTENT_ITEM_TYPE ->
                        if (d2?.toIntOrNull() == CommonDataKinds.Event.TYPE_BIRTHDAY) birthday = parseDate(d1)
                }
            }
        }
        // Like Apple Contacts: an entry with a company but no personal name is an organisation.
        val isOrg = first.isBlank() && last.isBlank() && company.isNotBlank() && (display.isBlank() || display == company)
        if (!isOrg && first.isBlank() && last.isBlank()) first = display

        val photoUri = Uri.withAppendedPath(
            ContactsContract.Contacts.CONTENT_URI.buildUpon().appendPath(contactId.toString()).build(),
            ContactsContract.Contacts.Photo.DISPLAY_PHOTO,
        )
        val photo = runCatching {
            resolver.openInputStream(photoUri)?.use { Photos.saveFromStream(it) }
        }.getOrNull() ?: runCatching {
            ContactsContract.Contacts.openContactPhotoInputStream(
                resolver, ContactsContract.Contacts.CONTENT_URI.buildUpon().appendPath(contactId.toString()).build(), true,
            )?.use { Photos.saveFromStream(it) }
        }.getOrNull()

        Contact(
            id = newId(),
            kind = if (isOrg) ContactKind.ORG else ContactKind.PERSON,
            firstName = first,
            lastName = last,
            phones = phones,
            emails = emails,
            company = company,
            position = position,
            notes = notes,
            photo = photo,
        ).withAddress(address).withBirthday(birthday, ::newId)
    }

    private fun phoneLabel(type: Int?, custom: String?): String = when (type) {
        CommonDataKinds.Phone.TYPE_MOBILE -> "Mobil"
        CommonDataKinds.Phone.TYPE_HOME -> "Privat"
        CommonDataKinds.Phone.TYPE_WORK, CommonDataKinds.Phone.TYPE_WORK_MOBILE -> "Arbeit"
        CommonDataKinds.Phone.TYPE_CUSTOM -> custom ?: "Sonstige"
        else -> "Sonstige"
    }

    private fun emailLabel(type: Int?): String = when (type) {
        CommonDataKinds.Email.TYPE_HOME -> "Privat"
        CommonDataKinds.Email.TYPE_WORK -> "Arbeit"
        else -> "Sonstige"
    }

    /** "1990-05-17", "--05-17" or "17.05.1990". */
    fun parseDate(raw: String): DayOfYear? {
        val s = raw.trim()
        Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""").find(s)?.let { m ->
            val (y, mo, d) = m.destructured
            return DayOfYear(d.toInt(), mo.toInt(), y.toInt().takeIf { it > 1800 })
        }
        Regex("""^--(\d{1,2})-?(\d{1,2})""").find(s)?.let { m ->
            val (mo, d) = m.destructured
            return DayOfYear(d.toInt(), mo.toInt(), null)
        }
        Regex("""^(\d{1,2})\.(\d{1,2})\.(\d{4})?""").find(s)?.let { m ->
            val (d, mo, y) = m.destructured
            return DayOfYear(d.toInt(), mo.toInt(), y.toIntOrNull())
        }
        return null
    }
}
