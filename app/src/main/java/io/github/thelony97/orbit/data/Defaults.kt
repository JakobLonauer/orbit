package io.github.thelony97.orbit.data

import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

object TypeIds {
    const val MEET = "meet"
    const val CALL = "call"
    const val VIDEO = "video"
    const val MESSAGE = "message"
    const val EMAIL = "email"
    const val EVENT = "event"
}

data class RelationKind(
    val key: String,
    /** How the relation reads from the "from" side: "<from> ist <forward> von <to>". */
    val forward: String,
    /** How it reads from the "to" side. */
    val backward: String,
    /** Typical for "person → organisation" links. */
    val toOrg: Boolean = false,
)

data class OrgCategory(val key: String, val label: String, val icon: String)

object Defaults {

    val orgCategories = listOf(
        OrgCategory("company", "Firma", "business"),
        OrgCategory("work", "Arbeit", "work"),
        OrgCategory("health", "Gesundheit", "health"),
        OrgCategory("authority", "Behörde", "authority"),
        OrgCategory("service", "Dienstleister", "service"),
        OrgCategory("club", "Verein", "club"),
        OrgCategory("other", "Sonstiges", "other"),
    )

    fun orgCategory(key: String?): OrgCategory =
        orgCategories.firstOrNull { it.key == key } ?: orgCategories.first()

    const val ME_DEFAULT_NAME = "Ich"

    fun me(): Contact = Contact(id = newId(), firstName = ME_DEFAULT_NAME, isMe = true, createdAt = now(), updatedAt = now())

    val entryTypes = listOf(
        EntryType(TypeIds.MEET, "Treffen", "meet", resetsTimer = true, builtin = true),
        EntryType(TypeIds.CALL, "Anruf", "call", resetsTimer = true, builtin = true),
        EntryType(TypeIds.VIDEO, "Videocall", "video", resetsTimer = true, builtin = true),
        EntryType(TypeIds.MESSAGE, "Nachricht", "message", resetsTimer = false, builtin = true),
        EntryType(TypeIds.EMAIL, "E-Mail", "email", resetsTimer = false, builtin = true),
        EntryType(TypeIds.EVENT, "Event", "event", resetsTimer = true, builtin = true),
    )

    /** Palette for groups – readable on light and dark backgrounds. */
    val groupColors: List<Long> = listOf(
        0xFFE57373, 0xFFFFB74D, 0xFFFFD54F, 0xFF81C784, 0xFF4DB6AC,
        0xFF4FC3F7, 0xFF7986CB, 0xFFBA68C8, 0xFFF06292, 0xFFA1887F,
    )

    fun starterGroups() = listOf(
        Group(newId(), "Familie", groupColors[0], 14),
        Group(newId(), "Freunde", groupColors[5], 30),
        Group(newId(), "Arbeit", groupColors[6], 60),
        Group(newId(), "Uni", groupColors[3], 60),
    )

    val relationKinds = listOf(
        RelationKind("partner", "Partner/in", "Partner/in"),
        RelationKind("spouse", "Ehepartner/in", "Ehepartner/in"),
        RelationKind("parent", "Elternteil", "Kind"),
        RelationKind("sibling", "Geschwister", "Geschwister"),
        RelationKind("grandparent", "Großelternteil", "Enkelkind"),
        RelationKind("relative", "Verwandt", "Verwandt"),
        RelationKind("friend", "Freund/in", "Freund/in"),
        RelationKind("colleague", "Kolleg/in", "Kolleg/in"),
        RelationKind("boss", "Vorgesetzte/r", "Teammitglied"),
        RelationKind("roommate", "Mitbewohner/in", "Mitbewohner/in"),
        // Person → organisation
        RelationKind("works_at", "Mitarbeiter/in", "Arbeitgeber", toOrg = true),
        RelationKind("patient", "Patient/in", "Ärzt:in / Praxis", toOrg = true),
        RelationKind("client", "Kunde/Kundin", "Dienstleister", toOrg = true),
        RelationKind("member", "Mitglied", "Verein / Organisation", toOrg = true),
    )

    fun relationKind(key: String): RelationKind =
        relationKinds.firstOrNull { it.key == key } ?: RelationKind(key, key, key)

    val phoneLabels = listOf("Mobil", "Privat", "Arbeit", "Sonstige")
    val emailLabels = listOf("Privat", "Arbeit", "Uni", "Sonstige")

    /** Icons available for custom entry types (keys mapped in the UI). */
    val entryIcons = listOf(
        "meet", "call", "video", "message", "email", "event", "coffee", "food",
        "drink", "sport", "game", "music", "travel", "gift", "heart", "star",
    )

    fun fresh(): OrbitData = OrbitData(
        contacts = listOf(me()),
        groups = starterGroups(),
        entryTypes = entryTypes,
    )
}
