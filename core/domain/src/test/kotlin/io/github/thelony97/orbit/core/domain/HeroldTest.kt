package io.github.thelony97.orbit.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroldTest {

    @Test fun jsonLd() {
        val html = """
            <html><head><script type="application/ld+json">
            {"@context":"https://schema.org","@graph":[
              {"@type":"LocalBusiness","name":"Elektro Berger","telephone":"+43 732 999888"},
              {"@type":"LocalBusiness","name":"Huber Installationen GmbH","description":"Installateur",
               "telephone":"+43 732 555123","address":{"@type":"PostalAddress","streetAddress":"Hauptstraße 5","postalCode":"4020","addressLocality":"Linz"}}
            ]}
            </script></head><body></body></html>
        """.trimIndent()
        val hit = Herold.parse(html, "+43732555123")!!
        assertEquals("Huber Installationen GmbH", hit.name)
        assertEquals("Installateur", hit.category)
        assertEquals("Hauptstraße 5, 4020 Linz", hit.address)
    }

    @Test fun vcardLinkOnThePage() {
        val html = """<a class="save" href="data:text/vcard;charset=utf-8,BEGIN%3AVCARD%0AVERSION%3A3.0%0AORG%3AHEROLD%20Business%20Data%20GmbH%0AADR%3BWORK%3A%3B%3BCanettistra%C3%9Fe%205%3BWien%3BWien%3B1100%3B%0ATEL%3BWORK%3A%2B43%2050%20485%20333%0AEND%3AVCARD">Kontakt speichern</a>"""
        val hit = Herold.parse(html, "+4350485333")!!
        assertEquals("HEROLD Business Data GmbH", hit.name)
        assertEquals("Canettistraße 5", hit.street)
        assertEquals("1100", hit.zip)
        assertEquals("Wien", hit.city)
    }

    @Test fun microdataInAnyAttributeOrder() {
        val html = """
            <div itemscope itemtype="https://schema.org/LocalBusiness">
              <h2 itemprop="name">Elektro Berger</h2><a itemprop="telephone" href="tel:+43732999888">0732 999888</a>
            </div>
            <div itemscope itemtype="https://schema.org/LocalBusiness">
              <meta content="Bäckerei Mayr &amp; Söhne" itemProp="name">
              <span itemprop="streetAddress">Linzer Straße 7</span><span itemprop="postalCode">4060</span>
              <span itemprop="addressLocality">Leonding</span>
              <a href="tel:+43732123456" itemprop="telephone">0732 123456</a>
            </div>
        """.trimIndent()
        val hit = Herold.parse(html, "+43732123456")!!
        assertEquals("Bäckerei Mayr & Söhne", hit.name)
        assertEquals("Linzer Straße 7, 4060 Leonding", hit.address)
    }

    @Test fun headingAroundTheTelLink() {
        val html = """
            <article><h2 class="font-satoshi text-xl"><!--t=3x-->Tischlerei Gruber<!----></h2>
            <p>Hauptplatz 3<br>4100 Ottensheim</p><a href="tel:+43 7234 82222">Anrufen</a></article>
        """.trimIndent()
        val hit = Herold.parse(html, "+43723482222")!!
        assertEquals("Tischlerei Gruber", hit.name)
        assertEquals("Hauptplatz 3", hit.street)
        assertEquals("4100", hit.zip)
        assertEquals("Ottensheim", hit.city)
    }

    @Test fun otherNumbersAreNoHit() {
        val html = """<h2>Tischlerei Gruber</h2><a href="tel:+43723482222">Anrufen</a>"""
        assertNull(Herold.parse(html, "+43732555123"))
        assertNull(Herold.parse("<html></html>", "+43732555123"))
    }

    @Test fun numbers() {
        assertEquals("0732555123", Herold.national("+43732555123"))
        assertNull(Herold.national("+4930123456"))
        assertTrue(Herold.sameNumber("+43 732 555123", "0732/555123"))
        assertFalse(Herold.sameNumber("+43 732 555123", "+43 732 555124"))
        assertFalse(Herold.sameNumber("123", "123"))
        assertEquals(
            listOf("https://www.herold.at/gelbe-seiten/suche/?term=0732555123", "https://www.herold.at/telefonbuch/suche/?term=0732555123"),
            Herold.urls("0732 555123"),
        )
    }
}
