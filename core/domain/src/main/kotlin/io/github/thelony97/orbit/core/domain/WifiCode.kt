package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.WifiNet

/** PLAN 9 D7: the WLAN as the „WIFI:“ QR every phone camera joins, and the SSID from the system. */
object WifiCode {

    private val SPECIAL = setOf('\\', ';', ',', ':', '"')

    private fun escape(s: String): String = buildString {
        for (ch in s) {
            if (ch in SPECIAL) append('\\')
            append(ch)
        }
    }

    fun qr(w: WifiNet): String {
        val open = w.password.isEmpty() || w.security.equals("nopass", ignoreCase = true)
        val type = if (open) "nopass" else w.security.uppercase()
        return buildString {
            append("WIFI:T:").append(type).append(";S:").append(escape(w.ssid)).append(';')
            if (!open) append("P:").append(escape(w.password)).append(';')
            append(';')
        }
    }

    /** The system reports „"Name"“ with quotes, or „<unknown ssid>“ without permission. */
    fun ssidOf(raw: String?): String? = raw?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
}
