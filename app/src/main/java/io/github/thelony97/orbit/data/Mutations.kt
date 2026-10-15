package io.github.thelony97.orbit.data

import java.util.concurrent.TimeUnit

/** Pure transformations on [OrbitData]; used with [OrbitRepository.update]. */
object Mutations {

    private val TRASH_KEEP = TimeUnit.DAYS.toMillis(30)

    /**
     * Saves a contact from the edit screen. Verlauf and photo folder always come from the current
     * state: the screen started from an older copy and must not drop entries synced meanwhile.
     */
    fun upsertContact(d: OrbitData, c: Contact): OrbitData {
        val stamped = c.copy(updatedAt = now(), createdAt = if (c.createdAt == 0L) now() else c.createdAt)
        val exists = d.contacts.any { it.id == c.id }
        return d.copy(
            contacts = if (exists) {
                d.contacts.map { cur ->
                    if (cur.id != c.id) cur
                    else stamped.copy(
                        entries = cur.entries,
                        gallery = cur.gallery,
                        // Hidden members are edited on their own pages; a member added from a
                        // notification meanwhile must survive this save.
                        members = cur.members,
                        // A logo fetched while the page was open stays (same address only).
                        links = stamped.links.map { l -> if (l.logo == null) cur.links.firstOrNull { it.id == l.id && it.url == l.url }?.logo?.let { l.copy(logo = it) } ?: l else l },
                    )
                }
            } else {
                d.contacts + stamped
            },
        )
    }

    fun trash(d: OrbitData, id: String): OrbitData =
        d.copy(contacts = d.contacts.map { if (it.id == id && !it.isMe) it.copy(deletedAt = now()) else it })

    fun restore(d: OrbitData, id: String): OrbitData =
        d.copy(contacts = d.contacts.map { if (it.id == id) it.copy(deletedAt = null) else it })

    /**
     * „Gratuliert“ abhaken (PLAN 3.5): adds the Verlauf entry for this occasion and year, or takes
     * it away again on a second tap. Other entries are never touched.
     */
    fun toggleCongrats(d: OrbitData, contactId: String, occasionId: String, year: Int, at: Long = now()): OrbitData =
        d.copy(contacts = d.contacts.map { c ->
            if (c.id != contactId) c
            else {
                val mine = c.entries.filter { it.typeId == io.github.thelony97.orbit.core.model.BuiltinTypes.CONGRATS && it.occasionId == occasionId && it.occasionYear == year }
                if (mine.isNotEmpty()) c.copy(entries = c.entries - mine.toSet())
                else c.copy(entries = (c.entries + Entry(
                    id = newId(), contactId = c.id, typeId = io.github.thelony97.orbit.core.model.BuiltinTypes.CONGRATS, at = at,
                    occasionId = occasionId, occasionYear = year,
                )).sortedByDescending { it.at })
            }
        })

    /** A gift idea was given (PLAN 3.5 „Geschenkideen antippen = in diesem Jahr verschenkt“): tick it off. */
    fun giveIdea(d: OrbitData, contactId: String, ideaId: String): OrbitData =
        d.copy(contacts = d.contacts.map { c ->
            if (c.id != contactId) c else c.copy(gifts = c.gifts.map { if (it.id == ideaId) it.copy(done = true) else it })
        })

    /** „Gratuliert“ from the Gratulieren button: only ever adds, decided inside the transform. */
    fun markCongrats(d: OrbitData, contactId: String, occasionId: String, year: Int, at: Long = now()): OrbitData {
        val c = d.contacts.firstOrNull { it.id == contactId } ?: return d
        return if (io.github.thelony97.orbit.core.domain.BirthdayTab.congratulated(c, occasionId, year)) d else toggleCongrats(d, contactId, occasionId, year, at)
    }

