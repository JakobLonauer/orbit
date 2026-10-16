package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import java.time.Instant
import java.time.ZoneId

/**
 * Ankommen (PLAN 9 D8): only places with something to show – open topics, a card (Ausweis),
 * hidden members or a WLAN – get a 100 m fence; arriving shows that, leaving leaves a Beleg.
 */
object Arrival {

    const val RADIUS_M = 100f
    /** Android allows 100 fences per app. */
    const val MAX_PLACES = 100
    /** Shorter stays are passing by, not a visit. */
    const val MIN_VISIT_MS = 3 * 60_000L

    data class Place(val contactId: String, val lat: Double, val lng: Double)

    fun hasContent(c: Contact): Boolean =
        c.topics.any { !it.done } || CallerIndex.idField(c) != null || c.members.isNotEmpty() || c.wifi.isNotEmpty()

    fun places(contacts: List<Contact>): List<Place> = contacts.asSequence()
        .filter { it.deletedAt == null && !it.archived && !it.isMe }
        .mapNotNull { c -> c.geo?.takeIf { it.found && (it.lat != 0.0 || it.lng != 0.0) && hasContent(c) }?.let { Place(c.id, it.lat, it.lng) } }
        .take(MAX_PLACES)
        .toList()

    /**
     * Ankommen per bekanntem WLAN (D8): the place whose saved network has exactly this name;
     * null for an unknown network (or none). Archive, trash and Ich never.
     */
    fun byWifi(contacts: List<Contact>, rawSsid: String?): String? {
        val ssid = WifiCode.ssidOf(rawSsid) ?: return null
        return contacts.firstOrNull { c ->
            c.deletedAt == null && !c.archived && !c.isMe && c.wifi.any { WifiCode.ssidOf(it.ssid) == ssid }
        }?.id
    }

    /** True when any place could be recognised by its WLAN. */
    fun anyWifi(contacts: List<Contact>): Boolean = contacts.any { it.deletedAt == null && !it.archived && !it.isMe && it.wifi.isNotEmpty() }

    /** What the arrival card says, short: „2 Themen · Ausweis · WLAN“. */
    fun summary(c: Contact): String = buildList {
        val topics = c.topics.count { !it.done }
        if (topics == 1) add("1 Thema") else if (topics > 1) add("$topics Themen")
        if (CallerIndex.idField(c) != null) add("Ausweis")
        if (c.members.isNotEmpty()) add(if (c.members.size == 1) "1 Mitarbeiter" else "${c.members.size} Mitarbeiter")
        if (c.wifi.isNotEmpty()) add("WLAN")
    }.joinToString(" · ")

    /** „Besuch 14:05–14:40“; null when too short to count. */
    fun visitNote(start: Long, end: Long, zone: ZoneId): String? {
        if (end - start < MIN_VISIT_MS) return null
        fun hm(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }
        return "Besuch ${hm(start)}–${hm(end)}"
    }
}
