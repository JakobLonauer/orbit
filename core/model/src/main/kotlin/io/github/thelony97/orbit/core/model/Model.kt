package io.github.thelony97.orbit.core.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/*
 * Storage format v2 (PLAN 4). The JSON core holds everything small and relational.
 * History (Verlauf) and attachments (Ablage) live in SQLite as [HistoryEntry] / [Attachment].
 * New optional fields may be added with defaults at any time; removing or re-typing a field
 * needs schema 3 and a tested migration.
 */

const val SCHEMA_V2 = 2

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class OrbitDoc(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val schema: Int = SCHEMA_V2,
    val contacts: List<Contact> = emptyList(),
    val groups: List<Group> = emptyList(),
    val entryTypes: List<EntryType> = emptyList(),
    /** Human ↔ human only. Person ↔ organisation lives in [orgLinks]. */
    val relations: List<Relation> = emptyList(),
    val orgLinks: List<OrgLink> = emptyList(),
    val meetings: List<Meeting> = emptyList(),
    val unknownNumbers: List<UnknownNumber> = emptyList(),
    val settings: Settings = Settings(),
)

@Serializable
data class LabeledValue(
    val label: String = "",
    val value: String = "",
)

@Serializable
data class CheckItem(
    val id: String,
    val text: String,
    val done: Boolean = false,
    val createdAt: Long = 0,
)

@Serializable
enum class ContactKind { PERSON, ORG }

@Serializable
data class GeoPoint(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val query: String = "",
    val found: Boolean = true,
)

@Serializable
enum class OccasionKind(val label: String) {
    BIRTHDAY("Geburtstag"),
    WEDDING("Hochzeitstag"),
    ANNIVERSARY("Jahrestag"),
    MET("Kennenlerntag"),
    NAME_DAY("Namenstag"),
    MEMORIAL("Gedenktag"),
    CUSTOM("Anlass"),
}

@Serializable
enum class Repeat { YEARLY, ONCE }

/** Anlass (PLAN 3.5). Stored once at its owner; [sharedWith] makes it appear at a second contact too. */
@Serializable
data class Occasion(
    val id: String,
    val kind: OccasionKind,
    /** Free label for [OccasionKind.CUSTOM]; optional extra wording otherwise. */
    val label: String = "",
    val date: CalendarDay,
    val repeat: Repeat = Repeat.YEARLY,
    val pinned: Boolean = false,
    /** Days before the date to remind; 0 = on the day. Empty = no reminder. */
    val reminders: List<Int> = listOf(0),
    val sharedWith: String? = null,
    val note: String = "",
) {
    /** What to call it: the own label, else the kind ("Hochzeitstag"). */
    val title: String get() = label.ifBlank { kind.label }

    companion object {
        val BIRTHDAY_REMINDERS = listOf(0, 7)
    }
}

@Serializable
enum class OpenWith { ORBIT_TAB, CHROME, APP }

/** Portal / link tile (PLAN 3.7). Never stores passwords – [note] is for things like a login name. */
@Serializable
data class Link(
    val id: String,
    val title: String = "",
    val url: String,
    val note: String = "",
    val pinned: Boolean = false,
    val openWith: OpenWith = OpenWith.ORBIT_TAB,
    val appPackage: String? = null,
    /** File name of the cached site logo in the attachment store. */
    val logo: String? = null,
)

@Serializable
data class CustomField(
    val id: String,
    val label: String,
    val value: String,
)

/** Hidden employee of an organisation (PLAN 3.6). Never listed or searched outside its org. */
@Serializable
data class Member(
    val id: String,
    val name: String,
    val role: String = "",
    val photo: String? = null,
    val phones: List<LabeledValue> = emptyList(),
    val emails: List<LabeledValue> = emptyList(),
    val note: String = "",
    val createdAt: Long = 0,
)

