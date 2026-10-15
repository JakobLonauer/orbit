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

    /** One block to read out and the sign that follows it („-“, „/“, „.“; null for a space or the end). */
    data class Block(val text: String, val after: Char? = null)

    private const val SIGNS = "-/.:"

    /**
     * Blocks to read out: the value's own grouping is kept – spaces split, dashes, slashes and dots
     * split and stay visible; pieces up to six signs stay whole („1234 170597“: the birth date in
     * the SV-Nr.), longer ones are cut into fours („1234567890“ → 1234 · 5678 · 90).
     */
    fun pieces(value: String): List<Block> {
        val raw = ArrayList<Block>()
        val cur = StringBuilder()
        fun end(sign: Char?) {
            if (cur.isNotEmpty()) {
                raw += Block(cur.toString(), sign)
                cur.clear()
            } else if (sign != null && raw.isNotEmpty() && raw.last().after == null) {
                // „AB -12“: the sign belongs to the block before the space.
                raw[raw.lastIndex] = raw.last().copy(after = sign)
            }
        }
        for (ch in value.trim()) {
            when {
                ch.isWhitespace() -> end(null)
                ch in SIGNS -> end(ch)
                else -> cur.append(ch)
            }
        }
        end(null)
        return raw.flatMap { b ->
            if (b.text.length <= 6) {
                listOf(b)
            } else {
                val parts = b.text.chunked(4)
                parts.mapIndexed { i, t -> Block(t, if (i == parts.lastIndex) b.after else null) }
            }
        }
    }

    /** The texts of [pieces]. */
    fun blocks(value: String): List<String> = pieces(value).map { it.text }

    /** The letters of [value] with their spelling words, in order („K – Konrad“). */
    fun spelled(value: String): List<Pair<Char, String>> =
        value.mapNotNull { ch -> word(ch)?.let { ch.uppercaseChar() to it } }
}
