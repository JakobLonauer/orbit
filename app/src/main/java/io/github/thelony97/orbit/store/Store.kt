package io.github.thelony97.orbit.store

import io.github.thelony97.orbit.core.domain.DocJson
import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.OrbitDoc
import java.io.File

/**
 * Storage v2 (ADR 0003): the JSON core in [file], Verlauf and Ablage in [db].
 *
 * In memory each [Contact] carries its `entries` and `gallery` (transient fields), so the rest
 * of the app keeps working with whole contacts. [save] writes the JSON core and only the
 * difference of history and attachments since the last save – no full rewrite.
 */
class Store(val file: File, private val db: HistoryDb, private val newId: () -> String) {

    /** Runs the one-time 1.0.7 upgrade if needed, then loads and hydrates. `null` = nothing stored yet. */
    fun load(): OrbitDoc? {
        StoreUpgrade.run(file, db, newId)
        if (!file.exists()) return null
        return hydrate(DocJson.decode(file.readText()))
    }

    fun hydrate(doc: OrbitDoc): OrbitDoc {
        val hydrated = assemble(doc, db.all(), db.allAttachments())
        // A corrupt database was moved aside: an empty Verlauf would look like data loss.
        check(!HistoryDb.corrupted) { "Verlauf-Datenbank beschädigt (Kopie aufbewahrt)" }
        return hydrated
    }

    /** Writes [next]; [previous] is what the last save wrote (null on a fresh install). */
    fun save(previous: OrbitDoc?, next: OrbitDoc) {
        db.apply(changes(previous, next))
        StoreUpgrade.atomicWrite(file, DocJson.encode(next))
    }

    companion object {
        /** Puts rows back into their contacts, newest first. Rows of unknown contacts are dropped. */
        fun assemble(
            doc: OrbitDoc,
            history: List<io.github.thelony97.orbit.core.model.HistoryEntry>,
            attachments: List<io.github.thelony97.orbit.core.model.Attachment>,
        ): OrbitDoc {
            val h = history.groupBy { it.contactId }
            val a = attachments.groupBy { it.contactId }
            return doc.copy(contacts = doc.contacts.map { c ->
                c.copy(
                    entries = h[c.id].orEmpty().sortedByDescending { it.at },
                    gallery = a[c.id].orEmpty().sortedByDescending { it.addedAt },
                )
            })
        }

        /** Every row with its owner set, for backups. */
        fun historyOf(doc: OrbitDoc) = doc.contacts.flatMap { c -> c.entries.map { it.copy(contactId = c.id) } }
        fun attachmentsOf(doc: OrbitDoc) = doc.contacts.flatMap { c -> c.gallery.map { it.copy(contactId = c.id) } }

        /** What changed in history and attachments between two in-memory states. Pure. */
        fun changes(previous: OrbitDoc?, next: OrbitDoc): HistoryDb.Changes {
            val before = previous?.contacts?.associateBy { it.id }.orEmpty()
            val upserts = ArrayList<io.github.thelony97.orbit.core.model.HistoryEntry>()
            val deletes = ArrayList<String>()
            val aUpserts = ArrayList<io.github.thelony97.orbit.core.model.Attachment>()
            val aDeletes = ArrayList<String>()
            for (c in next.contacts) {
                val old = before[c.id]
                if (old == null || old.entries !== c.entries) {
                    val oldById = old?.entries?.associateBy { it.id }.orEmpty()
                    val newIds = HashSet<String>()
                    for (e in c.entries) {
                        newIds += e.id
                        val owned = if (e.contactId == c.id) e else e.copy(contactId = c.id)
                        if (oldById[e.id] != owned) upserts += owned
                    }
                    oldById.keys.filterTo(deletes) { it !in newIds }
                }
                if (old == null || old.gallery !== c.gallery) {
                    val oldById = old?.gallery?.associateBy { it.id }.orEmpty()
                    val newIds = HashSet<String>()
                    for (a in c.gallery) {
                        newIds += a.id
                        val owned = if (a.contactId == c.id) a else a.copy(contactId = c.id)
                        if (oldById[a.id] != owned) aUpserts += owned
                    }
                    oldById.keys.filterTo(aDeletes) { it !in newIds }
                }
            }
            val nextIds = next.contacts.mapTo(HashSet()) { it.id }
            val gone = before.keys.filter { it !in nextIds }
            return HistoryDb.Changes(upserts, deletes, aUpserts, aDeletes, gone)
        }
    }
}
