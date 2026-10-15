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
import io.github.thelony97.orbit.data.OrbitRepository
import io.github.thelony97.orbit.data.newId

/**
 * „Kontakt hinzufügen“ landet in Orbit (PLAN 9 A2): any app that wants to create or extend a
 * contact (phone, messages, WhatsApp, Gmail, the camera reading a vCard QR) can open Orbit.
 * The handed-over values become a draft; Orbit's own pages do the rest.
 */
class ContactIntakeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = intent
        val target = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        when (i?.action) {
            Intent.ACTION_INSERT, Intent.ACTION_INSERT_OR_EDIT -> {
                val draft = fromInsert(i.extras)
                target.putExtra(MainActivity.EXTRA_INTAKE, OrbitRepository.json.encodeToString(Contact.serializer(), draft))
                target.putExtra(MainActivity.EXTRA_INTAKE_PICK, i.action == Intent.ACTION_INSERT_OR_EDIT)
            }
            Intent.ACTION_VIEW -> {
                val text = i.data?.let { uri -> runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull() }
                val cards = text?.let { VCardReader.read(it, ::newId) }.orEmpty()
                if (cards.isNotEmpty()) {
                    target.putExtra(MainActivity.EXTRA_INTAKE_CARDS, OrbitRepository.json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Contact.serializer()), cards))
                }
            }
        }
        startActivity(target)
        finish()
    }

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
        var name = e.getString(ContactsContract.Intents.Insert.NAME).orEmpty()
        var company = e.getString(ContactsContract.Intents.Insert.COMPANY).orEmpty()
        var position = e.getString(ContactsContract.Intents.Insert.JOB_TITLE).orEmpty()
        var address = e.getString(ContactsContract.Intents.Insert.POSTAL).orEmpty()
        val notes = e.getString(ContactsContract.Intents.Insert.NOTES).orEmpty()
        // Newer apps hand over whole data rows.
        val rows = runCatching { e.getParcelableArrayList(ContactsContract.Intents.Insert.DATA, ContentValues::class.java) }.getOrNull().orEmpty()
        for (row in rows) {
            when (row.getAsString(ContactsContract.Data.MIMETYPE)) {
                CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> phone(row.getAsString(CommonDataKinds.Phone.NUMBER), row.getAsInteger(CommonDataKinds.Phone.TYPE))
                CommonDataKinds.Email.CONTENT_ITEM_TYPE -> mail(row.getAsString(CommonDataKinds.Email.ADDRESS))
                CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> if (name.isBlank()) {
                    name = listOfNotNull(row.getAsString(CommonDataKinds.StructuredName.GIVEN_NAME), row.getAsString(CommonDataKinds.StructuredName.FAMILY_NAME))
                        .joinToString(" ").ifBlank { row.getAsString(CommonDataKinds.StructuredName.DISPLAY_NAME).orEmpty() }
                }
                CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                    if (company.isBlank()) company = row.getAsString(CommonDataKinds.Organization.COMPANY).orEmpty()
                    if (position.isBlank()) position = row.getAsString(CommonDataKinds.Organization.TITLE).orEmpty()
                }
                CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> if (address.isBlank()) address = row.getAsString(CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS).orEmpty()
            }
        }
        val (first, last) = Mutations.splitMemberName(name)
        return Contact(
            id = newId(),
            firstName = first,
            lastName = last,
            company = company.trim(),
            position = position.trim(),
            notes = notes.trim(),
            phones = phones,
            emails = emails,
        ).withAddress(address)
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
