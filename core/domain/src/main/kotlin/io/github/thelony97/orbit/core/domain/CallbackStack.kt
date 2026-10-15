package io.github.thelony97.orbit.core.domain

/**
 * Rückruf-Stapel (PLAN 9 B3): who is waiting for a call back – missed calls without any later
 * contact with the number, „Später zurückrufen“, and voice-mail SMS – one card per number, newest
 * first. Fixed rules over the phone's call log and SMS; nothing is guessed.
 */
object CallbackStack {

    enum class CallType { MISSED, REJECTED, INCOMING, OUTGOING }

    data class Call(val number: String, val at: Long, val type: CallType)

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
        // The last time Jakob was in touch with each number: answered or own calls, sent SMS.
        val touched = HashMap<String, Long>()
        fun touch(n: String, at: Long) {
            val k = key(n) ?: return
            if (at > (touched[k] ?: Long.MIN_VALUE)) touched[k] = at
        }
        calls.filter { it.type == CallType.OUTGOING || it.type == CallType.INCOMING }.forEach { touch(it.number, it.at) }
        sms.filter { it.sent }.forEach { touch(it.address, it.at) }

        data class Open(val number: String, val source: Source, var count: Int, var lastAt: Long)
        val open = LinkedHashMap<String, Open>()
        fun add(number: String, at: Long, source: Source) {
            val k = key(number) ?: return
            if (at < since || at <= (touched[k] ?: Long.MIN_VALUE)) return
            val o = open[k]
            when {
                o == null -> open[k] = Open(number, source, 1, at)
                else -> {
                    if (source == Source.MISSED && o.source == Source.MISSED) o.count++
                    if (at > o.lastAt) o.lastAt = at
                }
            }
        }
        // „Später“ first: such a card stays a „Später“ card even when more calls were missed.
        later.forEach { add(it.number, it.at, Source.LATER) }
        calls.filter { it.type == CallType.MISSED }.sortedBy { it.at }.forEach { add(it.number, it.at, Source.MISSED) }
        sms.filter { !it.sent }.forEach { s -> mailboxNumber(s.body)?.let { add(it, s.at, Source.MAILBOX) } }

        return open.map { (k, o) -> Card("cb:$k:${o.lastAt}", o.number, o.source, o.count, o.lastAt) }
            .filter { it.key !in done }
            .sortedByDescending { it.lastAt }
    }

    private val mailboxWords = Regex("""(?i)\b(mobilbox|mailbox|sprachnachricht|sprachbox|voicemail|nachricht hinterlassen)""")
    private val numberIn = Regex("""(?:\+|00|0)\d[\d /\-]{5,18}\d""")

    /** The caller's number in a voice-mail SMS („Neue Sprachnachricht von 0664 1234567“); null otherwise. */
    fun mailboxNumber(body: String): String? {
        if (!mailboxWords.containsMatchIn(body)) return null
        return numberIn.find(body)?.value?.trim()?.takeIf { v -> v.count(Char::isDigit) >= 7 }
    }
}
