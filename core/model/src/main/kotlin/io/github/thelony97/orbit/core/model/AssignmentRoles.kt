package io.github.thelony97.orbit.core.model

/** A Zuordnung role, readable from both sides: org page shows [personSide], person page [orgSide]. */
data class AssignmentRole(val key: String, val orgSide: String, val personSide: String)

object AssignmentRoles {
    const val CUSTOM = "custom"

    val all = listOf(
        AssignmentRole("gp", "Hausarzt", "Patient:in"),
        AssignmentRole("dentist", "Zahnarzt", "Patient:in"),
        AssignmentRole("specialist", "Facharzt", "Patient:in"),
        AssignmentRole("doctor", "Ärzt:in", "Patient:in"),
        AssignmentRole("employer", "Arbeitgeber", "Mitarbeiter:in"),
        AssignmentRole("colleague", "Team", "Kolleg:in"),
        AssignmentRole("supervisor", "Team", "Vorgesetzte:r"),
        AssignmentRole("lead", "Projekt", "Projektleitung"),
        AssignmentRole("school", "Schule/Uni", "Schüler:in/Studierende"),
        AssignmentRole("bank", "Bank", "Kund:in"),
        AssignmentRole("insurance", "Versicherung", "Versicherte:r"),
        AssignmentRole("club", "Verein", "Mitglied"),
        AssignmentRole("service", "Dienstleister", "Kund:in"),
    )

    fun of(link: OrgLink): AssignmentRole =
        all.firstOrNull { it.key == link.role } ?: AssignmentRole(CUSTOM, link.label, link.label)
}
