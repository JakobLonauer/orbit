package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.CalendarDay
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.LabeledValue
import io.github.thelony97.orbit.core.model.OccasionKind
import io.github.thelony97.orbit.core.model.OrbitDoc

/**
 * Doppelstern (PLAN 9 E5): two contacts that are one person by fixed rules – the same number, the
 * same mail address, or the same name with the same birthday. Merging keeps everything: lists
 * are joined, one value per conflicting field is chosen, history, photos and every reference move
 * to the one that stays; the other goes to the trash (recoverable).
 */
object Twins {

    enum class Why { NUMBER, MAIL, NAME_BIRTHDAY }

    data class Twin(val a: String, val b: String, val why: Why)

    private const val TAIL = 9

    fun pairs(contacts: List<Contact>, normalize: (String) -> String?, dismissed: Set<String> = emptySet()): List<Twin> {
        val live = contacts.filter { it.deletedAt == null && !it.isMe && !it.archived }
        val out = LinkedHashMap<String, Twin>()
        fun add(x: String, y: String, why: Why) {
            if (x == y) return
            val (a, b) = if (x < y) x to y else y to x
            val k = key(a, b)
            if (k !in dismissed && k !in out) out[k] = Twin(a, b, why)
        }
        group(live) { phoneKeys(it, normalize) }.forEach { ids -> ids.zipWithNext().forEach { (x, y) -> add(x, y, Why.NUMBER) } }
        group(live) { mailKeys(it) }.forEach { ids -> ids.zipWithNext().forEach { (x, y) -> add(x, y, Why.MAIL) } }
        group(live.filter { !it.isOrg }) { nameKeys(it) }.forEach { ids -> ids.zipWithNext().forEach { (x, y) -> add(x, y, Why.NAME_BIRTHDAY) } }
        // A person and an organisation are never the same.
        val byId = live.associateBy { it.id }
        return out.values.filter { p -> byId[p.a]?.isOrg == byId[p.b]?.isOrg }
    }

    private fun phoneKeys(c: Contact, normalize: (String) -> String?) =
        c.phones.mapNotNull { p -> normalize(p.value)?.filter(Char::isDigit)?.takeIf { it.length >= TAIL }?.takeLast(TAIL) }

    private fun mailKeys(c: Contact) = c.emails.map { it.value.trim().lowercase() }.filter { '@' in it }

    private fun nameKeys(c: Contact): List<String> {
        val name = Fold.fold(listOf(c.firstName, c.lastName).joinToString(" ").trim())
        val b = c.birthday ?: return emptyList()
        return if (name.isBlank()) emptyList() else listOf("$name|${b.day}.${b.month}.${b.year ?: 0}")
    }

    fun key(a: String, b: String): String = if (a < b) "twin:$a:$b" else "twin:$b:$a"

    private fun group(contacts: List<Contact>, keys: (Contact) -> List<String>): List<List<String>> {
        val m = HashMap<String, LinkedHashSet<String>>()
        contacts.forEach { c -> keys(c).distinct().forEach { k -> m.getOrPut(k) { LinkedHashSet() } += c.id } }
        return m.values.filter { it.size > 1 }.map { it.toList() }
    }

    /** A field where both have a different value – Jakob picks one. */
    data class Conflict(val field: String, val label: String, val keep: String, val gone: String)

    private val SCALARS: List<Triple<String, String, (Contact) -> String>> = listOf(
        Triple("firstName", "Vorname", { it.firstName }),
        Triple("lastName", "Nachname", { it.lastName }),
        Triple("nickname", "Rufname", { it.nickname }),
        Triple("title", "Titel", { it.title }),
        Triple("company", "Firma", { it.company }),
        Triple("position", "Position", { it.position }),
        Triple("howWeMet", "Kennengelernt", { it.howWeMet }),
        Triple("notes", "Notizen", { it.notes }),
        Triple("pinnedNote", "Angeheftet", { it.pinnedNote }),
        Triple("photo", "Foto", { it.photo.orEmpty() }),
        Triple("birthday", "Geburtstag", { c -> c.birthday?.let { "${it.day}.${it.month}." + (it.year?.toString() ?: "") }.orEmpty() }),
    )

