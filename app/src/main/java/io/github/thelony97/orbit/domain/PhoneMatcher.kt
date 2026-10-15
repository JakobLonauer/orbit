package io.github.thelony97.orbit.domain

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import io.github.thelony97.orbit.data.Contact
import java.util.Locale

object Phones {

    fun countryIso(context: Context): String {
        val tm = context.getSystemService(TelephonyManager::class.java)
        val iso = listOfNotNull(tm?.networkCountryIso, tm?.simCountryIso, Locale.getDefault().country)
            .firstOrNull { it.isNotBlank() }
        return (iso ?: "AT").uppercase(Locale.ROOT)
    }

    /** PLAN 9 B10: the SIM's country (home) and the network's right now (abroad when they differ). */
    fun homeIso(context: Context): String {
        val tm = context.getSystemService(TelephonyManager::class.java)
        return (tm?.simCountryIso?.takeIf { it.isNotBlank() } ?: Locale.getDefault().country.ifBlank { "AT" }).uppercase(Locale.ROOT)
    }

    fun hereIso(context: Context): String? =
        context.getSystemService(TelephonyManager::class.java)?.networkCountryIso?.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT)

    /** The number as it is dialled (PLAN 9 B10 Wählregeln). */
    fun dial(context: Context, number: String, hide: Boolean = false): String =
        io.github.thelony97.orbit.core.domain.DialRules.dial(number, homeIso(context), hereIso(context), hide)

    /** E.164 if possible ("+436601234567"), otherwise the bare digits. */
    fun normalize(raw: String, iso: String): String? {
        val cleaned = raw.trim()
        if (cleaned.count { it.isDigit() } < 3) return null
        val e164 = runCatching { PhoneNumberUtils.formatNumberToE164(cleaned, iso) }.getOrNull()
        if (!e164.isNullOrBlank()) return e164
        val plus = cleaned.startsWith("+") || cleaned.startsWith("00")
        val digits = cleaned.filter { it.isDigit() }.removePrefix(if (cleaned.startsWith("00")) "00" else "")
        return if (plus) "+$digits" else digits
    }

    fun tail(normalized: String): String = normalized.filter { it.isDigit() }.takeLast(9)

    fun pretty(raw: String, iso: String): String =
        runCatching { PhoneNumberUtils.formatNumber(raw, iso) }.getOrNull() ?: raw
}

/** Maps phone numbers to contacts. Exact E.164 match first, then the last nine digits. */
class PhoneMatcher(private val contacts: List<Contact>, private val iso: String) {
    private val exact = HashMap<String, String>()
    private val byTail = HashMap<String, MutableSet<String>>()

    init {
        contacts.filter { it.deletedAt == null && !it.isMe }.forEach { c ->
            c.phones.forEach { p ->
                val n = Phones.normalize(p.value, iso) ?: return@forEach
                exact[n] = c.id
                byTail.getOrPut(Phones.tail(n)) { mutableSetOf() }.add(c.id)
            }
        }
    }

    fun match(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val n = Phones.normalize(raw, iso) ?: return null
        exact[n]?.let { return it }
        val t = Phones.tail(n)
        if (t.length < 6) return null
        return byTail[t]?.singleOrNull()
    }

    fun key(raw: String?): String? = raw?.let { Phones.normalize(it, iso) }

    /**
     * For calls (PLAN 3.6 Regeln): the Zuordnungs-Maschine – a person's or organisation's own
     * number first, then a hidden member's Durchwahl, then an organisation's number range.
     */
    private val machine by lazy { io.github.thelony97.orbit.core.domain.Matcher(contacts) { Phones.normalize(it, iso) } }

    fun matchCall(raw: String?): io.github.thelony97.orbit.core.domain.Matcher.Match? = machine.matchNumber(raw)

    /** The Durchwahl of [raw] in [prefixes] (same rule as the range match). */
    fun extension(raw: String?, prefixes: List<String>): String? = machine.extension(raw, prefixes)
}
