package io.github.thelony97.orbit.core.model.v1

import kotlinx.serialization.Serializable

/*
 * Frozen copy of the 1.0.7 storage format (schema 1). Only used to read old data
 * during the v1 -> v2 migration. Never change these classes.
 */

@Serializable
data class OrbitData(
    val schema: Int = 1,
    val contacts: List<Contact> = emptyList(),
    val groups: List<Group> = emptyList(),
    val entryTypes: List<EntryType> = emptyList(),
    val relations: List<Relation> = emptyList(),
    val meetings: List<Meeting> = emptyList(),
    val unknownNumbers: List<UnknownNumber> = emptyList(),
    val settings: Settings = Settings(),
)

@Serializable
data class LabeledValue(
    val label: String = "",
    val value: String = "",
)

/** A calendar day without time zone. [year] is optional (birthday without year). */
@Serializable
data class DayOfYear(
    val day: Int,
    val month: Int,
    val year: Int? = null,
)

@Serializable
data class Anniversary(
    val id: String,
    val label: String,
    val date: DayOfYear,
)

@Serializable
data class CheckItem(
    val id: String,
    val text: String,
    val done: Boolean = false,
    val createdAt: Long = 0,
)

@Serializable
data class SmsMessage(
    val id: Long,
    val at: Long,
    val incoming: Boolean,
    val text: String,
)

@Serializable
enum class EntrySource { MANUAL, CALL_LOG, SMS, APP_ACTION, MEETING }

@Serializable
data class Entry(
    val id: String,
    val typeId: String,
    val at: Long,
    val note: String = "",
    val source: EntrySource = EntrySource.MANUAL,
    val durationSec: Long? = null,
    val incoming: Boolean? = null,
    val externalId: String? = null,
    val messages: List<SmsMessage> = emptyList(),
)

@Serializable
enum class ContactKind { PERSON, ORG }

/** Cached geocoding result for [Contact.address]. [query] is the address it was computed for. */
@Serializable
data class GeoPoint(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val query: String = "",
    val found: Boolean = true,
)

/** A picture in the contact's own photo folder (separate from the profile photo). */
@Serializable
data class GalleryPhoto(
    val id: String,
    val file: String,
    val addedAt: Long = 0,
    val caption: String = "",
)

@Serializable
data class Contact(
    val id: String,
    /** Real person or an organisation (company, practice, hospital, authority …). */
    val kind: ContactKind = ContactKind.PERSON,
    /** Key of an [OrgCategory]; only used for organisations. */
    val orgCategory: String? = null,
    /** The app user's own entry ("Ich"). Exactly one exists. */
    val isMe: Boolean = false,
    val firstName: String = "",
    val lastName: String = "",
    val phones: List<LabeledValue> = emptyList(),
    val defaultPhone: Int = 0,
    val emails: List<LabeledValue> = emptyList(),
    val address: String = "",
    val birthday: DayOfYear? = null,
    val company: String = "",
    val position: String = "",
    val howWeMet: String = "",
    val notes: String = "",
    val photo: String? = null,
    val groupIds: List<String> = emptyList(),
    val favorite: Boolean = false,
    /** Personal rhythm in days. Overrides the group rhythm when set. */
    val rhythmDays: Int? = null,
    val paused: Boolean = false,
    val archived: Boolean = false,
    val snoozedUntil: Long? = null,
    val topics: List<CheckItem> = emptyList(),
    val gifts: List<CheckItem> = emptyList(),
    val anniversaries: List<Anniversary> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
    /** Epoch day of the due date we already sent a reminder for ("nur einmal erinnern"). */
    val remindedDueDay: Long? = null,
    val website: String = "",
    val geo: GeoPoint? = null,
    val gallery: List<GalleryPhoto> = emptyList(),
) {
    val isOrg: Boolean get() = kind == ContactKind.ORG
}

@Serializable
data class Group(
    val id: String,
    val name: String,
    val color: Long,
    val rhythmDays: Int? = null,
    /** Pinned groups show up as quick filters above the list. */
    val pinned: Boolean = false,
)

@Serializable
data class EntryType(
    val id: String,
    val name: String,
    val icon: String,
    val resetsTimer: Boolean,
    val builtin: Boolean = false,
)

@Serializable
data class Relation(
    val id: String,
    val fromId: String,
    val toId: String,
    /** Key of a [RelationKind]. */
    val kind: String,
)

@Serializable
data class Meeting(
    val id: String,
    val contactId: String,
    val start: Long,
    val end: Long,
    val place: String = "",
    val asked: Boolean = false,
    val resolved: Boolean = false,
)

@Serializable
data class UnknownNumber(
    val key: String,
    val display: String,
    val cachedName: String? = null,
    val calls: Int = 0,
    val sms: Int = 0,
    val lastAt: Long = 0,
    val dismissed: Boolean = false,
)

@Serializable
data class Settings(
    val onboardingDone: Boolean = false,
    val digestHour: Int = 9,
    val digestMinute: Int = 0,
    val minCallSeconds: Int = 60,
    val sortByLastName: Boolean = false,
    val lockEnabled: Boolean = true,
    val autoBackup: Boolean = true,
    val backupTreeUri: String? = null,
    val lastBackupAt: Long? = null,
    val suggestUnknown: Boolean = true,
    val noteAfterCall: Boolean = true,
    val topicsDuringCall: Boolean = true,
    val callSyncedUntil: Long = 0,
    val smsSyncedUntil: Long = 0,
    val autoUpdateCheck: Boolean = true,
    val lastUpdateCheck: Long = 0,
    /** Epoch day of the last daily summary, so it never fires twice a day. */
    val lastDigestDay: Long = 0,
    /** Key of a sort mode. */
    val listSort: String = "name",
    val listSortReversed: Boolean = false,
)
