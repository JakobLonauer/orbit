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

    @Test fun mostRecentKindsFirstThenFilled() {
        val c = Contact(
            "a",
            entries = listOf(
                Entry("1", "coffee", at = 50),
                Entry("2", TypeIds.CALL, at = 10),
                Entry("3", TypeIds.CALL, at = 40),
            ),
        )
        assertEquals(
            listOf("coffee" to 50L, TypeIds.CALL to 40L, TypeIds.MEET to null),
            lastPerType(c),
        )
    }
}
