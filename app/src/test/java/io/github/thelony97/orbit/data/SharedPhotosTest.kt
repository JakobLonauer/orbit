package io.github.thelony97.orbit.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** PLAN 3.10: one picture, several contacts – the file goes only with its last reference. */
class SharedPhotosTest {
    private val pic = GalleryPhoto(id = "g1", contactId = "anna", file = "g-1.jpg", addedAt = 1)
    private val doc = OrbitData(contacts = listOf(
        Contact(id = "anna", gallery = listOf(pic), photo = "p-anna.jpg"),
        Contact(id = "tom"),
    ))

    @Test fun sharedPictureSurvivesPurgeOfOneOwner() {
        val shared = Mutations.shareAttachment(doc, pic, "tom", at = 5)
        val tom = shared.contacts.first { it.id == "tom" }
        assertEquals(listOf("g-1.jpg"), tom.gallery.map { it.file })
        assertEquals("tom", tom.gallery.single().contactId)
        // Twice is once.
        assertEquals(shared, Mutations.shareAttachment(shared, pic, "tom", at = 6))
        val purged = Mutations.purge(shared, "anna")
        assertEquals(listOf("p-anna.jpg"), Mutations.unreferenced(purged, listOf("g-1.jpg", "p-anna.jpg")))
        assertEquals(listOf("g-1.jpg", "p-anna.jpg"), Mutations.unreferenced(Mutations.purge(doc, "anna"), listOf("g-1.jpg", "p-anna.jpg")))
    }

    @Test fun oldAttachmentJsonReadsWithoutTheNewFields() {
        val a = io.github.thelony97.orbit.core.domain.DocJson.json.decodeFromString(
            io.github.thelony97.orbit.core.model.Attachment.serializer(),
            """{"id":"g1","contactId":"anna","file":"g-1.jpg","addedAt":3}""",
        )
        assertEquals(null, a.takenAt)
        assertEquals("image/jpeg", a.mime)
        assertEquals("", a.name)
    }
}
