package io.github.thelony97.orbit.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupFormatTest {

    @Test fun oldBackupIsConvertedWithItsHistory() {
        val v1 = """{"schema":1,"contacts":[{"id":"a","firstName":"Anna","birthday":{"day":1,"month":2},
            "entries":[{"id":"e1","typeId":"call","at":5}],"gallery":[{"id":"g","file":"g.jpg"}]}]}"""
        val d = Backups.fromBackup(v1, null, null)
        val a = d.contacts.single { it.id == "a" }
        assertEquals(listOf("e1"), a.entries.map { it.id })
        assertEquals("a", a.entries.single().contactId)
        assertEquals("g.jpg", a.gallery.single().file)
        assertEquals(1, a.birthday?.day)
    }

    @Test fun newBackupNeedsItsHistoryFile() {
        val v2 = """{"schema":2,"contacts":[{"id":"a"}]}"""
        assertThrows(IllegalArgumentException::class.java) { Backups.fromBackup(v2, null, null) }
        val h = """[{"id":"e1","contactId":"a","typeId":"call","at":5},{"id":"e2","contactId":"a","typeId":"call","at":9}]"""
        val d = Backups.fromBackup(v2, h, "[]")
        assertEquals(listOf("e2", "e1"), d.contacts.single().entries.map { it.id })
    }
}
