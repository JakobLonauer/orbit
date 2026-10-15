package io.github.thelony97.orbit.core.domain

import java.time.Instant
import java.time.ZoneId

/** Automatiken (PLAN 9 E17): fixed texts and rules of the built-in automations. */
object Automations {

    /** „Bin gerade im Termin – ich rufe dich ab 15:30 zurück.“ (Sie: „Sie“). */
    fun busyText(endMillis: Long, zone: ZoneId, formal: Boolean): String {
        val t = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalTime()
        val time = "%d:%02d".format(t.hour, t.minute)
        return if (formal) "Ich bin gerade in einem Termin und rufe Sie ab $time zurück." else "Bin gerade im Termin – ich rufe dich ab $time zurück."
    }

    /** Whether a caller in [groupIds] gets the busy SMS ([allowed] empty = everyone). */
    fun busyApplies(groupIds: List<String>, allowed: List<String>): Boolean = allowed.isEmpty() || groupIds.any { it in allowed }
}
