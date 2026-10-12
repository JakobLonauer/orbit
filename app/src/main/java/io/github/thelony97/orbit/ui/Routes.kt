package io.github.thelony97.orbit.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Home : Route

    @Serializable
    data class Detail(val id: String) : Route

    /** [id] = null creates a new contact; [prefill] is a JSON-encoded Contact (from the phone book or a suggestion). */
    @Serializable
    data class Edit(val id: String? = null, val prefill: String? = null) : Route

    @Serializable
    data object Groups : Route

    @Serializable
    data object EntryTypes : Route

    @Serializable
    data object Trash : Route

    @Serializable
    data object Suggestions : Route

    @Serializable
    data object MailAccounts : Route
}
