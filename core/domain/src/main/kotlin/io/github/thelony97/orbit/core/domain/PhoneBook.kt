package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.Group
import java.security.MessageDigest

/**
 * Orbit im Telefonbuch des Handys (PLAN 9 A1): what Orbit writes for one contact into its own
 * account of the phone's address book, and which rows to insert, rewrite or delete. Pure – the
 * app turns [Card]s into ContactsContract rows. Orbit is the truth: rows only ever flow out.
 */
object PhoneBook {

    /** ContactsContract.CommonDataKinds.Phone.TYPE_* (same numbers; kept here to stay pure). */
    object PhoneType {
        const val CUSTOM = 0
        const val HOME = 1
        const val MOBILE = 2
        const val WORK = 3
        const val FAX_WORK = 4
        const val OTHER = 7
        const val COMPANY_MAIN = 10
        const val MAIN = 12
    }

    /** ContactsContract.CommonDataKinds.Email.TYPE_*. */
    object MailType {
        const val CUSTOM = 0
        const val HOME = 1
        const val WORK = 2
        const val OTHER = 3
    }

    data class Number(val value: String, val type: Int, val label: String?, val primary: Boolean)
    data class Mail(val value: String, val type: Int, val label: String?)
    data class Field(val label: String, val value: String)

    /** PLAN 9 A3/B4: „Makro wählen“ in an organisation's contact card. */
    data class MacroRow(val id: String, val name: String)

    data class Card(
        val sourceId: String,
        val isOrg: Boolean,
        val given: String,
        val family: String,
        val prefix: String,
        /** For organisations the name the row shows (people get theirs from the parts). */
        val display: String,
        val nickname: String,
        val company: String,
        val jobTitle: String,
        val numbers: List<Number>,
        val mails: List<Mail>,
        val addresses: List<String>,
        /** "1990-05-17" or "--05-17" (year unknown) – the form Android's Event rows use. */
        val birthday: String?,
        /** File name of the profile picture in Orbit's photo folder. */
        val photo: String?,
        val starred: Boolean,
        val ringtone: String?,
        val website: String?,
        /** Orbit rows in the system contact card (PLAN 9 A3): own fields worth having at hand. */
        val fields: List<Field>,
        /** PLAN 9 A3: an organisation's macros, one „Makro wählen“ row each. */
        val macros: List<MacroRow> = emptyList(),
    ) {
        /** Changes whenever anything Orbit writes changes; stored with the row (SYNC1). */
        val hash: String by lazy {
            MessageDigest.getInstance("SHA-256").digest(toString().toByteArray())
                .take(10).joinToString("") { "%02x".format(it) }
        }
    }

    /** Who goes into the phone book: everyone with a name, never the archive, the trash or „Ich“. */
    fun exported(c: Contact, withOrgs: Boolean): Boolean =
        c.deletedAt == null && !c.archived && !c.isMe && (withOrgs || !c.isOrg) &&
            (c.firstName.isNotBlank() || c.lastName.isNotBlank() || c.company.isNotBlank())