    fun conflicts(keep: Contact, gone: Contact): List<Conflict> = SCALARS.mapNotNull { (field, label, get) ->
        val a = get(keep).trim()
        val b = get(gone).trim()
        if (a.isNotEmpty() && b.isNotEmpty() && a != b) Conflict(field, label, a, b) else null
    }

    /** „142 + 37 → 179 Einträge“. */
    fun countLine(keep: Contact, gone: Contact): String {
        val n = (keep.entries.map { it.id } + gone.entries.map { it.id }).distinct().size
        return "${keep.entries.size} + ${gone.entries.size} → $n Einträge"
    }

    private fun <T> join(a: List<T>, b: List<T>, key: (T) -> Any): List<T> = (a + b).distinctBy(key)

    private fun values(a: List<LabeledValue>, b: List<LabeledValue>) = join(a, b) { Fold.fold(it.value).filter { ch -> !ch.isWhitespace() } }

    private fun pick(field: String, a: String, b: String, fromGone: Set<String>) = when {
        a.isBlank() -> b
        b.isBlank() -> a
        field in fromGone -> b
        else -> a
    }

    /** The merged contact: [keep] with what [gone] adds; [fromGone] = conflicting fields taken from [gone]. */
    /** One birthday after the merge: the chosen one; every other occasion of both. */
    private fun dates(keep: Contact, gone: Contact, fromGone: Set<String>): List<io.github.thelony97.orbit.core.model.Occasion> {
        val birthdayFromGone = "birthday" in fromGone || keep.birthday == null
        val keepDates = if (birthdayFromGone && gone.birthday != null) keep.dates.filter { it.kind != OccasionKind.BIRTHDAY } else keep.dates
        val goneDates = if (!birthdayFromGone && keep.birthday != null) gone.dates.filter { it.kind != OccasionKind.BIRTHDAY } else gone.dates
        return join(keepDates, goneDates) { it.kind to it.date.let { d: CalendarDay -> "${d.day}.${d.month}" } }
    }

    private fun photo(keep: Contact, gone: Contact, fromGone: Set<String>) =
        if ("photo" in fromGone || keep.photo == null) gone.photo ?: keep.photo else keep.photo

