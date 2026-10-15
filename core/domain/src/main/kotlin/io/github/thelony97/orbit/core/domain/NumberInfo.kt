package io.github.thelony97.orbit.core.domain

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import java.util.Locale

/**
 * Nummernkunde (PLAN 9 B1/B2), offline: what a number is before anyone answers – place and kind
 * from libphonenumber's own tables („Linz · Festnetz“, „Mobil · Deutschland“, „0800 · gratis“,
 * „0900 · teuer“) and whether calling it costs extra (orange, dialling wants a second tap).
 * Fixed tables, no guessing.
 */
object NumberInfo {

    enum class Kind { LANDLINE, MOBILE, FREE, PREMIUM, SHARED_COST, VOIP, PERSONAL, SERVICE, UNKNOWN }

    data class Info(
        /** „+437325551234“. */
        val e164: String,
        val kind: Kind,
        /** Town or area („Linz“), empty when the tables know none. */
        val place: String,
        /** Country name for numbers from abroad („Deutschland“), empty at home. */
        val country: String,
        /** Region code of the number („AT“). */
        val region: String,
        /** National prefix of service numbers („0800“, „0900“), empty otherwise. */
        val prefix: String,
        /** The one line shown under the number. */
        val line: String,
    ) {
        /** Premium and shared-cost numbers: orange, a second tap to dial (PLAN 9 B2). */
        val costs: Boolean get() = kind == Kind.PREMIUM || kind == Kind.SHARED_COST
        val abroad: Boolean get() = country.isNotEmpty()
    }

    private val util: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }
    private val geocoder: PhoneNumberOfflineGeocoder by lazy { PhoneNumberOfflineGeocoder.getInstance() }
    private val german = Locale.GERMAN

    /** Everything known offline about [raw] for a phone in [home] (ISO region, „AT“); null for non-numbers. */
    fun of(raw: String?, home: String = "AT"): Info? {
        val text = raw?.trim().orEmpty()
        if (text.count(Char::isDigit) < 3) return null
        val homeRegion = home.uppercase().ifBlank { "AT" }
        val number = runCatching { util.parse(text, homeRegion) }.getOrNull() ?: return null
        if (!util.isPossibleNumber(number)) return null
        val region = util.getRegionCodeForNumber(number)
            ?: util.getRegionCodeForCountryCode(number.countryCode).takeIf { it != "ZZ" }
            ?: homeRegion
        val e164 = util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164)
        val kind = kindOf(util.getNumberType(number))
        val countryName = Locale.Builder().setRegion(region.takeIf { it.length == 2 } ?: homeRegion).build().getDisplayCountry(german)
        val abroad = region != homeRegion && region != "001"
        // The area, when the tables have one finer than the country itself.
        val place = runCatching { geocoder.getDescriptionForNumber(number, german) }.getOrDefault("")
            .takeIf { it.isNotBlank() && it != countryName }
            .orEmpty()
        val nsn = util.getNationalSignificantNumber(number)
        val prefix = if (kind in serviceKinds && nsn.length >= 3) "0" + nsn.take(3) else ""
        val country = if (abroad) countryName else ""
        return Info(e164, kind, place, country, region, prefix, line(kind, place, country, prefix))
    }

    private val serviceKinds = setOf(Kind.FREE, Kind.PREMIUM, Kind.SHARED_COST, Kind.VOIP)

    private fun kindOf(type: PhoneNumberType): Kind = when (type) {
        PhoneNumberType.FIXED_LINE -> Kind.LANDLINE
        PhoneNumberType.MOBILE -> Kind.MOBILE
        PhoneNumberType.TOLL_FREE -> Kind.FREE
        PhoneNumberType.PREMIUM_RATE -> Kind.PREMIUM
        PhoneNumberType.SHARED_COST -> Kind.SHARED_COST
        PhoneNumberType.VOIP -> Kind.VOIP
        PhoneNumberType.PERSONAL_NUMBER -> Kind.PERSONAL
        PhoneNumberType.UAN, PhoneNumberType.VOICEMAIL, PhoneNumberType.PAGER -> Kind.SERVICE
        // FIXED_LINE_OR_MOBILE (USA, …) cannot be told apart: no claim.
        else -> Kind.UNKNOWN
    }

    /** „Linz · Festnetz“, „Mobil · Deutschland“, „0800 · gratis“, „0900 · teuer“. */
    internal fun line(kind: Kind, place: String, country: String, prefix: String): String {
        val home = country.isEmpty()
        val parts: List<String> = when (kind) {
            Kind.LANDLINE -> if (home) listOf(place, "Festnetz") else listOf(place.ifEmpty { "Festnetz" }, country)
            Kind.MOBILE -> listOf("Mobil", country)
            Kind.FREE -> listOf(prefix.ifEmpty { "Gratisnummer" }, "gratis", country)
            Kind.PREMIUM -> listOf(prefix.ifEmpty { "Mehrwertnummer" }, "teuer", country)
            Kind.SHARED_COST -> listOf(prefix.ifEmpty { "Servicenummer" }, "kostet extra", country)
            Kind.VOIP -> listOf(prefix, "ortsunabhängig", country)
            Kind.PERSONAL -> listOf("Persönliche Nummer", country)
            Kind.SERVICE -> listOf("Servicenummer", country)
            Kind.UNKNOWN -> listOf(place, country)
        }
        return parts.filter { it.isNotBlank() }.distinct().joinToString(" · ")
    }

    /**
     * Possibly a ping call (PLAN 9 B2): missed, from abroad, from nobody Orbit knows – the kind of
     * call that wants an expensive call back. Marked orange; calling back wants a second tap.
     */
    fun pingSuspect(info: Info?, known: Boolean, missed: Boolean): Boolean =
        info != null && !known && missed && info.abroad
}
