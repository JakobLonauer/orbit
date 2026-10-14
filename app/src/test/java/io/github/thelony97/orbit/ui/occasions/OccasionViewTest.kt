package io.github.thelony97.orbit.ui.occasions

import io.github.thelony97.orbit.core.domain.OccasionList
import io.github.thelony97.orbit.core.model.OccasionKind
import org.junit.Assert.assertEquals
import org.junit.Test

class OccasionViewTest {
    @Test fun badgeCountsEachFilterOnceAndSortingOnce() {
        assertEquals(0, OccasionView().activeCount())
        val v = OccasionView(
            kinds = setOf(OccasionKind.WEDDING, OccasionKind.CUSTOM), groupId = "g", withinDays = 7,
            onlyRound = true, orgs = true, sort = OccasionList.Sort.NAME, reversed = true,
        )
        assertEquals(6, v.activeCount())
        assertEquals(1, OccasionView(reversed = true).activeCount())
    }

    @Test fun pinnedReiterKeepsTheSameFilter() {
        val v = OccasionView(kinds = setOf(OccasionKind.WEDDING), withinDays = 30)
        assertEquals(OccasionList.Filter(setOf(OccasionKind.WEDDING), null, 30, false, pinnedOnly = true), v.filter(pinnedOnly = true))
    }

    /** rememberSaveable keeps it through rotation via java.io.Serializable. */
    @Test fun survivesSerialization() {
        val v = OccasionView(kinds = setOf(OccasionKind.WEDDING), groupId = "g", sort = OccasionList.Sort.YEARS)
        val bytes = java.io.ByteArrayOutputStream().also { java.io.ObjectOutputStream(it).use { o -> o.writeObject(v) } }.toByteArray()
        val back = java.io.ObjectInputStream(bytes.inputStream()).use { it.readObject() }
        assertEquals(v, back)
    }
}
