package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2 adds two optional fields (SMS templates, SIM per contact). Data written by 1.0.101 has
 * neither and must read exactly as before; new data must survive a round trip.
 */
class R2FieldsTest {
    // The shape 1.0.101 writes (schema 2, no smsTemplates, no simId).
    private val old = """
        {"schema":2,"contacts":[{"id":"anna","firstName":"Anna","phones":[{"label":"Mobil","value":"+43 660 1"}],"defaultPhone":0}],
         "settings":{"onboardingDone":true,"minCallSeconds":30,"callerInfo":true}}
    """.trimIndent()

    @Test fun oldDataReadsWithDefaults() {
        val doc = DocJson.decode(old)
        assertEquals(Settings.DEFAULT_SMS_TEMPLATES, doc.settings.smsTemplates)
        assertEquals(30, doc.settings.minCallSeconds)
        assertNull(doc.contacts.single().simId)
        assertEquals("+43 660 1", doc.contacts.single().phones.single().value)
    }

    @Test fun newFieldsRoundTrip() {
        val doc = DocJson.decode(old).let { d ->
            d.copy(
                settings = d.settings.copy(smsTemplates = listOf("Gleich zurück")),
                contacts = d.contacts.map { it.copy(simId = "sim-2") },
            )
        }
        val back = DocJson.decode(DocJson.encode(doc))
        assertEquals(listOf("Gleich zurück"), back.settings.smsTemplates)
        assertEquals("sim-2", back.contacts.single().simId)
    }

    @Test fun emptyTemplateListStaysEmpty() {
        val doc = DocJson.decode(old).let { it.copy(settings = it.settings.copy(smsTemplates = emptyList())) }
        assertTrue(DocJson.decode(DocJson.encode(doc)).settings.smsTemplates.isEmpty())
    }
}
