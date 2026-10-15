package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.domain.ShareIn.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareInTest {
    @Test fun googleMapsPlace() {
        val text = "Hausarztpraxis Dr. Müller\nHauptplatz 1, 4020 Linz\nhttps://maps.app.goo.gl/AbC123"
        assertEquals(Item.Place("Hausarztpraxis Dr. Müller", "Hauptplatz 1, 4020 Linz", "https://maps.app.goo.gl/AbC123"), ShareIn.parse(text, null))
        // Only the link, the name in the subject.
        assertEquals(Item.Place("Mensa", "", "https://www.google.com/maps/place/Mensa"), ShareIn.parse("https://www.google.com/maps/place/Mensa", "Mensa"))
    }

    @Test fun browserPage() {
        assertEquals(Item.Page("KUSSS", "https://kusss.jku.at/kusss/index.action"), ShareIn.parse("https://kusss.jku.at/kusss/index.action", "KUSSS"))
        assertEquals(Item.Page("moodle.jku.at", "https://moodle.jku.at/"), ShareIn.parse("https://moodle.jku.at/", null))
        assertEquals(Item.Page("Spannender Artikel", "https://example.at/a"), ShareIn.parse("Spannender Artikel https://example.at/a", ""))
    }

    @Test fun numberAndMailAlone() {
        assertEquals(Item.Number("+43 660 1234567"), ShareIn.parse(" +43 660 1234567 ", null))
        assertEquals(Item.Number("0732/2468-12"), ShareIn.parse("tel:0732/2468-12", null))
        assertEquals(Item.Email("anna@jku.at"), ShareIn.parse("mailto:anna@jku.at", null))
        // Too short for a number, or more than a number: stays text.
        assertEquals(Item.Text("1234"), ShareIn.parse("1234", null))
        assertEquals(Item.Text("Ruf 0660 1234567 an"), ShareIn.parse("Ruf 0660 1234567 an", null))
    }

    @Test fun plainTextAndNothing() {
        assertEquals(Item.Text("Kaffee mitbringen"), ShareIn.parse("Kaffee mitbringen", null))
        assertNull(ShareIn.parse("  ", null))
        assertEquals(false, ShareIn.isMaps("https://example.at/googlemaps-fans"))
    }
}