    /**
     * „Geschenk“ (PLAN 3.5): one gift entry per occasion and year with what and who gave together;
     * saving again changes that entry, an empty text removes it.
     */
    fun setGift(d: OrbitData, contactId: String, occasionId: String, year: Int, what: String, withIds: List<String>, at: Long): OrbitData =
        d.copy(contacts = d.contacts.map { c ->
            if (c.id != contactId) c
            else {
                val gift = io.github.thelony97.orbit.core.model.BuiltinTypes.GIFT
                val old = c.entries.firstOrNull { it.typeId == gift && it.occasionId == occasionId && it.occasionYear == year }
                val text = what.trim()
                when {
                    text.isEmpty() && old != null -> c.copy(entries = c.entries - old)
                    text.isEmpty() -> c
                    // The date chip may move it; the entry keeps its id.
                    old != null -> c.copy(entries = c.entries.map { if (it.id == old.id) it.copy(note = text, withIds = withIds, at = at) else it }.sortedByDescending { it.at })
                    else -> c.copy(entries = (c.entries + Entry(
                        id = newId(), contactId = c.id, typeId = gift, at = at, note = text,
                        occasionId = occasionId, occasionYear = year, withIds = withIds,
                    )).sortedByDescending { it.at })
                }
            }
        })

    /**
     * Saves an occasion at its owner (PLAN 3.5: stored once, a shared one shows at both): replaces
     * the one with the same id or adds it. A contact keeps at most one birthday.
     */
    fun upsertOccasion(d: OrbitData, ownerId: String, o: Anniversary): OrbitData =
        d.copy(contacts = d.contacts.map { c ->
            if (c.id != ownerId) c
            else {
                val others = c.dates.filterNot { it.id == o.id || (o.kind == io.github.thelony97.orbit.core.model.OccasionKind.BIRTHDAY && it.kind == o.kind) }
                val at = c.dates.indexOfFirst { it.id == o.id }
                c.copy(dates = if (at >= 0) others.toMutableList().apply { add(at.coerceAtMost(size), o) } else others + o)
            }
        })

    /** „Zurückholen“ from the archive (also from the caller card while it rings). */
    fun unarchive(d: OrbitData, id: String): OrbitData =
        d.copy(contacts = d.contacts.map { if (it.id == id && it.archived) it.copy(archived = false, archivedAt = null) else it })

    /**
     * Files of [candidates] that nothing in [d] uses any more (PLAN 3.10: one picture can belong
     * to several contacts – a file goes only with its last reference).
     */
    fun unreferenced(d: OrbitData, candidates: Collection<String>): List<String> {
        val used = d.contacts.flatMapTo(HashSet()) { files(it) }
        return candidates.filter { it !in used }.distinct()
    }

    /** „Auch bei …“ (PLAN 3.10): the same picture in another contact's folder, stored once. */
    fun shareAttachment(d: OrbitData, a: GalleryPhoto, toId: String, at: Long = now()): OrbitData =
        d.copy(contacts = d.contacts.map { c ->
            if (c.id != toId || c.gallery.any { it.file == a.file }) c
            else c.copy(gallery = listOf(a.copy(id = newId(), contactId = toId, addedAt = at)) + c.gallery)
        })

    /** Removes a contact for good, together with relations, Zuordnungen and meetings that point at it. */
    fun purge(d: OrbitData, id: String): OrbitData = if (d.contacts.any { it.id == id && it.isMe }) d else d.copy(
        contacts = d.contacts.filterNot { it.id == id },
        relations = d.relations.filterNot { it.fromId == id || it.toId == id },
        orgLinks = d.orgLinks.filterNot { it.personId == id || it.orgId == id },
        meetings = d.meetings.filterNot { it.contactId == id },
    )

    /** Returns photo file names that are no longer referenced after purging old trash. */
    fun purgeExpiredTrash(d: OrbitData): Pair<OrbitData, List<String>> {
        val limit = now() - TRASH_KEEP
        val expired = d.contacts.filter { (it.deletedAt ?: Long.MAX_VALUE) < limit }
        var result = d
        expired.forEach { result = purge(result, it.id) }
        // Pictures shared with a contact that stays are kept.
        return result to unreferenced(result, expired.flatMap { files(it) })
    }

    /** All picture files that belong to a contact (profile photo and photo folder). */
    fun files(c: Contact): List<String> =
        listOfNotNull(c.photo, c.poster) + c.gallery.map { it.file } + c.links.mapNotNull { it.logo } + c.entries.mapNotNull { it.audio }

    fun addEntry(d: OrbitData, contactId: String, entry: Entry): OrbitData =
        d.copy(contacts = d.contacts.map { c ->
            if (c.id != contactId) c
            else c.copy(entries = (c.entries + entry.copy(contactId = contactId)).sortedByDescending { it.at })
        })

