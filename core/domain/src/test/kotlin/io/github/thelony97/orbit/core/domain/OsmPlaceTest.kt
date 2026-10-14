package io.github.thelony97.orbit.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OsmPlaceTest {
    private val answer = """
        [{"place_id":1,"lat":"48.3069","lon":"14.2858","name":"Ordination Dr. Huber","display_name":"Ordination Dr. Huber, 12, Landstraße, Linz, 4020, Österreich",
          "address":{"road":"Landstraße","house_number":"12","city":"Linz","postcode":"4020","country":"Österreich"},
          "extratags":{"website":"https://dr-huber.at","phone":"+43 732 123456;+43 732 123457","opening_hours":"Mo-Fr 08:00-12:00"}}]
    """.trimIndent()

    @Test fun firstHitWithTags() {
        val f = OsmPlace.parse(answer)!!
        assertEquals("Ordination Dr. Huber", f.name)
        assertEquals("Landstraße 12, 4020 Linz", f.address)
        assertEquals("https://dr-huber.at", f.website)
        assertEquals("+43 732 123456", f.phone)
        assertEquals("Mo-Fr 08:00-12:00", f.openingHours)
        assertEquals(48.3069, f.lat!!, 1e-6)
    }

    @Test fun nothingOrJunk() {
        assertNull(OsmPlace.parse("[]"))
        assertNull(OsmPlace.parse("<html>"))
        val bare = OsmPlace.parse("""[{"display_name":"Mensa, Linz"}]""")!!
        assertEquals("Mensa", bare.name)
        assertNull(bare.website)
        assertEquals("Arzt Müller, Hauptplatz 1", OsmPlace.query(" Arzt Müller ", "Hauptplatz 1"))
    }
}
