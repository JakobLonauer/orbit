package io.github.thelony97.orbit.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class EntrySource { MANUAL, CALL_LOG, SMS, APP_ACTION, MEETING, MAIL, SHARE }

@Serializable
data class SmsMessage(
    val id: Long,
    val at: Long,
    val incoming: Boolean,
    val text: String,
)

/**
 * One mail in a bundled mail entry (PLAN 3.9): only subject, date, direction and where to find
 * it – never the text, which is loaded on tap.
 */
@Serializable
data class MailRef(
    val account: String,
    val folder: String,
    val uidValidity: Long,
    val uid: Long,
    val at: Long,
    val incoming: Boolean,
    val subject: String,
    /** The other side's address (sender of an incoming, first recipient of an outgoing mail). */
    val address: String,
    val messageId: String? = null,
) {
    /** Same mail across folders (Gmail labels) and after UIDVALIDITY changes. */
    val key: String get() = messageId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: "$account/$folder/$uidValidity/$uid"
}

/** One Verlauf row (SQLite `entries`). [externalId] is unique per contact ("call:<millis>", "sms:<day>"). */
@Serializable
data class HistoryEntry(
    val id: String,
    val contactId: String,
    val typeId: String,
    val at: Long,
    val note: String = "",
    val source: EntrySource = EntrySource.MANUAL,
    val durationSec: Long? = null,
    val incoming: Boolean? = null,
    val externalId: String? = null,
    val messages: List<SmsMessage> = emptyList(),
    /** Conversation with a hidden employee of the organisation [contactId]. */
    val memberId: String? = null,
    val attachmentIds: List<String> = emptyList(),
    /** Same id on the copies of one entry shared between several contacts. */
    val sharedId: String? = null,
    /** Mail reference "<accountId>/<folder>/<uid>" for mail entries. */
    val mailRef: String? = null,
    /** E-Mail v2 (PLAN 3.9): the mails of one day and direction, newest first. */
    val mails: List<MailRef> = emptyList(),
    /** For "Gratuliert"/"Geschenk": occasion, its year and who gave together. */
    val occasionId: String? = null,
    val occasionYear: Int? = null,
    val withIds: List<String> = emptyList(),
    /** PLAN 9 D5 „Auftrag“: what it cost (cents) and how it went (1–5 stars); the invoice is an attachment. */
    val amountCents: Long? = null,
    val stars: Int? = null,
    /** PLAN 9 E12 Sprachnotiz: the recording's file (next to the photos) and its waveform (0–9 per step). */
    val audio: String? = null,
    val wave: String = "",
)

/** Ablage item (SQLite `attachments`); [file] is relative to the app's attachment folder. */
@Serializable
data class Attachment(
    val id: String,
    val contactId: String,
    val file: String,
    val mime: String = "image/jpeg",
    val addedAt: Long = 0,
    val caption: String = "",
    /** PLAN 3.10: when the picture was taken (EXIF), for the Fotos page; null = unknown. */
    val takenAt: Long? = null,
    /** Original file name of a document (PDF) for its tile. */
    val name: String = "",
)

object BuiltinTypes {
    const val MEET = "meet"
    const val CALL = "call"
    const val VIDEO = "video"
    const val MESSAGE = "message"
    const val EMAIL = "email"
    const val EVENT = "event"
    const val CONGRATS = "congrats"
    const val GIFT = "gift"
    const val JOB = "job"
    const val SETTLED = "settled"
    const val NOTE = "note"

    val all = listOf(
        EntryType(MEET, "Treffen", "meet", builtin = true),
        EntryType(CALL, "Anruf", "call", builtin = true),
        EntryType(VIDEO, "Videocall", "video", builtin = true),
        EntryType(MESSAGE, "Nachricht", "message", builtin = true),
        EntryType(EMAIL, "E-Mail", "email", builtin = true),
        EntryType(EVENT, "Event", "event", builtin = true),
        EntryType(CONGRATS, "Gratuliert", "cake", builtin = true),
        EntryType(GIFT, "Geschenk", "gift", builtin = true),
        EntryType(JOB, "Auftrag", "job", builtin = true),
        EntryType(SETTLED, "Erledigt", "receipt", builtin = true),
        EntryType(NOTE, "Notiz", "note", builtin = true),
    )
}
