package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Attachment
import io.github.thelony97.orbit.core.model.Contact
import java.time.Instant
import java.time.ZoneId

/**
 * Fotos-Seite (PLAN 3.10): pictures by the date they were taken (EXIF, else when they came in),
 * newest first, one section per month. A picture shared by several contacts shows once.
 */
object PhotoMonths {

    data class Item(val contactId: String, val photo: Attachment)

    /** [key] "2026-10" for the scrubber, [title] „Oktober 2026“. */
    data class Month(val key: String, val title: String, val items: List<Item>)

    fun dateOf(a: Attachment): Long = a.takenAt ?: a.addedAt

    fun build(contacts: List<Contact>, zone: ZoneId, visible: (Contact) -> Boolean): List<Month> {
        val items = contacts.filter(visible)
            .flatMap { c -> c.gallery.filter { it.mime.startsWith("image/") }.map { Item(c.id, it) } }
            .distinctBy { it.photo.file }
            .sortedByDescending { dateOf(it.photo) }
        return items.groupBy { Instant.ofEpochMilli(dateOf(it.photo)).atZone(zone).let { d -> d.year to d.monthValue } }
            .map { (ym, list) ->
                val (y, m) = ym
                Month("%04d-%02d".format(y, m), BirthdayTab.MONTHS[m - 1] + " " + y, list)
            }
    }

    /** The newest [n] of one contact for its page („im Kontakt die neuesten 6“). */
    fun newest(c: Contact, n: Int = 6): List<Attachment> =
        c.gallery.filter { it.mime.startsWith("image/") }.sortedByDescending { dateOf(it) }.take(n)
}
