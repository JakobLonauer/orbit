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

    @Test fun suggestPeopleByNextBirthdayThenIdeas() {
        val today = java.time.LocalDate.of(2026, 12, 20)
        val cs = listOf(
            io.github.thelony97.orbit.core.model.Contact("zoe", firstName = "Zoe"),
            io.github.thelony97.orbit.core.model.Contact("ida", firstName = "Ida", gifts = listOf(CheckItem("g", "Buch"))),
            io.github.thelony97.orbit.core.model.Contact(
                "jan", firstName = "Jan",
                dates = listOf(io.github.thelony97.orbit.core.model.Occasion("b", io.github.thelony97.orbit.core.model.OccasionKind.BIRTHDAY, date = io.github.thelony97.orbit.core.model.CalendarDay(3, 1))),
            ),
            io.github.thelony97.orbit.core.model.Contact(
                "eva", firstName = "Eva",
                dates = listOf(io.github.thelony97.orbit.core.model.Occasion("b", io.github.thelony97.orbit.core.model.OccasionKind.BIRTHDAY, date = io.github.thelony97.orbit.core.model.CalendarDay(24, 12))),
            ),
            io.github.thelony97.orbit.core.model.Contact("me", isMe = true, firstName = "Jakob"),
            io.github.thelony97.orbit.core.model.Contact("alt", firstName = "Alt", archived = true),
        )
        // Over the new year: 24.12. before 3.1.; then ideas, then the name.
        org.junit.Assert.assertEquals(listOf("eva", "jan", "ida", "zoe"), GiftBoard.suggestPeople(cs, today).map { it.id })
        org.junit.Assert.assertEquals(listOf("eva", "jan"), GiftBoard.suggestPeople(cs, today, max = 2).map { it.id })
    }
}
