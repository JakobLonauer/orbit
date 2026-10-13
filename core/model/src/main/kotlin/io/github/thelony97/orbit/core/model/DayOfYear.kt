package io.github.thelony97.orbit.core.model

import kotlinx.serialization.Serializable

/** A calendar day without time zone. [year] is optional (e.g. a birthday without year). */
@Serializable
data class CalendarDay(
    val day: Int,
    val month: Int,
    val year: Int? = null,
)
