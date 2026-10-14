package io.github.thelony97.orbit.data

import io.github.thelony97.orbit.core.model.OccasionKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** Saving an occasion from the dialog (PLAN 3.5): replace in place, add new, one birthday only. */
class OccasionEditTest {
    private val bd = Anniversary("bd", OccasionKind.BIRTHDAY, date = DayOfYear(14, 10, 1996))
    private val wed = Anniversary("wed", OccasionKind.WEDDING, date = DayOfYear(16, 10, 2021))
    private val doc = OrbitData(contacts = listOf(Contact(id = "anna", firstName = "Anna", dates = listOf(bd, wed)), Contact(id = "tom", firstName = "Tom")))

    @Test fun editKeepsPlaceAndOthers() {
        val d = Mutations.upsertOccasion(doc, "anna", bd.copy(date = DayOfYear(15, 10, 1996)))
        assertEquals(listOf("bd", "wed"), d.contacts.first().dates.map { it.id })
        assertEquals(15, d.contacts.first().dates.first().date.day)
        assertEquals(doc.contacts[1], d.contacts[1])
    }

    @Test fun newOneIsAddedSharedStoredOnce() {
        val met = Anniversary("met", OccasionKind.MET, date = DayOfYear(1, 5, 2019), sharedWith = "tom")
        val d = Mutations.upsertOccasion(doc, "anna", met)
        assertEquals(listOf("bd", "wed", "met"), d.contacts.first().dates.map { it.id })
        assertEquals(emptyList<Anniversary>(), d.contacts[1].dates)
    }

    @Test fun aSecondBirthdayReplacesTheFirst() {
        val other = Anniversary("bd2", OccasionKind.BIRTHDAY, date = DayOfYear(1, 1, 1990))
        val d = Mutations.upsertOccasion(doc, "anna", other)
        assertEquals(listOf("wed", "bd2"), d.contacts.first().dates.map { it.id })
    }
}