    fun card(c: Contact, groups: List<Group>): Card {
        val byId = groups.associateBy { it.id }
        val ringtone = c.ringtone?.takeIf { it.isNotBlank() }
            ?: c.groupIds.firstNotNullOfOrNull { byId[it]?.ringtone?.takeIf { r -> r.isNotBlank() } }
        val numbers = c.phones.withIndex()
            .filter { it.value.value.any(Char::isDigit) }
            .map { (i, p) ->
                val (type, label) = phoneType(p.label, c.isOrg)
                Number(p.value.trim(), type, label, primary = i == c.defaultPhone && c.phones.size > 1)
            }
        val mails = c.emails.filter { '@' in it.value }.map { m ->
            val (type, label) = mailType(m.label)
            Mail(m.value.trim(), type, label)
        }
        val birthday = c.birthday?.let { b ->
            val y = b.year
            if (y != null) "%04d-%02d-%02d".format(y, b.month, b.day) else "--%02d-%02d".format(b.month, b.day)
        }
        return Card(
            sourceId = c.id,
            isOrg = c.isOrg,
            given = if (c.isOrg) "" else c.firstName.trim(),
            family = if (c.isOrg) "" else c.lastName.trim(),
            prefix = if (c.isOrg) "" else c.title.trim(),
            display = if (c.isOrg) c.company.trim() else "",
            nickname = if (c.isOrg) "" else c.nickname.trim(),
            company = c.company.trim(),
            jobTitle = c.position.trim(),
            numbers = numbers,
            mails = mails,
            addresses = c.addresses.map { it.value.trim() }.filter { it.isNotEmpty() },
            birthday = if (c.isOrg) null else birthday,
            photo = c.photo,
            starred = c.favorite,
            ringtone = ringtone,
            website = c.links.firstOrNull()?.url?.takeIf { it.isNotBlank() },
            fields = c.fields.filter { it.value.isNotBlank() }.take(4).map { Field(it.label.ifBlank { "Feld" }, it.value.trim()) },
            macros = if (c.isOrg) c.macros.filter { it.steps.isNotEmpty() }.take(3).map { MacroRow(it.id, it.name.ifBlank { "Makro" }) } else emptyList(),
        )
    }

    /** Orbit's label → the phone book's type; anything unknown stays as its own label. */
    fun phoneType(label: String, isOrg: Boolean): Pair<Int, String?> = when (Fold.fold(label.trim())) {
        "mobil", "handy", "mobile" -> PhoneType.MOBILE to null
        "privat", "zuhause", "home" -> PhoneType.HOME to null
        "arbeit", "buro", "work", "dienstlich" -> PhoneType.WORK to null
        "zentrale" -> PhoneType.COMPANY_MAIN to null
        "fax" -> PhoneType.FAX_WORK to null
        "", "sonstige", "andere" -> (if (isOrg) PhoneType.MAIN else PhoneType.OTHER) to null
        else -> PhoneType.CUSTOM to label.trim()
    }

    fun mailType(label: String): Pair<Int, String?> = when (Fold.fold(label.trim())) {
        "privat", "home" -> MailType.HOME to null
        "arbeit", "work", "buro" -> MailType.WORK to null
        "", "sonstige", "andere" -> MailType.OTHER to null
        else -> MailType.CUSTOM to label.trim()
    }

    /** One of Orbit's rows already in the phone book: its id there and what was written. */
    data class Existing(val rawId: Long, val sourceId: String?, val hash: String?)

    data class Plan(val insert: List<Card>, val rewrite: List<Pair<Long, Card>>, val delete: List<Long>) {
        val isEmpty: Boolean get() = insert.isEmpty() && rewrite.isEmpty() && delete.isEmpty()
    }

    /**
     * New cards are inserted, changed ones rewritten, rows without a card deleted – also
     * duplicates and rows a contacts app marked deleted (they come back: Orbit is the truth).
     */
    fun plan(cards: List<Card>, existing: List<Existing>): Plan {
        val bySource = HashMap<String, Existing>()
        val delete = ArrayList<Long>()
        for (e in existing) {
            val s = e.sourceId
            if (s == null || bySource.containsKey(s)) delete += e.rawId else bySource[s] = e
        }
        val insert = ArrayList<Card>()
        val rewrite = ArrayList<Pair<Long, Card>>()
        val wanted = HashSet<String>()
        for (card in cards) {
            if (!wanted.add(card.sourceId)) continue
            val e = bySource[card.sourceId]
            when {
                e == null -> insert += card
                e.hash != card.hash -> rewrite += e.rawId to card
            }
        }
        bySource.values.filter { it.sourceId !in wanted }.forEach { delete += it.rawId }
        return Plan(insert, rewrite, delete.distinct())
    }
}
