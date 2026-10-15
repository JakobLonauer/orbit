package io.github.thelony97.orbit.core.domain

/**
 * Rückruf-Stapel (PLAN 9 B3): who is waiting for a call back – missed calls without any later
 * contact with the number, „Später zurückrufen“, and voice-mail SMS – one card per number, newest
 * first. Fixed rules over the phone's call log and SMS; nothing is guessed.
 */
object CallbackStack {

    enum class CallType { MISSED, REJECTED, INCOMING, OUTGOING }

    /** [account]: the SIM it came in on (PLAN 9 B9: the call back goes out on it). */
    data class Call(val number: String, val at: Long, val type: CallType, val account: String? = null)

    data class Sms(val address: String, val at: Long, val sent: Boolean, val body: String)

    /** „Später zurückrufen“ chosen at [at] for [number]. */
    data class Later(val number: String, val at: Long)

    enum class Source { MISSED, LATER, MAILBOX }

    data class Card(
        /** Stable per number and newest event: „erledigt“ is stored under it. */
        val key: String,
        val number: String,
        val source: Source,
        /** How many missed calls (1 for the others). */
        val count: Int,
        val lastAt: Long,
        /** The SIM the newest missed call came in on. */
        val account: String? = null,
    )

    const val DAYS = 7
    private const val DAY = 86_400_000L

    /**
     * The open call-backs at [now]. [key] normalises numbers (E.164); [done] holds the keys of cards
     * Jakob swiped away.
     */
    fun build(
        calls: List<Call>,
        sms: List<Sms>,
        later: List<Later>,
        done: Set<String>,
        now: Long,
        key: (String) -> String?,
    ): List<Card> {
        val since = now - DAYS * DAY
        val open = Opens(since, lastTouch(calls, sms, key), key)
        // „Später“ first: such a card stays a „Später“ card even when more calls were missed.
        later.forEach { open.add(it.number, it.at, Source.LATER) }
        calls.filter { it.type == CallType.MISSED }.sortedBy { it.at }.forEach { open.add(it.number, it.at, Source.MISSED, it.account) }
        sms.filter { !it.sent }.forEach { s -> mailboxNumber(s.body)?.let { open.add(it, s.at, Source.MAILBOX) } }
        return open.cards().filter { it.key !in done }.sortedByDescending { it.lastAt }
    }

    /** The last time Jakob was in touch with each number: answered or own calls, sent SMS. */
    private fun lastTouch(calls: List<Call>, sms: List<Sms>, key: (String) -> String?): Map<String, Long> {
        val touched = HashMap<String, Long>()
        val events = calls.filter { it.type == CallType.OUTGOING || it.type == CallType.INCOMING }.map { it.number to it.at } +
            sms.filter { it.sent }.map { it.address to it.at }
        events.forEach { (n, at) ->
            val k = key(n) ?: return@forEach
            touched[k] = maxOf(at, touched[k] ?: Long.MIN_VALUE)
        }
        return touched
    }

    /** The cards being collected: one per number, events before the last contact don't count. */
    private class Opens(private val since: Long, private val touched: Map<String, Long>, private val key: (String) -> String?) {
        private class Open(val number: String, val source: Source, var count: Int, var lastAt: Long, var account: String?)

        private val open = LinkedHashMap<String, Open>()

        fun add(number: String, at: Long, source: Source, account: String? = null) {
            val k = key(number) ?: return
            if (at < since || at <= (touched[k] ?: Long.MIN_VALUE)) return
            val o = open[k]
            if (o == null) {
                open[k] = Open(number, source, 1, at, account)
                return
            }
            if (source == Source.MISSED && o.source == Source.MISSED) o.count++
            if (at > o.lastAt) o.lastAt = at
            if (account != null) o.account = account
        }

        fun cards(): List<Card> = open.map { (k, o) -> Card("cb:$k:${o.lastAt}", o.number, o.source, o.count, o.lastAt, o.account) }
    }

    private val mailboxWords = Regex("""(?i)\b(mobilbox|mailbox|sprachnachricht|sprachbox|voicemail|nachricht hinterlassen)""")
    private val numberIn = Regex("""(?:\+|00|0)\d[\d /\-]{5,18}\d""")

    /** The caller's number in a voice-mail SMS („Neue Sprachnachricht von 0664 1234567“); null otherwise. */
    fun mailboxNumber(body: String): String? {
        if (!mailboxWords.containsMatchIn(body)) return null
        return numberIn.find(body)?.value?.trim()?.takeIf { v -> v.count(Char::isDigit) >= 7 }
    }
}