    fun merged(keep: Contact, gone: Contact, fromGone: Set<String>, now: Long): Contact {
        return keep.copy(
            firstName = pick("firstName", keep.firstName, gone.firstName, fromGone),
            lastName = pick("lastName", keep.lastName, gone.lastName, fromGone),
            nickname = pick("nickname", keep.nickname, gone.nickname, fromGone),
            title = pick("title", keep.title, gone.title, fromGone),
            salutation = keep.salutation.ifBlank { gone.salutation },
            company = pick("company", keep.company, gone.company, fromGone),
            position = pick("position", keep.position, gone.position, fromGone),
            howWeMet = pick("howWeMet", keep.howWeMet, gone.howWeMet, fromGone),
            notes = pick("notes", keep.notes, gone.notes, fromGone),
            pinnedNote = pick("pinnedNote", keep.pinnedNote, gone.pinnedNote, fromGone),
            photo = photo(keep, gone, fromGone),
            phones = values(keep.phones, gone.phones),
            emails = values(keep.emails, gone.emails),
            addresses = values(keep.addresses, gone.addresses),
            groupIds = (keep.groupIds + gone.groupIds).distinct(),
            favorite = keep.favorite || gone.favorite,
            archived = keep.archived && gone.archived,
            topics = join(keep.topics, gone.topics) { Fold.fold(it.text) },
            gifts = join(keep.gifts, gone.gifts) { Fold.fold(it.text) },
            dates = dates(keep, gone, fromGone),
            links = join(keep.links, gone.links) { it.url },
            fields = join(keep.fields, gone.fields) { Fold.fold(it.label) + "=" + it.value },
            members = join(keep.members, gone.members) { it.id },
            contracts = join(keep.contracts, gone.contracts) { it.id },
            loans = join(keep.loans, gone.loans) { it.id },
            wifi = join(keep.wifi, gone.wifi) { it.ssid },
            macros = join(keep.macros, gone.macros) { it.id },
            geo = keep.geo ?: gone.geo,
            greeting = keep.greeting ?: gone.greeting,
            orgCategory = keep.orgCategory ?: gone.orgCategory,
            trade = keep.trade.ifBlank { gone.trade },
            ringtone = keep.ringtone ?: gone.ringtone,
            simId = keep.simId ?: gone.simId,
            hideNumber = keep.hideNumber || gone.hideNumber,
            poster = keep.poster ?: gone.poster,
            numberPrefixes = (keep.numberPrefixes + gone.numberPrefixes).distinct(),
            emailDomains = (keep.emailDomains + gone.emailDomains).distinct(),
            mapsUrl = keep.mapsUrl ?: gone.mapsUrl,
            openingHours = keep.openingHours.ifBlank { gone.openingHours },
            phoneHours = keep.phoneHours.ifBlank { gone.phoneHours },
            vacationUntil = keep.vacationUntil ?: gone.vacationUntil,
            vacationSubstitute = (keep.vacationSubstitute ?: gone.vacationSubstitute)?.takeIf { it != keep.id && it != gone.id },
            dead = (keep.dead + gone.dead).distinct(),
            giftBudgetCents = keep.giftBudgetCents ?: gone.giftBudgetCents,
            createdAt = listOf(keep.createdAt, gone.createdAt).filter { it > 0 }.minOrNull() ?: keep.createdAt,
            updatedAt = now,
            entries = join(keep.entries, gone.entries.map { it.copy(contactId = keep.id) }) { it.id }.sortedByDescending { it.at },
            gallery = join(keep.gallery, gone.gallery.map { it.copy(contactId = keep.id) }) { it.id },
        )
    }

    /** The whole document after the merge: every reference to [goneId] points to [keepId]; [goneId] goes to the trash, empty. */
    fun apply(doc: OrbitDoc, keepId: String, goneId: String, fromGone: Set<String>, now: Long): OrbitDoc {
        val keep = doc.contacts.firstOrNull { it.id == keepId } ?: return doc
        val gone = doc.contacts.firstOrNull { it.id == goneId } ?: return doc
        val m = merged(keep, gone, fromGone, now)
        fun re(id: String) = if (id == goneId) keepId else id
        return doc.copy(
            contacts = doc.contacts.map { c ->
                when (c.id) {
                    keepId -> m
                    goneId -> c.copy(deletedAt = now, entries = emptyList(), gallery = emptyList(), updatedAt = now)
                    else -> c.copy(
                        dates = c.dates.map { o -> if (o.sharedWith == goneId) o.copy(sharedWith = keepId) else o },
                        vacationSubstitute = c.vacationSubstitute?.let(::re),
                        entries = if (c.entries.any { goneId in it.withIds }) c.entries.map { e -> e.copy(withIds = e.withIds.map(::re).distinct()) } else c.entries,
                    )
                }
            },
            relations = doc.relations.map { r -> r.copy(fromId = re(r.fromId), toId = re(r.toId)) }
                .filter { it.fromId != it.toId }.distinctBy { Triple(it.fromId, it.toId, it.kind) },
            orgLinks = doc.orgLinks.map { l -> l.copy(personId = re(l.personId), orgId = re(l.orgId)) }.distinctBy { Triple(it.personId, it.orgId, it.role) },
            meetings = doc.meetings.map { if (it.contactId == goneId) it.copy(contactId = keepId) else it },
        )
    }
}
