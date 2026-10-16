package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.OrbitDoc
import java.security.MessageDigest
import java.time.LocalDate

/**
 * Orbit-Kalender (PLAN 9 A13): every occasion of a visible contact as an all-day event in Orbit's
 * own calendar on the phone, yearly ones repeating, and the contracts' cancellation deadlines. Pure: which events to insert, rewrite, delete.
 */
object CalendarSync {

    data class Item(val key: String, val title: String, val day: LocalDate, val rrule: String?, val description: String) {
        val hash: String by lazy {
            MessageDigest.getInstance("SHA-256").digest(toString().toByteArray()).take(8).joinToString("") { "%02x".format(it) }
        }
    }

    data class Existing(val id: Long, val key: String?, val hash: String?)

    data class Plan(val insert: List<Item>, val update: List<Pair<Long, Item>>, val delete: List<Long>) {
        val isEmpty: Boolean get() = insert.isEmpty() && update.isEmpty() && delete.isEmpty()
    }

    /**
     * The events: never the archive or the trash. A yearly occasion starts in the current year
     * (this year's past ones stay visible), a one-off one on its own day.
     */
    fun items(doc: OrbitDoc, today: LocalDate, nameOf: (String) -> String): List<Item> {
        val yearStart = today.withDayOfYear(1)
        val visible = doc.contacts.filter { it.deletedAt == null && !it.archived }
        val ids = visible.map { it.id }.toSet()
        return visible.flatMap { c -> deadlines(c, today, nameOf) } + visible.flatMap { c ->
            c.dates.mapNotNull { o ->
                // A shared occasion whose partner is gone still belongs to its owner alone.
                val partner = o.sharedWith
                val occasion = if (partner != null && partner !in ids) o.copy(sharedWith = null) else o
                OccasionCalendar.event(occasion, c.id, yearStart, nameOf)?.let { e ->
                    Item("${c.id}/${o.id}", e.title, e.day, e.rrule, e.description)
                }
            }
        }
    }

    /**
     * Fristen (PLAN 9 A13/D3): the next „Kündigen bis …“ of every running contract as a one-off
     * event on its day; cancelled or ended contracts have none. A new term moves the event along.
     */
    private fun deadlines(c: io.github.thelony97.orbit.core.model.Contact, today: LocalDate, nameOf: (String) -> String): List<Item> =
        c.contracts.mapNotNull { k ->
            val t = Contracts.term(k, today) ?: return@mapNotNull null
            val day = t.deadline ?: return@mapNotNull null
            if (t.cancelled || t.over) return@mapNotNull null
            val details = listOf(k.number.takeIf { it.isNotBlank() }?.let { "Nr. $it" }, "Laufzeit bis ${t.end.dayOfMonth}.${t.end.monthValue}.${t.end.year}")
            Item(
                "${c.id}/contract/${k.id}",
                // Who first (a week view shows ~20 characters); the event's day is the deadline.
                nameOf(c.id) + " · " + Contracts.title(k) + " kündigen",
                day, null, details.filterNotNull().joinToString(" · "),
            )
        }

    fun plan(items: List<Item>, existing: List<Existing>): Plan {
        val byKey = HashMap<String, Existing>()
        val delete = ArrayList<Long>()
        for (e in existing) {
            val k = e.key
            if (k == null || byKey.containsKey(k)) delete += e.id else byKey[k] = e
        }
        val insert = ArrayList<Item>()
        val update = ArrayList<Pair<Long, Item>>()
        val wanted = HashSet<String>()
        for (item in items) {
            if (!wanted.add(item.key)) continue
            val e = byKey[item.key]
            when {
                e == null -> insert += item
                e.hash != item.hash -> update += e.id to item
            }
        }
        byKey.values.filter { it.key !in wanted }.forEach { delete += it.id }
        return Plan(insert, update, delete)
    }
}
