package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.domain.StorageUsage.FileInfo
import io.github.thelony97.orbit.core.domain.StorageUsage.Part
import org.junit.Assert.assertEquals
import org.junit.Test

class StorageUsageTest {
    @Test fun classifiesByFixedRules() {
        val table = listOf(
            FileInfo("p-1.jpg", 1, inPhotos = true) to Part.PHOTOS,
            FileInfo("logo-x.png", 1, inPhotos = true) to Part.PHOTOS,
            FileInfo("d-1.pdf", 1, inPhotos = true) to Part.DOCUMENTS,
            FileInfo("d-2.PDF", 1, inPhotos = true) to Part.DOCUMENTS,
            FileInfo("orbit.db", 1) to Part.HISTORY,
            FileInfo("orbit.db-wal", 1) to Part.HISTORY,
            FileInfo("orbit.json", 1) to Part.CONTACTS,
            FileInfo("caller-index.json", 1) to Part.CONTACTS,
            FileInfo("orbit.v1.bak.json", 1) to Part.SAFETY,
            FileInfo("orbit.v2.bak.json", 1) to Part.SAFETY,
            FileInfo("orbit-broken-123.json", 1) to Part.SAFETY,
        )
        table.forEach { (f, want) -> assertEquals(f.name, want, StorageUsage.classify(f)) }
        assertEquals(null, StorageUsage.classify(FileInfo("mail-sync.json", 1)))
        // A pdf outside the attachment folder is no document of Orbit's.
        assertEquals(null, StorageUsage.classify(FileInfo("x.pdf", 1)))
    }

    @Test fun slicesAreSummedSortedAndWithoutEmptyParts() {
        val s = StorageUsage.slices(
            listOf(
                FileInfo("a.jpg", 300, true),
                FileInfo("b.jpg", 200, true),
                FileInfo("d-1.pdf", 0, true),
                FileInfo("orbit.db", 100),
                FileInfo("orbit.db-wal", 50),
                FileInfo("orbit.json", 10),
                FileInfo("other.txt", 9999),
            ),
        )
        assertEquals(listOf(Part.PHOTOS, Part.HISTORY, Part.CONTACTS), s.map { it.part })
        assertEquals(500L, s[0].bytes)
        assertEquals(2, s[0].files)
        assertEquals(150L, s[1].bytes)
    }

    @Test fun equalSizesKeepPartOrder() {
        val s = StorageUsage.slices(listOf(FileInfo("orbit.json", 5), FileInfo("a.jpg", 5, true)))
        assertEquals(listOf(Part.PHOTOS, Part.CONTACTS), s.map { it.part })
    }

    @Test fun austrianSizes() {
        assertEquals("0 B", StorageUsage.format(0))
        assertEquals("999 B", StorageUsage.format(999))
        assertEquals("900 kB", StorageUsage.format(900_000))
        assertEquals("9,8 MB", StorageUsage.format(9_800_000))
        assertEquals("1 MB", StorageUsage.format(1_000_000))
        assertEquals("182 MB", StorageUsage.format(182_000_000))
        assertEquals("1,2 GB", StorageUsage.format(1_200_000_000))
    }
}
