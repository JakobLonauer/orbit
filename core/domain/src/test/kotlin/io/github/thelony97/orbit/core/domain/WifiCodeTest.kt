package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.WifiNet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WifiCodeTest {
    @Test fun wpa() = assertEquals("WIFI:T:WPA;S:Praxis Gast;P:a\\;b\\:c;;", WifiCode.qr(WifiNet("1", "Praxis Gast", "a;b:c")))

    @Test fun open() = assertEquals("WIFI:T:nopass;S:Cafe;;", WifiCode.qr(WifiNet("1", "Cafe")))

    @Test fun ssid() {
        assertEquals("Praxis", WifiCode.ssidOf("\"Praxis\""))
        assertNull(WifiCode.ssidOf("<unknown ssid>"))
    }
}
