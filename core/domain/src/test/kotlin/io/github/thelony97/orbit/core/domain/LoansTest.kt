package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.CustomField
import io.github.thelony97.orbit.core.model.Loan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LoansTest {
    private val anna = Contact(
        "anna",
        loans = listOf(
            Loan("1", "Pizza", 2_500, toThem = true, since = "2026-10-01"),
            Loan("2", "Kino", 1_200, toThem = false, since = "2026-10-10"),
            Loan("3", "Bohrmaschine", toThem = true, since = "2026-08-01"),
            Loan("4", "Alt", 9_900, toThem = true, since = "2026-01-01", settledOn = "2026-02-01"),
        ),
    )

    @Test fun balanceCountsOpenMoneyOnly() = assertEquals(1_300L, Loans.balance(anna))

    @Test fun sinceWords() {
        val today = LocalDate.of(2026, 10, 15)
        assertEquals("seit 2 Wo", Loans.since(anna.loans[0], today))
        assertEquals("seit 5 T", Loans.since(anna.loans[1], today))
        assertEquals("seit 2 M", Loans.since(anna.loans[2], today))
    }

    @Test fun receipts() {
        assertEquals("Bezahlt bekommen: 25 € · Pizza", Loans.receipt(anna.loans[0]))
        assertEquals("Zurückbekommen: Bohrmaschine", Loans.receipt(anna.loans[2]))
    }

    @Test fun ibanFromOwnCard() {
        val me = Contact("me", isMe = true, fields = listOf(CustomField("f", "IBAN", "AT61 1904 3002 3457 3201")))
        assertEquals("AT611904300234573201", Loans.iban(me))
        assertNull(Loans.iban(Contact("x")))
    }

    @Test fun epcPayload() {
        val p = Loans.epc("Jakob Lonauer", "AT611904300234573201", 2_550, "Pizza")!!
        assertEquals(listOf("BCD", "002", "1", "SCT", "", "Jakob Lonauer", "AT611904300234573201", "EUR25.50", "", "", "Pizza"), p.split("\n"))
        assertNull(Loans.epc("J", "AT61", 0, ""))
    }
}
