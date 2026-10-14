package io.github.thelony97.orbit.core.domain

/**
 * Fixed, visible rule (PLAN 2.1) for W5 „nur zeigen, was zutrifft“: SMS and WhatsApp are offered
 * unless a number is a known landline. Known = the national mobile ranges of AT, DE and CH;
 * every other country counts as „maybe mobile“ (better one orb too many than a missing one).
 */
object NumberKind {
    /** [e164] like „+437322468“; anything that is not E.164 is never called a landline. */
    fun isLandline(e164: String?): Boolean {
        val n = e164?.filter { it.isDigit() || it == '+' } ?: return false
        if (!n.startsWith("+") || n.length < 8) return false
        return when {
            n.startsWith("+43") -> !n.startsWith("+436")
            n.startsWith("+49") -> !(n.startsWith("+4915") || n.startsWith("+4916") || n.startsWith("+4917"))
            n.startsWith("+41") -> !n.startsWith("+417")
            else -> false
        }
    }
}
