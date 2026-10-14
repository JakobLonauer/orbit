package io.github.thelony97.orbit.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.thelony97.orbit.domain.Audience
import io.github.thelony97.orbit.domain.StatusFilter

/**
 * View state that lives as long as the app process: filters, the network root and map focus.
 * Deliberately not saved – "Organisationen anzeigen" switches itself off again after closing the app.
 */
object Session {
    /** Day (epoch) the Sternenregen last played – once per day, not on every tab visit. */
    var starRainDay = -1L
    var audience by mutableStateOf(Audience.PEOPLE)
    var status by mutableStateOf(StatusFilter.ALL)
    var groups by mutableStateOf(emptySet<String>())

    /** null = "Ich". */
    var networkRoot by mutableStateOf<String?>(null)
    var networkDepth by mutableIntStateOf(2)

    /** „Zurück mit Gedächtnis“ (PLAN 1.2): the row you opened glows once when you come back. */
    var lastOpened: String? = null

    /** Start-Moment (PLAN 1.3) plays once per app start, not on every return to the home screen. */
    var startPlayed = false

    /** Contact the map should centre on next time it opens. */
    var mapFocus by mutableStateOf<String?>(null)

    fun toggleGroup(id: String) {
        groups = if (id in groups) groups - id else groups + id
    }
}
