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

/** R4: person ↔ organisation relations live in [OrbitDoc.orgLinks] (Zuordnungen); see MigrateV2. */
const val SCHEMA_V3 = 3

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class OrbitDoc(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val schema: Int = SCHEMA_V3,
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
    /** PLAN 3.3 „Themen optional mit Datum“: ISO day (2026-10-20) the morning notification names it; null = none. */
    val remindOn: String? = null,
    /** PLAN 9 E7 Geschenke-Board (gifts only): bought, waiting to be given. */
    val bought: Boolean = false,
    /** PLAN 9 E7: what it costs (cents). */
    val priceCents: Long? = null,
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

/** PLAN 9 B4: one step of a Hotline-Makro. */
@Serializable
enum class MacroStepKind {
    /** [MacroStep.value] = the keys („1“, „#“). */
    KEY,
    /** [MacroStep.ms] = how long to wait. */
    PAUSE,
    /** Until Jakob taps „Weiter“ (dial string „;“). */
    WAIT,
    /** [MacroStep.value] = id of an own field whose digits are typed (Kundennummer …). */
    FIELD,
}

@Serializable
data class MacroStep(val kind: MacroStepKind, val value: String = "", val ms: Long = 0)

/** PLAN 9 B4: a recorded or edited key sequence for a hotline („Kundenservice“). */
@Serializable
data class Macro(val id: String, val name: String = "", val steps: List<MacroStep> = emptyList())

/** How „Gratulieren“ greets one person: [formal] = Sie, [language] an ISO 639-1 code. */
@Serializable
data class Greeting(
    val formal: Boolean = false,
    val language: String = "de",
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
    /** PLAN 9 D5: the Gewerk of a craft business („Installateur“). */
    val trade: String = "",
    val isMe: Boolean = false,
    val firstName: String = "",
    val lastName: String = "",
    /** PLAN 9 A10: academic title before the name („Mag.a“, „Dr.in“, „DI“). */
    val title: String = "",
    /** PLAN 9 A10: Rufname („Hansi“) – found by search and the dialpad, used in sentences. */
    val nickname: String = "",
    /** PLAN 9 A10/B7: Anrede word („Frau“, „Herr“); empty = none. Du/Sie lives in [greeting]. */
    val salutation: String = "",
    /** PLAN 9 A8: ringtone (content URI) in the phone book; null = the group's, else the phone's. */
    val ringtone: String? = null,
    val phones: List<LabeledValue> = emptyList(),
    val defaultPhone: Int = 0,
    /** PLAN 3.12 „Dual-SIM pro Kontakt“: the SIM (phone account id) Orbit calls this contact with; null = the phone decides. */
    val simId: String? = null,
    /** PLAN 9 B10: calls to this contact go out with #31# – the own number stays hidden. */
    val hideNumber: Boolean = false,
    /** PLAN 9 B11 Anruf-Poster: a picture of the gallery shown full screen when they call; null = the profile photo. */
    val poster: String? = null,
    /** PLAN 3.5 Gratulations-Vorlage: Du/Sie and language of the „Gratulieren“ text; null = Du, Deutsch. */
    val greeting: Greeting? = null,
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
    /** PLAN 9 D2: Telefonzeiten, same syntax as [openingHours] („Mo-Fr 07:30-11:00“). */
    val phoneHours: String = "",
    /** PLAN 9 D2: „Urlaub bis …“ (ISO day) – overlays opening and phone hours. */
    val vacationUntil: String? = null,
    /** PLAN 9 D2: who stands in during the holiday („→ Dr. Mayr“), a contact id. */
    val vacationSubstitute: String? = null,
    /** PLAN 9 B4: Hotline-Makros – keys, pauses, „Warten auf Weiter“ and own fields, played in a call. */
    val macros: List<Macro> = emptyList(),
    /** PLAN 9 D3: Verträge with this organisation (Handy, Strom, Versicherung …). */
    val contracts: List<Contract> = emptyList(),
    /** PLAN 9 E14 Tote Daten: numbers/addresses an SMS or mail could not reach (shown orange). */
    val dead: List<String> = emptyList(),
    /** PLAN 9 E7: what gifts for this person may cost in a year (cents); null = no budget. */
    val giftBudgetCents: Long? = null,
    /** PLAN 9 D6: things lent or borrowed and money owed, either way. */
    val loans: List<Loan> = emptyList(),
    /** PLAN 9 D7: the WLAN of a place (an organisation's guest network). */
    val wifi: List<WifiNet> = emptyList(),
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
    /** PLAN 9 A8: ringtone for every member without an own one (phone book). */
    val ringtone: String? = null,
    /** PLAN 9 B9: the SIM members are called with when they have none of their own (two SIMs only). */
    val simId: String? = null,
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

/**
 * PLAN 9 D3: a contract with an organisation. Dates are ISO days; months 0 = not set. The
 * „Kündigen bis …“ day follows from [start], [minMonths], [renewMonths] and [noticeMonths]/[noticeDays].
 */
@Serializable
data class Contract(
    val id: String,
    val name: String = "",
    val number: String = "",
    val start: String? = null,
    /** Mindestlaufzeit; 0 = none (cancel any time with the notice). */
    val minMonths: Int = 0,
    /** Verlängerung after the first term; 0 = runs on indefinitely (monthly cancellable). */
    val renewMonths: Int = 12,
    val noticeMonths: Int = 0,
    val noticeDays: Int = 0,
    /** „19,90 €/Monat“ – as typed. */
    val price: String = "",
    /** Attachment file names (Ablage). */
    val documents: List<String> = emptyList(),
    /** ISO day the cancellation went out; the contract then ends with its term. */
    val cancelledOn: String? = null,
    val note: String = "",
)

/**
 * PLAN 9 D6: something lent/borrowed or money owed. [toThem] = from Jakob to them (he lent it, they
 * owe him); otherwise they gave it / he owes them. Money when [amountCents] is set.
 */
@Serializable
data class Loan(
    val id: String,
    val what: String = "",
    val amountCents: Long? = null,
    val toThem: Boolean = true,
    /** ISO day. */
    val since: String = "",
    /** ISO day it came back / was paid; then it is history. */
    val settledOn: String? = null,
)

/** PLAN 9 D7: a stored WLAN. [security] „WPA“ (WPA2/3 personal), „WEP“ or „nopass“. */
@Serializable
data class WifiNet(
    val id: String,
    val ssid: String,
    val password: String = "",
    val security: String = "WPA",
)

/** PLAN 9 E15: a Smart-Gruppe – a saved Orbit-Zeile („#uni hat:geburtstag“). */
@Serializable
data class SmartGroup(val id: String, val name: String, val query: String)

/** PLAN 9 E6: a Textbaustein; {Vorname} {Rufname} {Anrede} {Kundennummer} {Vertragsnummer} {Datum} are filled per contact. */
@Serializable
data class Snippet(val id: String, val name: String, val text: String)

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
    /** PLAN 3.10: keep pictures in full size instead of 2048 px on the long edge. */
    val keepOriginalPhotos: Boolean = false,
    /** PLAN 3.13a „Treffen am Morgen“: topics, last note and family before a planned meeting. */
    val meetingBrief: Boolean = true,
    /** PLAN 3.12 without the dialer role: who is calling, as a notification while it rings. */
    val callerInfo: Boolean = true,
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
    /** PLAN 3.12 „Ablehnen mit SMS-Vorlage“: the replies offered while it rings. */
    val smsTemplates: List<String> = DEFAULT_SMS_TEMPLATES,
    /** PLAN 9 A1: Orbit writes its contacts into the phone's address book (own account „Orbit“). */
    val phoneBook: Boolean = false,
    /** PLAN 9 A1: organisations go into the phone book too. */
    val phoneBookOrgs: Boolean = true,
    /** PLAN 9 A13: every occasion in Orbit's own calendar on the phone. */
    val orbitCalendar: Boolean = false,
    /** PLAN 9.2 Geist: stable keys of suggestions Jakob swiped away – they never come back. */
    val dismissedGhosts: List<String> = emptyList(),
    /** PLAN 9 B1: unknown Austrian callers looked up on the Herold website (once per number and week). */
    val callerLookup: Boolean = true,
    /**
     * PLAN 9 B8 „Abwehr ohne Verlust“: Orbit as the call filter silences unknown callers by fixed
     * rules – never rejects. Works only while Jakob gave Orbit the call-filter role.
     */
    val screening: Boolean = false,
    /** B8 rule „Unbekannt nachts (22–7)“. */
    val screenNight: Boolean = true,
    /** B8 rule „Mehrwert“: premium and shared-cost numbers. */
    val screenPremium: Boolean = true,
    /** B8 rule „Land“: region codes („RS“, „NG“) whose unknown callers are silenced. */
    val screenCountries: List<String> = emptyList(),
    /** B8 rule „Nummernserie“: number beginnings („0720 99“, „+44 7“) that are silenced when unknown. */
    val screenSeries: List<String> = emptyList(),
    /**
     * PLAN 9 B12 Bewegungsgesten: contact open + to the ear = call; ringing + turned over = silent;
     * from the ear onto the table = speaker. Off until Jakob switches it on; never in battery saver.
     */
    val motionGestures: Boolean = false,
    /** PLAN 9 D8: Ankommen – geofences at places with content; off until Jakob switches it on. */
    val arrive: Boolean = false,
    /** PLAN 9 E6: Textbausteine with {Platzhaltern}; „/“ in a text line offers them. */
    val snippets: List<Snippet> = DEFAULT_SNIPPETS,
    /** PLAN 9 E17: a missed call during a busy appointment gets an SMS with the time Jakob calls back. */
    val autoBusySms: Boolean = false,
    /** PLAN 9 E17: only callers in these groups; empty = everyone in Orbit. */
    val autoBusyGroups: List<String> = emptyList(),
    /** PLAN 9 E15: saved searches of the Orbit-Zeile, shown as chips next to the groups. */
    val smartGroups: List<SmartGroup> = emptyList(),
) {
    companion object {
        val DEFAULT_SNIPPETS = listOf(
            Snippet("geburtstag", "Geburtstag", "Alles Gute zum Geburtstag, {Rufname}!"),
            Snippet("termin", "Termin", "{Anrede}, ich möchte gerne einen Termin vereinbaren. Meine Kundennummer: {Kundennummer}."),
            Snippet("danke", "Danke", "Danke, {Rufname}!"),
        )

        val DEFAULT_SMS_TEMPLATES = listOf(
            "Kann gerade nicht – ich rufe zurück.",
            "Bin im Termin, melde mich später.",
            "Schreib mir bitte kurz.",
        )
    }
}
