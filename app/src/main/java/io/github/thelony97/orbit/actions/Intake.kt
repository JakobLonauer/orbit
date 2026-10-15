package io.github.thelony97.orbit.actions

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import io.github.thelony97.orbit.MainActivity
import io.github.thelony97.orbit.core.domain.VCardReader
import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.LabeledValue
import io.github.thelony97.orbit.data.Mutations
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.thelony97.orbit.data.newId

/**
 * „Kontakt hinzufügen“ landet in Orbit (PLAN 9 A2): any app that wants to create or extend a
 * contact (phone, messages, WhatsApp, Gmail, the camera reading a vCard QR) can open Orbit.
 * The handed-over values become a draft; Orbit's own pages do the rest.
 */
class ContactIntakeActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = intent
        lifecycleScope.launch {
            // Same process: the draft travels through memory, never through a (size-limited) extra.
            val intake = when (i?.action) {
                Intent.ACTION_INSERT, Intent.ACTION_INSERT_OR_EDIT ->
                    MainActivity.Intake(listOf(fromInsert(i.extras)), pick = i.action == Intent.ACTION_INSERT_OR_EDIT)
                Intent.ACTION_VIEW -> withContext(Dispatchers.IO) { cards(i.data) }.takeIf { it.isNotEmpty() }?.let { MainActivity.Intake(it, pick = false) }
                else -> null
            }
            if (intake != null) MainActivity.pendingIntake.value = intake
            startActivity(Intent(this@ContactIntakeActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
    }

    /** A .vcf file or a scanned vCard: read and parsed off the main thread. */
    private fun cards(uri: android.net.Uri?): List<Contact> = runCatching {
        val text = uri?.let { contentResolver.openInputStream(it)?.use { s -> s.readBytes().toString(Charsets.UTF_8) } }.orEmpty()
        VCardReader.read(text, ::newId)
    }.getOrDefault(emptyList())

    /** ContactsContract.Intents.Insert extras (and DATA rows) → a draft contact. */
    private fun fromInsert(extras: Bundle?): Contact {
        val e = extras ?: Bundle()
        val phones = ArrayList<LabeledValue>()
        val emails = ArrayList<LabeledValue>()
        fun phone(value: String?, type: Int?) {
            val v = value?.trim().orEmpty()
            if (v.any(Char::isDigit) && phones.none { it.value == v }) phones += LabeledValue(phoneLabel(type), v)
        }
        fun mail(value: String?) {
            val v = value?.trim().orEmpty()
            if ('@' in v && emails.none { it.value.equals(v, ignoreCase = true) }) emails += LabeledValue("Privat", v)
        }
        phone(e.getString(ContactsContract.Intents.Insert.PHONE), e.getInt(ContactsContract.Intents.Insert.PHONE_TYPE, 0).takeIf { it != 0 })
        phone(e.getString(ContactsContract.Intents.Insert.SECONDARY_PHONE), null)
        phone(e.getString(ContactsContract.Intents.Insert.TERTIARY_PHONE), null)
        mail(e.getString(ContactsContract.Intents.Insert.EMAIL))
        mail(e.getString(ContactsContract.Intents.Insert.SECONDARY_EMAIL))
        mail(e.getString(ContactsContract.Intents.Insert.TERTIARY_EMAIL))
        // Newer apps hand over whole data rows; the simple extras win where both exist.
        val rows = runCatching { e.getParcelableArrayList(ContactsContract.Intents.Insert.DATA, ContentValues::class.java) }.getOrNull().orEmpty()
        rows.forEach { row ->
            when (row.getAsString(ContactsContract.Data.MIMETYPE)) {
                CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> phone(row.getAsString(CommonDataKinds.Phone.NUMBER), row.getAsInteger(CommonDataKinds.Phone.TYPE))
                CommonDataKinds.Email.CONTENT_ITEM_TYPE -> mail(row.getAsString(CommonDataKinds.Email.ADDRESS))
            }
        }
        val fromRows = textFromRows(rows)
        val name = e.getString(ContactsContract.Intents.Insert.NAME).orEmpty().ifBlank { fromRows.name }
        val (first, last) = Mutations.splitMemberName(name)
        return Contact(
            id = newId(),
            firstName = first,
            lastName = last,
            company = e.getString(ContactsContract.Intents.Insert.COMPANY).orEmpty().ifBlank { fromRows.company }.trim(),
            position = e.getString(ContactsContract.Intents.Insert.JOB_TITLE).orEmpty().ifBlank { fromRows.position }.trim(),
            notes = e.getString(ContactsContract.Intents.Insert.NOTES).orEmpty().trim(),
            phones = phones,
            emails = emails,
        ).withAddress(e.getString(ContactsContract.Intents.Insert.POSTAL).orEmpty().ifBlank { fromRows.address })
    }

    private data class RowText(val name: String = "", val company: String = "", val position: String = "", val address: String = "")

    /** Name, company, role and address from handed-over data rows (first one of each wins). */
    private fun textFromRows(rows: List<ContentValues>): RowText = rows.fold(RowText()) { acc, row ->
        when (row.getAsString(ContactsContract.Data.MIMETYPE)) {
            CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> if (acc.name.isNotBlank()) acc else acc.copy(
                name = listOfNotNull(row.getAsString(CommonDataKinds.StructuredName.GIVEN_NAME), row.getAsString(CommonDataKinds.StructuredName.FAMILY_NAME))
                    .joinToString(" ").ifBlank { row.getAsString(CommonDataKinds.StructuredName.DISPLAY_NAME).orEmpty() },
            )
            CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> acc.copy(
                company = acc.company.ifBlank { row.getAsString(CommonDataKinds.Organization.COMPANY).orEmpty() },
                position = acc.position.ifBlank { row.getAsString(CommonDataKinds.Organization.TITLE).orEmpty() },
            )
            CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE ->
                acc.copy(address = acc.address.ifBlank { row.getAsString(CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS).orEmpty() })
            else -> acc
        }
    }

    private fun phoneLabel(type: Int?): String = when (type) {
        CommonDataKinds.Phone.TYPE_MOBILE -> "Mobil"
        CommonDataKinds.Phone.TYPE_HOME -> "Privat"
        CommonDataKinds.Phone.TYPE_WORK, CommonDataKinds.Phone.TYPE_WORK_MOBILE -> "Arbeit"
        else -> "Mobil"
    }
}

/** Markieren → Orbit (PLAN 9 A4): marked text in any app goes into Orbit's „Teilen“ sheet. */
class ProcessTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim()
        if (!text.isNullOrEmpty()) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, text)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        finish()
    }
}
