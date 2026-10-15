package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.domain.CallbackStack.Call
import io.github.thelony97.orbit.core.domain.CallbackStack.CallType
import io.github.thelony97.orbit.core.domain.CallbackStack.Later
import io.github.thelony97.orbit.core.domain.CallbackStack.Sms
import io.github.thelony97.orbit.core.domain.CallbackStack.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallbackStackTest {
    private val now = 1_800_000_000_000L
    private val h = 3_600_000L
    private val key: (String) -> String? = { raw ->
        val d = raw.filter { it.isDigit() }
        when {
            d.length < 3 -> null
            raw.trim().startsWith("+") -> "+$d"
            d.startsWith("0") -> "+43" + d.drop(1)
            else -> d
        }
    }

    private fun build(calls: List<Call> = emptyList(), sms: List<Sms> = emptyList(), later: List<Later> = emptyList(), done: Set<String> = emptySet()) =
        CallbackStack.build(calls, sms, later, done, now, key)

    @Test fun missedCallsGroupPerNumberNewestFirst() {
        val cards = build(
            calls = listOf(
                Call("0664 1111111", now - 5 * h, CallType.MISSED),
                Call("+43 664 1111111", now - 2 * h, CallType.MISSED),
                Call("0732 555123", now - 3 * h, CallType.MISSED),
            ),
        )
        assertEquals(2, cards.size)
        assertEquals(2, cards[0].count)
        assertEquals(now - 2 * h, cards[0].lastAt)
        assertEquals(Source.MISSED, cards[1].source)
    }

    @Test fun anyLaterContactClosesIt() {
        val cards = build(
            calls = listOf(
                Call("0664 1111111", now - 5 * h, CallType.MISSED),
                Call("0664 1111111", now - 4 * h, CallType.OUTGOING),
                Call("0732 555123", now - 3 * h, CallType.MISSED),
                Call("0650 2222222", now - 3 * h, CallType.MISSED),
            ),
            sms = listOf(Sms("+43650 2222222", now - 1 * h, sent = true, body = "Melde mich später")),
        )
        assertEquals(listOf("0732 555123"), cards.map { it.number })
    }

    @Test fun missedAgainAfterTheCallBackOpensANewCard() {
        val cards = build(
            calls = listOf(
                Call("0664 1111111", now - 5 * h, CallType.MISSED),
                Call("0664 1111111", now - 4 * h, CallType.OUTGOING),
                Call("0664 1111111", now - 1 * h, CallType.MISSED),
            ),
        )
        assertEquals(1, cards.single().count)
    }

    @Test fun laterAndMailbox() {
        val cards = build(
            later = listOf(Later("0676 3333333", now - 2 * h)),
            sms = listOf(Sms("A1", now - 1 * h, sent = false, body = "Sie haben eine neue Sprachnachricht von +43 699 4444444 erhalten.")),
        )
        assertEquals(listOf(Source.MAILBOX, Source.LATER), cards.map { it.source })
        assertEquals("+43 699 4444444", cards[0].number)
    }

    @Test fun doneAndOldOnesAreGone() {
        val first = build(calls = listOf(Call("0732 555123", now - 3 * h, CallType.MISSED))).single()
        assertTrue(build(calls = listOf(Call("0732 555123", now - 3 * h, CallType.MISSED)), done = setOf(first.key)).isEmpty())
        assertTrue(build(calls = listOf(Call("0732 555123", now - 8 * 24 * h, CallType.MISSED))).isEmpty())
    }

    @Test fun mailboxPatterns() {
        assertEquals("0664 1234567", CallbackStack.mailboxNumber("Neue Nachricht in Ihrer Mobilbox von 0664 1234567."))
        assertNull(CallbackStack.mailboxNumber("Dein Code: 123456"))
        assertNull(CallbackStack.mailboxNumber("Ruf mich an: 0664 1234567"))
    }
}
