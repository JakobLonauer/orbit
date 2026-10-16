package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.Contact

/**
 * Tote Daten für Mails (PLAN 9 E14) – feste Regeln, nichts geraten:
 * - Eine Rückläufer-Mail kommt von `mailer-daemon@…`/`postmaster@…` oder ihr Betreff beginnt mit
 *   einer bekannten Rückläufer-Formel („Undeliverable:“, „Unzustellbar:“ …). Verzögerungs-Meldungen
 *   zählen nie.
 * - Die gescheiterte Adresse steht im Zustellbericht (`Final-Recipient`/`Original-Recipient` mit
 *   `Action: failed`); ohne Zustellbericht nur Adressen in Zeilen, die das Scheitern nennen.
 * - Eine Adresse ist tot ab dem Rückläufer; eine neuere Mail von ihr (auch in einem späteren Abgleich)
 *   macht sie wieder lebendig.
 */
object Bounces {

    private val daemons = listOf("mailer-daemon", "postmaster", "mail-daemon", "maildaemon")

    private val subjects = listOf(
        "undeliverable", "undelivered mail", "delivery status notification (failure)", "mail delivery failed",
        "mail delivery failure", "delivery failure", "failure notice", "returned mail", "unzustellbar", "nicht zustellbar",
        "zustellung fehlgeschlagen", "fehler bei der zustellung", "mail delivery system",
    )

    private val delays = listOf("delay", "verzögert", "verzoegert", "warning:", "warnung:", "relayed", "delivered:", "zugestellt:", "gelesen:", "read:")

    private val failureWords = listOf(
        "wasn't delivered", "was not delivered", "could not be delivered", "couldn't be delivered", "undeliverable",
        "delivery has failed", "failed permanently", "user unknown", "no such user", "does not exist", "doesn't exist",
        "mailbox unavailable", "address rejected", "recipient address rejected", "nicht zugestellt", "konnte nicht zugestellt",
        "unzustellbar", "existiert nicht", "unbekannt",
    )

    private val address = Regex("[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val dsnRecipient = Regex("(?im)^\\s*(final|original)-recipient\\s*:\\s*rfc822\\s*;\\s*<?\\s*([^\\s<>;]+@[^\\s<>;]+?)\\s*>?\\s*$")
    private val dsnAction = Regex("(?im)^\\s*action\\s*:\\s*([a-z]+)")

    /** True for a bounce (not for a delay or a read receipt). */
    fun isBounce(from: String, subject: String): Boolean {
        val s = subject.trim().lowercase()
        if (delays.any { s.contains(it) }) return false
        val local = from.trim().lowercase().substringBefore('@')
        return daemons.any { local == it || local.startsWith("$it-") || local.endsWith("-$it") } ||
            subjects.any { s.startsWith(it) }
    }

    /**
     * The addresses that failed, lower case. With a delivery report only those with `Action: failed`
     * (or no action at all); without one, addresses on lines that name the failure.
     */
    fun failedRecipients(text: String): Set<String> {
        val hits = dsnRecipient.findAll(text).toList()
        if (hits.isNotEmpty()) {
            val out = LinkedHashSet<String>()
            val failed = HashSet<String>()
            val other = HashSet<String>()
            hits.forEachIndexed { i, m ->
                val a = m.groupValues[2].trim().trimEnd('.').lowercase()
                val end = hits.getOrNull(i + 1)?.range?.first ?: text.length
                val action = dsnAction.find(text.substring(m.range.last + 1, end))?.groupValues?.get(1)?.lowercase()
                when (action) {
                    null, "failed" -> failed += a
                    else -> other += a
                }
                out += a
            }
            // Final- and Original-Recipient name the same person: one „delayed“ keeps it alive.
            return out.filter { it in failed && it !in other }.toSet()
        }
        return text.lineSequence()
            .filter { line -> val l = line.lowercase(); failureWords.any { l.contains(it) } }
            .flatMap { line -> address.findAll(line).map { it.value.trimEnd('.').lowercase() } }
            .filterNot { a -> daemons.any { a.startsWith("$it@") } }
            .toSet()
    }

    /**
     * The contact with its dead mail addresses brought up to date: [failed] and [heard] map a lower-case
     * address to the time of the last bounce resp. the last mail from it. Unchanged contacts come back as is.
     */
    fun apply(c: Contact, failed: Map<String, Long>, heard: Map<String, Long>): Contact {
        if (failed.isEmpty() && heard.isEmpty()) return c
        var dead = c.dead
        for (e in c.emails) {
            val k = e.value.trim().lowercase()
            val f = failed[k]
            val h = heard[k]
            if (f != null && (h == null || f > h)) {
                if (e.value !in dead) dead = dead + e.value
            } else if (h != null && e.value in dead) {
                dead = dead - e.value
            }
        }
        return if (dead == c.dead) c else c.copy(dead = dead)
    }
}
