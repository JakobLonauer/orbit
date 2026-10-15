package io.github.thelony97.orbit.core.domain

/**
 * Kölner Phonetik (PLAN 9 A11): German names that sound alike get the same code –
 * Maier, Meier, Mayer and Mayr are all "67", Höller and Hoeller both "057". Fixed rules, no
 * guessing; the search uses it only behind the exact hits.
 */
object Phonetic {

    /** Code of one word ("Müller-Lüdenscheidt" → "65752682"); empty for words without letters. */
    fun code(word: String): String {
        val s = Fold.fold(word).filter { it in 'a'..'z' }
        if (s.isEmpty()) return ""
        val raw = StringBuilder()
        for (i in s.indices) {
            raw.append(letter(s[i], if (i > 0) s[i - 1] else ' ', if (i + 1 < s.length) s[i + 1] else ' ', i == 0))
        }
        // Neighbouring equal digits collapse; a zero only counts at the very start.
        val collapsed = StringBuilder()
        for (ch in raw) if (collapsed.isEmpty() || collapsed.last() != ch) collapsed.append(ch)
        val out = StringBuilder()
        collapsed.forEachIndexed { i, ch -> if (ch != '0' || i == 0) out.append(ch) }
        return out.toString()
    }

    /** The published letter table of the Kölner Phonetik – one branch per rule, on purpose. */
    @Suppress("CyclomaticComplexMethod")
    private fun letter(c: Char, prev: Char, next: Char, first: Boolean): String = when (c) {
        'a', 'e', 'i', 'j', 'o', 'u', 'y' -> "0"
        'h' -> ""
        'b' -> "1"
        'p' -> if (next == 'h') "3" else "1"
        'd', 't' -> if (next == 'c' || next == 's' || next == 'z') "8" else "2"
        'f', 'v', 'w' -> "3"
        'g', 'k', 'q' -> "4"
        'c' -> when {
            first -> if (next in "ahkloqrux") "4" else "8"
            prev == 's' || prev == 'z' -> "8"
            next in "ahkoqux" -> "4"
            else -> "8"
        }
        'x' -> if (prev == 'c' || prev == 'k' || prev == 'q') "8" else "48"
        'l' -> "5"
        'm', 'n' -> "6"
        'r' -> "7"
        's', 'z' -> "8"
        else -> ""
    }

    private val separators = Regex("[\\s\\-/,.'\"„“”()]+")

    /** Codes of every word of a name ("Anna-Lena Müller" → three codes). */
    fun codes(text: String): Set<String> =
        text.split(separators).mapNotNullTo(LinkedHashSet()) { w -> code(w).takeIf { it.isNotEmpty() } }

    /**
     * Does the typed [word] sound like one of [codes]? Only words of four or more letters take
     * part (shorter ones would match half the list); from three digits on, a code may also be
     * the start of a longer one ("Lüdensch" finds Lüdenscheidt).
     */
    fun matches(word: String, codes: Set<String>): Boolean {
        if (word.count { it.isLetter() } < 4) return false
        val q = code(word)
        if (q.isEmpty()) return false
        return codes.any { it == q || (q.length >= 3 && it.startsWith(q)) }
    }
}
