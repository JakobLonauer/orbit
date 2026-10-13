package io.github.thelony97.orbit.ui.detail

import io.github.thelony97.orbit.data.Contact
import io.github.thelony97.orbit.data.Entry
import io.github.thelony97.orbit.data.TypeIds
import org.junit.Assert.assertEquals
import org.junit.Test

class LastPerTypeTest {
    @Test fun emptyContactShowsDefaultKinds() {
        assertEquals(
            listOf(TypeIds.MEET to null, TypeIds.CALL to null, TypeIds.MESSAGE to null),
            lastPerType(Contact("a")),
        )
    }

    @Test fun fixedOrderWithUsedKindsAndFill() {
        val c = Contact(
            "a",
            entries = listOf(
                Entry("1", "a", "coffee", at = 50),
                Entry("2", "a", TypeIds.CALL, at = 10),
                Entry("3", "a", TypeIds.CALL, at = 40),
            ),
        )
        assertEquals(
            listOf(TypeIds.MEET to null, TypeIds.CALL to 40L, "coffee" to 50L),
            lastPerType(c),
        )
    }
}
