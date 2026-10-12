package io.github.thelony97.orbit.data

import java.util.concurrent.TimeUnit

/** Pure transformations on [OrbitData]; used with [OrbitRepository.update]. */
object Mutations {

    private val TRASH_KEEP = TimeUnit.DAYS.toMillis(30)

    fun upsertContact(d: OrbitData, c: Contact): OrbitData {
        val stamped = c.copy(updatedAt = now(), createdAt = if (c.createdAt == 0L) now() else c.createdAt)
        val exists = d.contacts.any { it.id == c.id }
        return d.copy(contacts = if (exists) d.contacts.map { if (it.id == c.id) stamped else it } else d.contacts + stamped)
    }

    fun trash(d: OrbitData, id: String): OrbitData =
        d.copy(contacts = d.contacts.map { if (it.id == id) it.copy(deletedAt = now()) else it })

    fun restore(d: OrbitData, id: String): OrbitData =
        d.copy(contacts = d.contacts.map { if (it.id == id) it.copy(deletedAt = null) else it })

    /** Removes a contact for good, together with relations and meetings that point at it. */
    fun purge(d: OrbitData, id: String): OrbitData = d.copy(
        contacts = d.contacts.filterNot { it.id == id },
        relations = d.relations.filterNot { it.fromId == id || it.toId == id },
        meetings = d.meetings.filterNot { it.contactId == id },
    )

    /** Returns photo file names that are no longer referenced after purging old trash. */
    fun purgeExpiredTrash(d: OrbitData): Pair<OrbitData, List<String>> {
        val limit = now() - TRASH_KEEP
        val expired = d.contacts.filter { it.deletedAt != null && it.deletedAt < limit }
        var result = d
        expired.forEach { result = purge(result, it.id) }
        return result to expired.mapNotNull { it.photo }
    }

    fun addEntry(d: OrbitData, contactId: String, entry: Entry): OrbitData =
        d.copy(contacts = d.contacts.map { c ->
            if (c.id != contactId) c
            else c.copy(entries = (c.entries + entry).sortedByDescending { it.at }, snoozedUntil = null)
        })

    fun updateEntry(d: OrbitData, contactId: String, entry: Entry): OrbitData =
        d.copy(contacts = d.contacts.map { c ->
            if (c.id != contactId) c
            else c.copy(entries = c.entries.map { if (it.id == entry.id) entry else it }.sortedByDescending { it.at })
        })

    fun deleteEntry(d: OrbitData, contactId: String, entryId: String): OrbitData =
        d.copy(contacts = d.contacts.map { c ->
            if (c.id != contactId) c else c.copy(entries = c.entries.filterNot { it.id == entryId })
        })

    fun addRelation(d: OrbitData, fromId: String, toId: String, kind: String): OrbitData {
        if (fromId == toId) return d
        val withoutPair = d.relations.filterNot {
            (it.fromId == fromId && it.toId == toId) || (it.fromId == toId && it.toId == fromId)
        }
        return d.copy(relations = withoutPair + Relation(newId(), fromId, toId, kind))
    }

    fun removeRelation(d: OrbitData, relationId: String): OrbitData =
        d.copy(relations = d.relations.filterNot { it.id == relationId })

    fun upsertGroup(d: OrbitData, g: Group): OrbitData {
        val exists = d.groups.any { it.id == g.id }
        return d.copy(groups = if (exists) d.groups.map { if (it.id == g.id) g else it } else d.groups + g)
    }

    fun deleteGroup(d: OrbitData, id: String): OrbitData = d.copy(
        groups = d.groups.filterNot { it.id == id },
        contacts = d.contacts.map { c -> if (id in c.groupIds) c.copy(groupIds = c.groupIds - id) else c },
    )

    fun upsertType(d: OrbitData, t: EntryType): OrbitData {
        val exists = d.entryTypes.any { it.id == t.id }
        return d.copy(entryTypes = if (exists) d.entryTypes.map { if (it.id == t.id) t else it } else d.entryTypes + t)
    }

    /** Custom types can be deleted; their entries fall back to "Treffen". */
    fun deleteType(d: OrbitData, id: String): OrbitData {
        val type = d.entryTypes.firstOrNull { it.id == id } ?: return d
        if (type.builtin) return d
        return d.copy(
            entryTypes = d.entryTypes.filterNot { it.id == id },
            contacts = d.contacts.map { c ->
                if (c.entries.none { it.typeId == id }) c
                else c.copy(entries = c.entries.map { e -> if (e.typeId == id) e.copy(typeId = TypeIds.MEET) else e })
            },
        )
    }

    fun upsertMeeting(d: OrbitData, m: Meeting): OrbitData {
        val exists = d.meetings.any { it.id == m.id }
        return d.copy(meetings = if (exists) d.meetings.map { if (it.id == m.id) m else it } else d.meetings + m)
    }

    /** "Ja, hat stattgefunden" → becomes a Treffen entry. */
    fun confirmMeeting(d: OrbitData, meetingId: String, happened: Boolean): OrbitData {
        val m = d.meetings.firstOrNull { it.id == meetingId } ?: return d
        if (m.resolved) return d
        var result = d.copy(meetings = d.meetings.map { if (it.id == meetingId) it.copy(resolved = true, asked = true) else it })
        if (happened) {
            result = addEntry(
                result, m.contactId,
                Entry(
                    id = newId(), typeId = TypeIds.MEET, at = m.start,
                    note = m.place.takeIf { it.isNotBlank() }?.let { "Treffen: $it" } ?: "",
                    source = EntrySource.MEETING, externalId = "meeting:${m.id}",
                ),
            )
        }
        return result
    }

    fun dismissUnknown(d: OrbitData, key: String): OrbitData =
        d.copy(unknownNumbers = d.unknownNumbers.map { if (it.key == key) it.copy(dismissed = true) else it })

    fun removeUnknown(d: OrbitData, key: String): OrbitData =
        d.copy(unknownNumbers = d.unknownNumbers.filterNot { it.key == key })
}
