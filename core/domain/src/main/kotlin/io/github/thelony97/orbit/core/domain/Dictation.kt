package io.github.thelony97.orbit.core.domain

/**
 * Diktier-Ansicht (PLAN 9 B7, A9): a number read out on the phone – big blocks, letters with the
 * Austrian spelling alphabet (ÖNORM A 1081: „Anton, Berta, Cäsar …“, Z = Zürich).
 */
object Dictation {

    private val words = mapOf(
        'A' to "Anton", 'Ä' to "Ärger", 'B' to "Berta", 'C' to "Cäsar", 'D' to "Dora", 'E' to "Emil",
        'F' to "Friedrich", 'G' to "Gustav", 'H' to "Heinrich", 'I' to "Ida", 'J' to "Julius",
        'K' to "Konrad", 'L' to "Ludwig", 'M' to "Martha", 'N' to "Nordpol", 'O' to "Otto",
        'Ö' to "Österreich", 'P' to "Paula", 'Q' to "Quelle", 'R' to "Richard", 'S' to "Siegfried",
        'T' to "Theodor", 'U' to "Ulrich", 'Ü' to "Übel", 'V' to "Viktor", 'W' to "Wilhelm",
        'X' to "Xaver", 'Y' to "Ypsilon", 'Z' to "Zürich", 'ß' to "scharfes S",
    )

    /** „Anton“ for a or A; null for digits and signs. */
    fun word(ch: Char): String? = if (ch == 'ß') words['ß'] else words[ch.uppercaseChar()]

    /**
     * Blocks to read out: the value's own grouping (spaces, dashes, slashes) is kept, longer
     * pieces are cut into fours – „AT61 1904 3002“ stays, „1234567890“ → 1234 · 5678 · 90.
     */
    fun blocks(value: String): List<String> =
        value.trim().split(Regex("[\\s\\-/.]+")).filter { it.isNotEmpty() }
            .flatMap { piece -> if (piece.length > 5) piece.chunked(4) else listOf(piece) }

    /** The letters of [value] with their spelling words, in order („K – Konrad“). */
    fun spelled(value: String): List<Pair<Char, String>> =
        value.mapNotNull { ch -> word(ch)?.let { ch.uppercaseChar() to it } }
}
