package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact
import io.github.thelony97.orbit.core.model.LabeledValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BouncesTest {

    @Test fun bounceSenders() {
        val cases = listOf(
            Triple("MAILER-DAEMON@mail.gmx.net", "Mail delivery failed: returning message to sender", true),
            Triple("mailer-daemon@googlemail.com", "Delivery Status Notification (Failure)", true),
            Triple("postmaster@firma.onmicrosoft.com", "Unzustellbar: Termin", true),
            Triple("noreply@shop.at", "Undeliverable: Ihre Bestellung", true),
            Triple("mailer-daemon@googlemail.com", "Delivery Status Notification (Delay)", false),
            Triple("postmaster@firma.at", "Zugestellt: Termin", false),
            Triple("postmaster@firma.at", "Warning: could not send message for past 4 hours", false),
            Triple("anna@huber.at", "Re: Kaffee?", false),
            Triple("anna@huber.at", "Der unzustellbare Brief", false),
        )
        for ((from, subject, expected) in cases) assertEquals("$from / $subject", expected, Bounces.isBounce(from, subject))
    }

    @Test fun deliveryReportFailed() {
        val dsn = """
            Reporting-MTA: dns; mx.google.com

            Final-Recipient: rfc822; Anna@Huber.at
            Action: failed
            Status: 5.1.1

            Final-Recipient: rfc822; <tom@berger.at>
            Action: delayed
            Status: 4.4.1
        """.trimIndent()
        assertEquals(setOf("anna@huber.at"), Bounces.failedRecipients(dsn))
    }

    @Test fun originalAndFinalNameTheSamePerson() {
        val dsn = "Original-Recipient: rfc822;anna@huber.at\nFinal-Recipient: rfc822;anna@huber.at\nAction: failed\n"
        assertEquals(setOf("anna@huber.at"), Bounces.failedRecipients(dsn))
    }

    @Test fun withoutReportOnlyFailureLines() {
        val text = """
            Your message wasn't delivered to anna@huber.at because the address couldn't be found.
            Von: jakob@lonauer.at
            An: anna@huber.at; tom@berger.at
        """.trimIndent()
        assertEquals(setOf("anna@huber.at"), Bounces.failedRecipients(text))
        assertTrue(Bounces.failedRecipients("Hallo Tom, schöne Grüße an tom@berger.at").isEmpty())
    }

    @Test fun deadUntilHeardFromAgain() {
        val c = Contact("a", emails = listOf(LabeledValue("", "Anna@Huber.at"), LabeledValue("", "anna@uni.at")))
        val dead = Bounces.apply(c, failed = mapOf("anna@huber.at" to 100L), heard = emptyMap())
        assertEquals(listOf("Anna@Huber.at"), dead.dead)
        // A mail from her in a later batch brings it back.
        assertTrue(Bounces.apply(dead, failed = emptyMap(), heard = mapOf("anna@huber.at" to 150L)).dead.isEmpty())
        // Bounce newer than her last mail in the same batch: dead.
        assertEquals(listOf("Anna@Huber.at"), Bounces.apply(c, mapOf("anna@huber.at" to 300L), mapOf("anna@huber.at" to 200L)).dead)
        // Bounce and later answer in the same batch: alive.
        assertTrue(Bounces.apply(c, failed = mapOf("anna@huber.at" to 100L), heard = mapOf("anna@huber.at" to 200L)).dead.isEmpty())
        // Numbers marked by a failed SMS stay untouched.
        val withNumber = c.copy(dead = listOf("0664 1"))
        assertEquals(listOf("0664 1", "anna@uni.at"), Bounces.apply(withNumber, mapOf("anna@uni.at" to 1L), emptyMap()).dead)
        assertSame(c, Bounces.apply(c, emptyMap(), emptyMap()))
        assertSame(c, Bounces.apply(c, emptyMap(), mapOf("x@y.at" to 1L)))
    }
}
