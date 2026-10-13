package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.ContactKind
import io.github.thelony97.orbit.core.model.LabeledValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class T9Test {
    private fun person(id: String, first: String, last: String, vararg phones: String, archived: Boolean = false, favorite: Boolean = false) =
        Contact(id = id, firstName = first, lastName = last, phones = phones.map { LabeledValue("Mobil", it) }, archived = archived, favorite = favorite)

    private val anna = person("anna", "Anna", "Berger", "+43 660 1234567")
    private val bea = person("bea", "Bea", "Kern", "0699 5550001")
    private val juergen = person("j", "Jürgen", "Öller", "+43 732 778899")
    private val arzt = Contact(id = "arzt", kind = ContactKind.ORG, company = "Arzt Müller", phones = listOf(LabeledValue("Zentrale", "+43 732 600100")))
    private fun name(c: Contact) = if (c.kind == ContactKind.ORG) c.company else "${c.firstName} ${c.lastName}"
    private fun search(d: String, vararg cs: Contact) = T9.search(cs.toList(), d, ::name, homeCc = "43").map { it.contactId }

    @Test fun lettersSitOnTheirKeysUmlautsOnTheBaseLetter() {
        assertEquals('2', T9.digitOf('ä'))
        assertEquals('6', T9.digitOf('Ö'))
        assertEquals('8', T9.digitOf('ü'))
        assertEquals('7', T9.digitOf('ß'))
        assertEquals('3', T9.digitOf('é'))
        assertEquals('5', T9.digitOf('ł'))
        assertEquals('6', T9.digitOf('ø'))
        assertNull(T9.digitOf(' '))
    }

    @Test fun firstNameLastNameAndRunningIntoTheNextWord() {
        assertEquals(listOf("anna"), search("2662", anna, bea)) // Anna
        assertEquals(listOf("anna"), search("237", anna, bea)) // Ber…
        assertEquals(listOf("anna"), search("266223", anna)) // annabe
        assertEquals(listOf("j"), search("5874", juergen)) // Jürg…
        assertEquals(listOf("j"), search("6553", juergen)) // Öller
    }

    @Test fun firstWordBeatsOtherWordsBeatsNumbers() {
        // 23: "Be(a)" first word, "Be(rger)" second word.
        assertEquals(listOf("bea", "anna"), search("23", anna, bea))
        // 66: nobody's name, Anna's number has „66“.
        assertEquals(listOf("anna"), search("660", anna, bea))
    }

    @Test fun initials() {
        val hit = T9.search(listOf(anna), "22", ::name).single()
        assertEquals(T9.How.INITIALS, hit.how)
        assertEquals(listOf(0, 5), hit.nameMarks)
    }

    @Test fun numberInStoredOrNationalForm() {
        val stored = T9.search(listOf(anna), "1234", ::name).single()
        assertEquals(T9.How.NUMBER, stored.how)
        assertEquals("1234", stored.numberMarks.map { "+43 660 1234567"[it] }.joinToString(""))
        val national = T9.search(listOf(anna), "06601", ::name, homeCc = T9.callingCode("at")).single()
        assertEquals("6601", national.numberMarks.map { "+43 660 1234567"[it] }.joinToString(""))
        assertEquals(listOf("bea"), search("0699", anna, bea))
    }

    @Test fun nationalTypingOnlyStandsForTheHomeCountry() {
        val vienna = person("w", "Wien", "Amt", "+43 1 6000")
        val munich = person("m", "Max", "Mustermann", "+49 89 123456")
        // „03…“ is a region code, not „+43“ followed by 3; Graz is not Vienna.
        assertEquals(emptyList<String>(), search("03", anna, vienna))
        assertEquals(emptyList<String>(), search("0316", vienna))
        // A German number typed the German way only works with the German home code.
        assertEquals(emptyList<String>(), search("089", munich))
        assertEquals(listOf("m"), T9.search(listOf(munich), "089", ::name, homeCc = "49").map { it.contactId })
        // Without a known home code there is no national matching at all.
        assertTrue(T9.search(listOf(anna), "0660", ::name).isEmpty())
    }

    @Test fun organisationsAreDialable() {
        assertEquals(listOf("arzt"), search("2798", arzt, anna)) // Arzt
        assertEquals(listOf("arzt"), search("685", arzt, anna)) // Mül…
    }

    @Test fun neverArchivedDeletedMeOrWithoutNumber() {
        val gone = person("x", "Anna", "Alt", "+43 1 222", archived = true)
        val trashed = person("t", "Anna", "Weg", "+43 1 333").copy(deletedAt = 5L)
        val me = person("me", "Anna", "Ich", "+43 1 444").copy(isMe = true)
        val noNumber = person("n", "Anna", "Ohne")
        assertEquals(listOf("anna"), search("2662", gone, trashed, me, noNumber, anna))
    }

    @Test fun favouritesThenRecentWithinTheSameKind() {
        val a = person("a", "Anna", "Berger", "+43 1 1")
        val b = person("b", "Anna", "Huber", "+43 1 2", favorite = true)
        val c = person("c", "Anna", "Kern", "+43 1 3")
        val last = mapOf("a" to 10L, "c" to 20L)
        val ids = T9.search(listOf(a, b, c), "2662", ::name, lastAt = { last[it.id] }).map { it.contactId }
        assertEquals(listOf("b", "c", "a"), ids)
    }

    @Test fun dialsTheDefaultNumber() {
        val two = Contact(
            id = "two", firstName = "Tom", lastName = "Huber",
            phones = listOf(LabeledValue("Privat", "+43 1 111"), LabeledValue("Mobil", "+43 664 222")),
            defaultPhone = 1,
        )
        assertEquals(1, T9.search(listOf(two), "866", ::name).single().phoneIndex)
        // A number hit dials the number that matched.
        assertEquals(0, T9.search(listOf(two), "111", ::name).single().phoneIndex)
    }

    @Test fun emptyOrNonDigitInputFindsNothing() {
        assertTrue(T9.search(listOf(anna), "", ::name).isEmpty())
        assertTrue(T9.search(listOf(anna), "*#", ::name).isEmpty())
    }
}