@Serializable
data class Contact(
    val id: String,
    val kind: ContactKind = ContactKind.PERSON,
    val orgCategory: String? = null,
    val isMe: Boolean = false,
    val firstName: String = "",
    val lastName: String = "",
    val phones: List<LabeledValue> = emptyList(),
    val defaultPhone: Int = 0,
    val emails: List<LabeledValue> = emptyList(),
    val addresses: List<LabeledValue> = emptyList(),
    val company: String = "",
    val position: String = "",
    val howWeMet: String = "",
    val notes: String = "",
    /** Short note pinned to the top of the contact page and shown on incoming calls. */
    val pinnedNote: String = "",
    val photo: String? = null,
    val groupIds: List<String> = emptyList(),
    val favorite: Boolean = false,
    val archived: Boolean = false,
    val archivedAt: Long? = null,
    val topics: List<CheckItem> = emptyList(),
    val gifts: List<CheckItem> = emptyList(),
    val dates: List<Occasion> = emptyList(),
    val links: List<Link> = emptyList(),
    val fields: List<CustomField> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val deletedAt: Long? = null,
    val geo: GeoPoint? = null,
    // Organisation only
    val members: List<Member> = emptyList(),
    val numberPrefixes: List<String> = emptyList(),
    val emailDomains: List<String> = emptyList(),
    val mapsUrl: String? = null,
    /** OSM opening_hours syntax, e.g. "Mo-Fr 08:00-12:00". */
    val openingHours: String = "",
    /**
     * In memory only: this contact's Verlauf, newest first. Stored in SQLite, never in the JSON
     * core (ADR 0003); the repository fills it on load and writes the difference on save.
     */
    @Transient val entries: List<HistoryEntry> = emptyList(),
    /** In memory only: the photo folder (Ablage), newest first. Stored in SQLite. */
    @Transient val gallery: List<Attachment> = emptyList(),
) {
    val isOrg: Boolean get() = kind == ContactKind.ORG
    val address: String get() = addresses.firstOrNull()?.value.orEmpty()
    val birthday: CalendarDay? get() = dates.firstOrNull { it.kind == OccasionKind.BIRTHDAY }?.date
    /** Anlässe except the birthday. */
    val anniversaries: List<Occasion> get() = dates.filter { it.kind != OccasionKind.BIRTHDAY }
    val website: String get() = links.firstOrNull()?.url.orEmpty()

    fun withAddress(value: String): Contact {
        val v = value.trim()
        val rest = addresses.drop(1)
        return copy(addresses = if (v.isEmpty()) rest else listOf((addresses.firstOrNull() ?: LabeledValue()).copy(value = v)) + rest)
    }

    /** Sets or removes the birthday; keeps its id, reminders and note when it already exists. */
    fun withBirthday(day: CalendarDay?, newId: () -> String): Contact {
        val existing = dates.firstOrNull { it.kind == OccasionKind.BIRTHDAY }
        val others = dates.filter { it.kind != OccasionKind.BIRTHDAY }
        return when {
            day == null -> copy(dates = others)
            existing != null -> copy(dates = listOf(existing.copy(date = day)) + others)
            else -> copy(dates = listOf(Occasion(newId(), OccasionKind.BIRTHDAY, date = day, reminders = Occasion.BIRTHDAY_REMINDERS)) + others)
        }
    }

    /** Sets the first link (the web site); blank removes it. */
    fun withWebsite(url: String, newId: () -> String): Contact {
        val v = url.trim()
        val rest = links.drop(1)
        if (v.isEmpty()) return copy(links = rest)
        val full = if (v.startsWith("http://") || v.startsWith("https://")) v else "https://$v"
        val first = links.firstOrNull()?.copy(url = full) ?: Link(newId(), "Webseite", full)
        return copy(links = listOf(first) + rest)
    }
}

@Serializable
data class Group(
    val id: String,
    val name: String,
    val color: Long,
    val pinned: Boolean = false,
)

@Serializable
data class EntryType(
    val id: String,
    val name: String,
    val icon: String,
    val builtin: Boolean = false,
)

@Serializable
data class Relation(
    val id: String,
    val fromId: String,
    val toId: String,
    val kind: String,
)

/** Zuordnung person → organisation (PLAN 3.6), strictly separate from [Relation]. */
@Serializable
data class OrgLink(
    val id: String,
    val personId: String,
    val orgId: String,
    /** Key of an [AssignmentRole]; [AssignmentRoles.CUSTOM] uses [label]. */
    val role: String,
    val label: String = "",
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
    /** PLAN 3.13: lock right away (0) or after 60 / 300 seconds away from Orbit. */
    val lockDelaySeconds: Int = 0,
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
    val lastDigestDay: Long = 0,
    val listSort: String = "name",
    val listSortReversed: Boolean = false,
    /** Show organisations in lists, search, net and map. Off by default (PLAN 3.0). */
    val showOrgs: Boolean = false,
    /** "Gratuliert"/"Geschenk" ticking in the birthday tab (PLAN 3.5). */
    val giftTracking: Boolean = true,
)
