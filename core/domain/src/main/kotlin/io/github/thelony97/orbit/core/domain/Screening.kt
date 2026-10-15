package io.github.thelony97.orbit.core.domain

import kotlinx.serialization.Serializable

/**
 * Abwehr ohne Verlust (PLAN 9 B8): which rule silences a ringing call. Fixed, visible rules for
 * unknown numbers only; a known number is never touched, and a call is never rejected – it just
 * rings without sound and stays in the call log (and as a thin trace in the Verlauf).
 */
object Screening {

    @Serializable
    data class Rules(
        val night: Boolean = true,
        val premium: Boolean = true,
        val countries: List<String> = emptyList(),
        /** Normalised number beginnings (E.164 without padding). */
        val series: List<String> = emptyList(),
    )

    const val NIGHT = "Nachts"
    const val PREMIUM = "Mehrwert"
    const val COUNTRY = "Land"
    const val SERIES = "Nummernserie"

    /** The usual origins of ping calls (Wangiri) – offered as chips under „Länder“. */
    val pingCountries = listOf("TN", "CI", "BJ", "NG", "SL", "GN", "ML", "MR", "CG", "CD", "LR", "SO", "CU")

    /** Region codes from what Jakob typed („ng, CI ; sn“): two letters each, upper case, once. */
    fun countriesOf(typed: String): List<String> =
        typed.split(',', ';', ' ', '\n').map { it.trim().uppercase() }.filter { it.length == 2 && it.all(Char::isLetter) }.distinct()

    /** Number beginnings from what Jakob typed („0900, +43 720“): at least three digits each. */
    fun seriesOf(typed: String): List<String> =
        typed.split(',', ';', '\n').map { it.trim() }.filter { p -> p.count(Char::isDigit) >= 3 }.distinct()

    /**
     * The rule that silences [e164] (with its [info]) at [hour] (0–23), or null to let it ring.
     * [known]: in Orbit (contact, member or range) – never silenced.
     */
    fun rule(rules: Rules?, e164: String?, info: NumberInfo.Info?, known: Boolean, hour: Int): String? {
        if (rules == null || known) return null
        val n = e164.orEmpty()
        return when {
            n.isNotEmpty() && rules.series.any { n.startsWith(it) && n.length > it.length } -> SERIES
            info != null && info.abroad && info.region in rules.countries -> COUNTRY
            rules.premium && info?.costs == true -> PREMIUM
            // A hidden number at night is just as unknown.
            rules.night && (hour >= 22 || hour < 7) -> NIGHT
            else -> null
        }
    }
}
