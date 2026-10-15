package io.github.thelony97.orbit.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class RedialTest {

    @Test fun busyAndAnnouncementsDialAgain() {
        assertEquals(Redial.Next.AGAIN, Redial.next(Redial.End.BUSY, 0, 1))
        // „Alle Leitungen besetzt“: answered, hung up after 12 s.
        assertEquals(Redial.Next.AGAIN, Redial.next(Redial.End.REMOTE, 12_000, 3))
        // Nobody answered at all.
        assertEquals(Redial.Next.AGAIN, Redial.next(Redial.End.OTHER, 0, 3))
    }

    @Test fun talkingOrJakobEndsIt() {
        assertEquals(Redial.Next.DONE, Redial.next(Redial.End.REMOTE, 95_000, 7))
        assertEquals(Redial.Next.STOP, Redial.next(Redial.End.LOCAL, 3_000, 2))
        assertEquals(Redial.Next.STOP, Redial.next(Redial.End.ERROR, 0, 1))
    }

    @Test fun thirtyTriesAtMost() {
        assertEquals(Redial.Next.AGAIN, Redial.next(Redial.End.BUSY, 0, 29))
        assertEquals(Redial.Next.GIVE_UP, Redial.next(Redial.End.BUSY, 0, 30))
    }

    @Test fun offersAreTheNextHalfHours() {
        assertEquals(
            listOf(LocalDateTime.of(2026, 10, 15, 7, 30), LocalDateTime.of(2026, 10, 15, 8, 0)),
            Redial.offers(LocalDateTime.of(2026, 10, 15, 7, 12, 40)),
        )
        assertEquals(
            listOf(LocalDateTime.of(2026, 10, 15, 8, 0), LocalDateTime.of(2026, 10, 15, 8, 30)),
            Redial.offers(LocalDateTime.of(2026, 10, 15, 7, 26)),
        )
        assertEquals(
            listOf(LocalDateTime.of(2026, 10, 16, 0, 0), LocalDateTime.of(2026, 10, 16, 0, 30)),
            Redial.offers(LocalDateTime.of(2026, 10, 15, 23, 50)),
        )
    }
}
