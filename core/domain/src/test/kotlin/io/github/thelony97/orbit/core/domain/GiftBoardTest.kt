package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.CheckItem
import io.github.thelony97.orbit.core.model.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GiftBoardTest {
    private val anna = Contact(
        "a", firstName = "Anna", giftBudgetCents = 6_000,
        gifts = listOf(
            CheckItem("1", "Buch", priceCents = 2_500),
            CheckItem("2", "Schal", bought = true, priceCents = 4_000),
            CheckItem("3", "Kerze", done = true, bought = true, priceCents = 2_500),
        ),
    )

    @Test fun columns() {
        val cols = GiftBoard.columns(listOf(anna))
        assertEquals(listOf("Buch"), cols.getValue(GiftBoard.Stage.IDEA).map { it.gift.text })
        assertEquals(listOf("Schal"), cols.getValue(GiftBoard.Stage.BOUGHT).map { it.gift.text })
        assertEquals(listOf("Kerze"), cols.getValue(GiftBoard.Stage.GIVEN).map { it.gift.text })
    }

    @Test fun moveAndBudget() {
        assertEquals(GiftBoard.Stage.BOUGHT, GiftBoard.stageOf(GiftBoard.move(anna.gifts[0], GiftBoard.Stage.BOUGHT)))
        assertEquals(GiftBoard.Stage.IDEA, GiftBoard.stageOf(GiftBoard.move(anna.gifts[2], GiftBoard.Stage.IDEA)))
        val (line, over) = GiftBoard.budgetLine(anna)!!
        assertEquals("65 / 60 €", line)
        assertTrue(over)
    }
}
