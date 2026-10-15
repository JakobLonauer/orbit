package io.github.thelony97.orbit.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Home : Route

    /** [entryId]: open this Verlauf entry for editing right away (QS tile „Notiz zum letzten Anruf“). */
    @Serializable
    data class Detail(val id: String, val entryId: String? = null) : Route

    /** Fotos-Seite (PLAN 3.10): all pictures, or one contact's ([contactId]). */
    @Serializable
    data class Photos(val contactId: String? = null) : Route

    /** A hidden member's own page inside its organisation (PLAN 3.6). */
    @Serializable
    data class MemberPage(val orgId: String, val memberId: String) : Route

    /** [id] = null creates a new contact; [prefill] is a JSON-encoded Contact (from the phone book or a suggestion). */
    @Serializable
    data class Edit(val id: String? = null, val prefill: String? = null) : Route

    /** Mehrere Handy-Kontakte auf einmal übernehmen (PLAN 3.14). */
    @Serializable
    data object PhoneImport : Route

    /** PLAN 9 A2: the cards of a .vcf file to pick from (held in [Session.cardDrafts]). */
    @Serializable
    data object CardImport : Route

    /** PLAN 9 A1 „Neu im Telefonbuch“: only these phone-book contacts, all picked. */
    @Serializable
    data class PhoneImportPick(val contactIds: List<Long>, val seenUpTo: Long = -1) : Route

    @Serializable
    data object Groups : Route

    @Serializable
    data object EntryTypes : Route

    @Serializable
    data object Trash : Route

    @Serializable
    data object Archive : Route

    @Serializable
    data object Settings : Route

    /** One of the five settings areas (PLAN 3.13): capture, notify, contacts, security, about. */
    @Serializable
    data class SettingsArea(val key: String) : Route

    @Serializable
    data object Suggestions : Route

    @Serializable
    data object MailAccounts : Route
}
