package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.ContactKind
import io.github.thelony97.orbit.core.model.CustomField
import io.github.thelony97.orbit.core.model.OrbitDoc
import io.github.thelony97.orbit.core.model.OrgLink
import org.junit.Assert.assertEquals
import org.junit.Test

class MyNumbersTest {
    @Test fun ownFieldsThenTheServicesNumbers() {
        val me = Contact("me", isMe = true, firstName = "Jakob", fields = listOf(CustomField("1", "SV-Nr.", "1234 010190"), CustomField("2", "Leer", " ")))
        val bank = Contact("bank", kind = ContactKind.ORG, company = "Raiffeisen", fields = listOf(CustomField("3", "Kundennummer", "K-77")))
        val versicherung = Contact("v", kind = ContactKind.ORG, company = "Allianz", fields = listOf(CustomField("4", "Polizze", "P-9")))
        val gone = Contact("g", kind = ContactKind.ORG, company = "Alt", archived = true, fields = listOf(CustomField("5", "Kundennummer", "X")))
        val other = Contact("o", kind = ContactKind.ORG, company = "Fremd", fields = listOf(CustomField("6", "Kundennummer", "Y")))
        val doc = OrbitDoc(
            contacts = listOf(me, bank, versicherung, gone, other),
            orgLinks = listOf(OrgLink("l1", "me", "bank", "bank"), OrgLink("l2", "me", "v", "insurance"), OrgLink("l3", "me", "g", "bank")),
        )
        assertEquals(
            listOf(
                MyNumbers.Item(null, "SV-Nr.", "1234 010190"),
                MyNumbers.Item("Allianz", "Polizze", "P-9"),
                MyNumbers.Item("Raiffeisen", "Kundennummer", "K-77"),
            ),
            MyNumbers.of(doc),
        )
    }
}
