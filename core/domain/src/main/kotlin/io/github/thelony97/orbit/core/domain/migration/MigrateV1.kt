package io.github.thelony97.orbit.core.domain.migration

import io.github.thelony97.orbit.core.model.Attachment
import io.github.thelony97.orbit.core.model.BuiltinTypes
import io.github.thelony97.orbit.core.model.CalendarDay
import io.github.thelony97.orbit.core.model.CheckItem
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.ContactKind
import io.github.thelony97.orbit.core.model.EntrySource
import io.github.thelony97.orbit.core.model.EntryType
import io.github.thelony97.orbit.core.model.GeoPoint
import io.github.thelony97.orbit.core.model.Group
import io.github.thelony97.orbit.core.model.HistoryEntry
import io.github.thelony97.orbit.core.model.LabeledValue
import io.github.thelony97.orbit.core.model.Link
import io.github.thelony97.orbit.core.model.Meeting
import io.github.thelony97.orbit.core.model.Occasion
import io.github.thelony97.orbit.core.model.OccasionKind
import io.github.thelony97.orbit.core.model.OrbitDoc
import io.github.thelony97.orbit.core.model.OrgLink
import io.github.thelony97.orbit.core.model.Relation
import io.github.thelony97.orbit.core.model.Settings
import io.github.thelony97.orbit.core.model.SmsMessage
import io.github.thelony97.orbit.core.model.UnknownNumber
import io.github.thelony97.orbit.core.model.v1.OrbitData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.github.thelony97.orbit.core.model.v1.Contact as V1Contact
import io.github.thelony97.orbit.core.model.v1.Entry as V1Entry

/**
 * 1.0.7 (schema 1) -> schema 2. Pure: no I/O, ids come from [newId] so tests are deterministic.
 * The caller keeps a copy of the old file before writing the result (PLAN 2, data safety).
 */
object MigrateV1 {