    fun updateEntry(d: OrbitData, contactId: String, entry: Entry): OrbitData =
        d.copy(contacts = d.contacts.map { c ->
            if (c.id != contactId) c
            else c.copy(entries = c.entries.map { if (it.id == entry.id) entry.copy(contactId = contactId) else it }.sortedByDescending { it.at })
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

    /**
     * Zuordnung person → organisation (PLAN 3.6). The same role twice is one; a person may hold
     * several roles at one organisation (Kolleg:in and Projektleitung).
     */
    fun addOrgLink(d: OrbitData, personId: String, orgId: String, role: String, label: String = ""): OrbitData {
        val person = d.contacts.firstOrNull { it.id == personId }
        val org = d.contacts.firstOrNull { it.id == orgId }
        if (person == null || org == null || person.isOrg || !org.isOrg) return d
        val custom = role == io.github.thelony97.orbit.core.model.AssignmentRoles.CUSTOM
        val text = if (custom) label.trim() else ""
        if (custom && text.isEmpty()) return d
        if (d.orgLinks.any { it.personId == personId && it.orgId == orgId && it.role == role && it.label == text }) return d
        return d.copy(orgLinks = d.orgLinks + io.github.thelony97.orbit.core.model.OrgLink(newId(), personId, orgId, role, text))
    }

    /** A fetched site logo for a link tile (PLAN 3.7); the link may have gone meanwhile. */
    fun setLinkLogo(d: OrbitData, ownerId: String, linkId: String, file: String): OrbitData =
        d.copy(contacts = d.contacts.map { c ->
            if (c.id != ownerId || c.links.none { it.id == linkId }) c
            else c.copy(links = c.links.map { if (it.id == linkId) it.copy(logo = file) else it })
        })

    fun removeOrgLink(d: OrbitData, linkId: String): OrbitData =
        d.copy(orgLinks = d.orgLinks.filterNot { it.id == linkId })

    /** Versteckter Mitarbeiter (PLAN 3.6): added or changed inside its organisation only. */
    fun upsertMember(d: OrbitData, orgId: String, m: io.github.thelony97.orbit.core.model.Member): OrbitData =
        d.copy(contacts = d.contacts.map { c ->
            if (c.id != orgId || !c.isOrg) c
            else if (c.members.any { it.id == m.id }) c.copy(members = c.members.map { if (it.id == m.id) m else it })
            else c.copy(members = c.members + m.copy(createdAt = if (m.createdAt == 0L) now() else m.createdAt))
        })

    /**
     * „Mitarbeiter zuordnen?“ (PLAN 3.6) after a call from an unknown Durchwahl in the
     * organisation's number range: the typed „Name, Rolle“ becomes a member with that number, and
     * the call is theirs. A member who already has the number just gets the call.
     */
    fun memberFromCall(d: OrbitData, orgId: String, entryId: String, text: String, number: String, newMemberId: String = newId()): OrbitData {
        val org = d.contacts.firstOrNull { it.id == orgId && it.isOrg } ?: return d
        if (org.entries.none { it.id == entryId }) return d
        val digits = number.filter { it.isDigit() }
        val existing = org.members.firstOrNull { m -> digits.length >= 3 && m.phones.any { p -> p.value.filter { it.isDigit() } == digits } }
        val withMember = if (existing != null) d else {
            val (name, role) = io.github.thelony97.orbit.core.domain.Members.parse(text) ?: return d
            upsertMember(d, orgId, io.github.thelony97.orbit.core.model.Member(newMemberId, name, role = role, phones = listOf(LabeledValue("Durchwahl", number.trim()))))
        }
        val memberId = existing?.id ?: newMemberId
        return withMember.copy(contacts = withMember.contacts.map { c ->
            if (c.id != orgId) c else c.copy(entries = c.entries.map { if (it.id == entryId) it.copy(memberId = memberId) else it })
        })
    }

    /**
     * „Zu eigenem Kontakt machen“ (PLAN 3.6): the hidden member becomes a person – new, or merged
     * into [intoId]. Conversations and note move along (the entries keep their ids), and the
     * Zuordnung to the organisation is created with the member's role. Returns the person's id.
     */
    fun memberToContact(d: OrbitData, orgId: String, memberId: String, intoId: String? = null, newContactId: String = newId(), at: Long = now()): Pair<OrbitData, String?> {
        val org = d.contacts.firstOrNull { it.id == orgId && it.isOrg } ?: return d to null
        val m = org.members.firstOrNull { it.id == memberId } ?: return d to null
        val target = intoId?.let { id -> d.contacts.firstOrNull { it.id == id && !it.isOrg && it.deletedAt == null } }
        if (intoId != null && target == null) return d to null
        val personId = target?.id ?: newContactId
        val talks = org.entries.filter { it.memberId == m.id }
        // A call the person already has (synced from the same number) is one call: its note goes
        // onto the person's entry instead of a second row the database would refuse.
        val targetByExt = target?.entries.orEmpty().filter { it.externalId != null }.associateBy { it.externalId }
        val duplicates = talks.filter { it.externalId != null && targetByExt.containsKey(it.externalId) }
        val moved = (talks - duplicates.toSet()).map { it.copy(contactId = personId, memberId = null) }
        val movedIds = talks.mapTo(HashSet()) { it.id }
        val notesFor = duplicates.filter { it.note.isNotBlank() }.associate { targetByExt.getValue(it.externalId)!!.id to it.note }
        fun merge(old: List<LabeledValue>, add: List<LabeledValue>): List<LabeledValue> {
            val have = old.map { v -> v.value.filter { it.isLetterOrDigit() }.lowercase() }.toSet()
            return old + add.filter { v -> v.value.isNotBlank() && v.value.filter { it.isLetterOrDigit() }.lowercase() !in have }
        }
        val person = if (target != null) {
            target.copy(
                phones = merge(target.phones, m.phones),
                emails = merge(target.emails, m.emails),
                notes = listOf(target.notes, m.note).filter { it.isNotBlank() }.joinToString("\n"),
                photo = target.photo ?: m.photo,
                entries = (target.entries.map { e ->
                    notesFor[e.id]?.let { n -> if (n in e.note) e else e.copy(note = listOf(e.note, n).filter { it.isNotBlank() }.joinToString("\n")) } ?: e
                } + moved).sortedByDescending { it.at },
                updatedAt = at,
            )
        } else {
            val (first, last) = splitMemberName(m.name)
            Contact(
                id = personId, firstName = first, lastName = last, phones = m.phones, emails = m.emails,
                notes = m.note, photo = m.photo, entries = moved.sortedByDescending { it.at }, createdAt = at, updatedAt = at,
            )
        }
        val role = m.role.trim()
        val link = io.github.thelony97.orbit.core.model.OrgLink(
            newId(), personId, orgId,
            role = if (role.isEmpty()) "employer" else io.github.thelony97.orbit.core.model.AssignmentRoles.CUSTOM,
            label = role,
        )
        val contacts = d.contacts.map { c ->
            when (c.id) {
                orgId -> c.copy(members = c.members.filterNot { it.id == m.id }, entries = c.entries.filterNot { it.id in movedIds })
                personId -> person
                else -> c
            }
        } + if (target == null) listOf(person) else emptyList()
        val links = if (d.orgLinks.any { it.personId == personId && it.orgId == orgId }) d.orgLinks else d.orgLinks + link
        return d.copy(contacts = contacts, orgLinks = links) to personId
    }

    /** „Herr Mayer“ → („“, „Mayer“), „Anna Maria Huber“ → („Anna Maria“, „Huber“). */
    fun splitMemberName(name: String): Pair<String, String> {
        val titles = setOf("herr", "frau", "hr", "fr", "dr", "mag", "di", "ing", "prof")
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val words = parts.filterNot { it.lowercase().trimEnd('.') in titles }
        return when {
            words.isEmpty() -> "" to name.trim()
            words.size == 1 && parts.size > words.size -> "" to words.single()
            words.size == 1 -> words.single() to ""
            else -> words.dropLast(1).joinToString(" ") to words.last()
        }
    }

    /**
     * Removes a member; their conversations stay with the organisation (without the name), so
     * nothing written is lost.
     */
    fun removeMember(d: OrbitData, orgId: String, memberId: String): OrbitData =
        d.copy(contacts = d.contacts.map { c ->
            if (c.id != orgId) c
            else c.copy(
                members = c.members.filterNot { it.id == memberId },
                entries = if (c.entries.none { it.memberId == memberId }) c.entries else c.entries.map { if (it.memberId == memberId) it.copy(memberId = null) else it },
            )
        })

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
                    id = newId(), contactId = m.contactId, typeId = TypeIds.MEET, at = m.start,
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
