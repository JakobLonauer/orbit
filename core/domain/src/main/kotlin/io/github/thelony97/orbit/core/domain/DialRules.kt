package io.github.thelony97.orbit.core.domain

import com.google.i18n.phonenumbers.PhoneNumberUtil

/**
 * Wählregeln (PLAN 9 B10), fixed and visible: abroad a national number („0664 1234567“) is dialled
 * with the home country code („+436641234567“); for a contact with „Nummer unterdrücken“ the call
 * goes out with #31# in front. Short codes and numbers already international stay as they are.
 */
object DialRules {

    const val HIDE = "#31#"

    /**
     * The number as it is dialled. [home]: the SIM's country (ISO 3166), [here]: the network's
     * country right now (null or blank when unknown – then nothing changes).
     */
    fun dial(number: String, home: String, here: String?, hide: Boolean = false): String {
        val n = number.trim()
        // Short numbers (emergency, services) are never touched.
        if (n.count(Char::isDigit) <= SHORT) return n
        // A macro's pauses and waits („,“ „;“) stay behind the number.
        val cut = n.indexOfFirst { it == ',' || it == ';' }.takeIf { it >= 0 } ?: n.length
        val head = n.substring(0, cut)
        val tail = n.substring(cut)
        val abroad = !here.isNullOrBlank() && !here.equals(home, ignoreCase = true)
        val national = head.startsWith("0") && !head.startsWith("00")
        val out = (if (abroad && national) international(head, home) ?: head else head) + tail
        return if (hide && !out.startsWith(HIDE)) HIDE + out else out
    }

    private const val SHORT = 5

    private fun international(n: String, home: String): String? = runCatching {
        val util = PhoneNumberUtil.getInstance()
        val parsed = util.parse(n, home.uppercase())
        if (!util.isPossibleNumber(parsed)) null else util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
    }.getOrNull()
}
