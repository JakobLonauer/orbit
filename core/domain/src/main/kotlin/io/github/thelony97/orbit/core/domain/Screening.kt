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
        /** Number beginnings as Jakob typed them („0720“, „+44 7“); see [prefix]. */
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
     * A typed number beginning as the start of an E.164 number: „0720“ in Austria → „+43720“,
     * „0044 7“ → „+447“, „+44 7“ → „+447“. Null without three digits.
     */
    fun prefix(typed: String, home: String): String? {
        val t = typed.trim()
        val digits = t.filter(Char::isDigit)
        if (digits.length < 3) return null
        return when {
            t.startsWith("+") -> "+$digits"
            digits.startsWith("00") -> "+" + digits.drop(2)
            digits.startsWith("0") -> {
                val cc = runCatching { com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance().getCountryCodeForRegion(home.uppercase()) }.getOrDefault(0)
                if (cc > 0) "+$cc" + digits.drop(1) else null
            }
            else -> "+$digits"
        }
    }

    /**
     * The rule that silences [e164] (with its [info]) at [hour] (0–23), or null to let it ring.
     * [known]: in Orbit (contact, member or range) – never silenced. [home]: the phone's country,
     * for number beginnings typed nationally.
     */
    fun rule(rules: Rules?, e164: String?, info: NumberInfo.Info?, known: Boolean, hour: Int, home: String = "AT"): String? {
        if (rules == null || known) return null
        return when {
            inSeries(rules, e164.orEmpty(), home) -> SERIES
            fromCountry(rules, info) -> COUNTRY
            rules.premium && info?.costs == true -> PREMIUM
            // A hidden number at night is just as unknown.
            rules.night && night(hour) -> NIGHT
            else -> null
        }
    }

    private fun fromCountry(rules: Rules, info: NumberInfo.Info?): Boolean = info != null && info.abroad && info.region in rules.countries

    private fun night(hour: Int): Boolean = hour >= 22 || hour < 7

    private fun inSeries(rules: Rules, n: String, home: String): Boolean =
        n.isNotEmpty() && rules.series.any { typed -> prefix(typed, home)?.let { n.startsWith(it) && n.length > it.length } == true }
}