    data class Result(
        val doc: OrbitDoc,
        val history: List<HistoryEntry>,
        val attachments: List<Attachment>,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    /** Schema of a stored document; documents without the field are schema 1. */
    fun schemaOf(text: String): Int =
        runCatching { json.parseToJsonElement(text).jsonObject["schema"]?.jsonPrimitive?.intOrNull }
            .getOrNull() ?: 1

    fun fromJson(text: String, newId: () -> String): Result =
        migrate(json.decodeFromString(OrbitData.serializer(), text), newId)

    fun migrate(old: OrbitData, newId: () -> String): Result {
        val byId = old.contacts.associateBy { it.id }
        val history = ArrayList<HistoryEntry>()
        val attachments = ArrayList<Attachment>()

        val contacts = old.contacts.map { c ->
            c.entries.mapTo(history) { it.toHistory(c.id) }
            c.gallery.mapTo(attachments) { Attachment(it.id, c.id, it.file, "image/jpeg", it.addedAt, it.caption) }
            c.toV2(newId)
        }

        // Person ↔ organisation relations stay relations for now: the screens read them there.
        // R4 moves them into orgLinks (see roleFor) together with the Zuordnungen UI.
        val relations = old.relations
            .filter { byId[it.fromId] != null && byId[it.toId] != null }
            .map { Relation(it.id, it.fromId, it.toId, it.kind) }
        val orgLinks = emptyList<OrgLink>()

        val types = old.entryTypes.map { EntryType(it.id, it.name, it.icon, it.builtin) }
        val missing = BuiltinTypes.all.filter { b -> types.none { it.id == b.id } }

        val doc = OrbitDoc(
            contacts = contacts,
            groups = old.groups.map { Group(it.id, it.name, it.color, it.pinned) },
            entryTypes = types + missing,
            relations = relations,
            orgLinks = orgLinks.distinctBy { Triple(it.personId, it.orgId, it.role + it.label) },
            meetings = old.meetings.map { Meeting(it.id, it.contactId, it.start, it.end, it.place, it.asked, it.resolved) },
            unknownNumbers = old.unknownNumbers.map {
                UnknownNumber(it.key, it.display, it.cachedName, it.calls, it.sms, it.lastAt, it.dismissed)
            },
            settings = old.settings.toV2(),
        )
        return Result(doc, history, attachments)
    }

    /** v1 org relation kinds -> assignment roles; anything else keeps its wording as a custom role. */
    internal fun roleFor(kind: String): Pair<String, String> = when (kind) {
        "works_at" -> "employer" to ""
        "patient" -> "doctor" to ""
        "client" -> "service" to ""
        "member" -> "club" to ""
        "colleague" -> "colleague" to ""
        "boss" -> "supervisor" to ""
        else -> "custom" to (V1_LABELS[kind] ?: kind)
    }

    private val V1_LABELS = mapOf(
        "partner" to "Partner/in", "spouse" to "Ehepartner/in", "parent" to "Elternteil",
        "sibling" to "Geschwister", "grandparent" to "Großelternteil", "relative" to "Verwandt",
        "friend" to "Freund/in", "roommate" to "Mitbewohner/in",
    )

    /** Free-text anniversary labels -> kind. */
    fun kindFor(label: String): OccasionKind {
        val l = label.lowercase()
        return when {
            "hochzeit" in l -> OccasionKind.WEDDING
            "namenstag" in l -> OccasionKind.NAME_DAY
            "kennengelernt" in l || "kennenlern" in l -> OccasionKind.MET
            "gedenk" in l || "todestag" in l -> OccasionKind.MEMORIAL
            "jahrestag" in l -> OccasionKind.ANNIVERSARY
            else -> OccasionKind.CUSTOM
        }
    }

    private fun V1Contact.toV2(newId: () -> String): Contact {
        val dates = buildList {
            birthday?.let {
                add(Occasion(newId(), OccasionKind.BIRTHDAY, date = CalendarDay(it.day, it.month, it.year),
                    reminders = Occasion.BIRTHDAY_REMINDERS))
            }
            anniversaries.forEach { a ->
                val kind = kindFor(a.label)
                // Keep Jakob's own wording ("Hochzeit Oma & Opa"); drop it only if it just repeats the kind.
                add(Occasion(a.id, kind, label = if (a.label.trim().equals(kind.label, ignoreCase = true)) "" else a.label.trim(),
                    date = CalendarDay(a.date.day, a.date.month, a.date.year)))
            }
        }
        val links = website.trim().takeIf { it.isNotEmpty() }?.let { listOf(Link(newId(), "Webseite", normalizeUrl(it))) }
            .orEmpty()
        return Contact(
            id = id,
            kind = if (kind == io.github.thelony97.orbit.core.model.v1.ContactKind.ORG) ContactKind.ORG else ContactKind.PERSON,
            orgCategory = orgCategory,
            isMe = isMe,
            firstName = firstName,
            lastName = lastName,
            phones = phones.map { LabeledValue(it.label, it.value) },
            defaultPhone = defaultPhone,
            emails = emails.map { LabeledValue(it.label, it.value) },
            addresses = address.trim().takeIf { it.isNotEmpty() }?.let { listOf(LabeledValue("", it)) }.orEmpty(),
            company = company,
            position = position,
            howWeMet = howWeMet,
            notes = notes,
            photo = photo,
            groupIds = groupIds,
            favorite = favorite,
            archived = archived,
            archivedAt = if (archived) updatedAt else null,
            topics = topics.map { CheckItem(it.id, it.text, it.done, it.createdAt) },
            gifts = gifts.map { CheckItem(it.id, it.text, it.done, it.createdAt) },
            dates = dates,
            links = links,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
            geo = geo?.let { GeoPoint(it.lat, it.lng, it.query, it.found) },
        )
    }

    private fun V1Entry.toHistory(contactId: String) = HistoryEntry(
        id = id,
        contactId = contactId,
        typeId = typeId,
        at = at,
        note = note,
        source = EntrySource.valueOf(source.name),
        durationSec = durationSec,
        incoming = incoming,
        externalId = externalId,
        messages = messages.map { SmsMessage(it.id, it.at, it.incoming, it.text) },
    )

    private fun io.github.thelony97.orbit.core.model.v1.Settings.toV2() = Settings(
        onboardingDone = onboardingDone,
        digestHour = digestHour,
        digestMinute = digestMinute,
        minCallSeconds = minCallSeconds,
        sortByLastName = sortByLastName,
        lockEnabled = lockEnabled,
        autoBackup = autoBackup,
        backupTreeUri = backupTreeUri,
        lastBackupAt = lastBackupAt,
        suggestUnknown = suggestUnknown,
        noteAfterCall = noteAfterCall,
        topicsDuringCall = topicsDuringCall,
        callSyncedUntil = callSyncedUntil,
        smsSyncedUntil = smsSyncedUntil,
        autoUpdateCheck = autoUpdateCheck,
        lastUpdateCheck = lastUpdateCheck,
        lastDigestDay = lastDigestDay,
        // "due" sorting no longer exists
        listSort = if (listSort == "due") "name" else listSort,
        listSortReversed = listSortReversed,
    )

    internal fun normalizeUrl(raw: String): String =
        if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
}
