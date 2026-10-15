package io.github.thelony97.orbit.core.domain

import io.github.thelony97.orbit.core.model.CustomField
import io.github.thelony97.orbit.core.model.MacroStep
import io.github.thelony97.orbit.core.model.MacroStepKind

/**
 * Hotline-Makros (PLAN 9 B4): from keys Jakob typed in a call to a macro and back to what the
 * phone plays. Fixed rules: a gap of [GAP_MS] or more is a pause (whole seconds), a run of digits
 * equal to an own field becomes that field.
 */
object Macros {

    /** A key typed in the call, [at] milliseconds after the call was answered. */
    data class Key(val char: Char, val at: Long)

    const val GAP_MS = 1_500L
    private const val SECOND = 1_000L

    /** A recording turned into steps; fields ([fields]) replace the digit runs that equal them. */
    fun record(keys: List<Key>, fields: List<CustomField>): List<MacroStep> {
        if (keys.isEmpty()) return emptyList()
        // Runs of keys typed without a pause between them.
        val runs = ArrayList<MutableList<Key>>()
        var last = 0L
        keys.sortedBy { it.at }.forEach { k ->
            if (runs.isEmpty() || k.at - last >= GAP_MS) runs.add(ArrayList())
            runs.last().add(k)
            last = k.at
        }
        val out = ArrayList<MacroStep>()
        var prevEnd = 0L
        runs.forEach { run ->
            val gap = run.first().at - prevEnd
            if (gap >= GAP_MS) out += MacroStep(MacroStepKind.PAUSE, ms = (gap + SECOND / 2) / SECOND * SECOND)
            val text = run.joinToString("") { it.char.toString() }
            // „482177#“: the field, then the confirming key the hotline asks for.
            val tail = text.takeLastWhile { it == '#' || it == '*' }
            val field = fieldFor(text.dropLast(tail.length), fields)
            if (field != null) {
                out += MacroStep(MacroStepKind.FIELD, field.id)
                tail.forEach { out += MacroStep(MacroStepKind.KEY, it.toString()) }
            } else {
                text.forEach { out += MacroStep(MacroStepKind.KEY, it.toString()) }
            }
            prevEnd = run.last().at
        }
        return merge(out)
    }

    /** A field whose digits equal [typed] (separators in the field ignored); at least 4 digits. */
    private fun fieldFor(typed: String, fields: List<CustomField>): CustomField? {
        val digits = typed.filter { it.isLetterOrDigit() }
        if (digits.length < 4) return null
        return fields.firstOrNull { f -> f.value.filter { it.isLetterOrDigit() }.equals(digits, ignoreCase = true) }
    }

    /** Neighbouring pauses add up; a trailing pause is dropped. */
    private fun merge(steps: List<MacroStep>): List<MacroStep> {
        val out = ArrayList<MacroStep>()
        steps.forEach { s ->
            val prev = out.lastOrNull()
            if (s.kind == MacroStepKind.PAUSE && prev?.kind == MacroStepKind.PAUSE) out[out.lastIndex] = prev.copy(ms = prev.ms + s.ms) else out += s
        }
        while (out.lastOrNull()?.kind == MacroStepKind.PAUSE) out.removeAt(out.lastIndex)
        return out
    }

    /** „1 · 4 s · 3 · Kundennummer · #“ – the macro as one line (chips in the editor). */
    fun label(steps: List<MacroStep>, fieldName: (String) -> String?): String = steps.joinToString(" · ") { s ->
        when (s.kind) {
            MacroStepKind.KEY -> s.value
            MacroStepKind.PAUSE -> "${s.ms / SECOND} s"
            MacroStepKind.WAIT -> "Weiter"
            MacroStepKind.FIELD -> fieldName(s.value) ?: "Feld"
        }
    }

    /**
     * Without the phone role: a dial string the phone's own dialer plays after the number –
     * „,“ is a 2-second pause, „;“ waits for a tap. Field digits come from [fieldValue].
     */
    fun dialString(steps: List<MacroStep>, fieldValue: (String) -> String?): String = buildString {
        steps.forEach { s ->
            when (s.kind) {
                MacroStepKind.KEY -> append(s.value.filter { it.isDigit() || it == '*' || it == '#' })
                MacroStepKind.PAUSE -> repeat(((s.ms + 1_999) / 2_000).toInt().coerceAtLeast(1)) { append(',') }
                MacroStepKind.WAIT -> append(';')
                MacroStepKind.FIELD -> append(fieldValue(s.value).orEmpty().filter { it.isDigit() || it == '*' || it == '#' })
            }
        }
    }

    /** What the player does, one thing at a time. */
    sealed interface Play {
        data class Tone(val char: Char, val fromField: Boolean) : Play
        data class Wait(val ms: Long) : Play
        data object AwaitTap : Play
    }

    /** The steps as single tones, pauses and waits; field digits are typed like keys. */
    fun plan(steps: List<MacroStep>, fieldValue: (String) -> String?): List<Play> = buildList {
        steps.forEach { s ->
            when (s.kind) {
                MacroStepKind.KEY -> s.value.filter { it.isDigit() || it == '*' || it == '#' }.forEach { add(Play.Tone(it, fromField = false)) }
                MacroStepKind.PAUSE -> add(Play.Wait(s.ms))
                MacroStepKind.WAIT -> add(Play.AwaitTap)
                MacroStepKind.FIELD -> fieldValue(s.value).orEmpty().filter { it.isDigit() || it == '*' || it == '#' }
                    .forEach { add(Play.Tone(it, fromField = true)) }
            }
        }
    }

    /**
     * PLAN 9 B7 „Tastenfeld schreibt mit“: digit runs of four and more typed in a call that are not
     * an own field – Vorgangsnummern the hotline asked for.
     */
    fun cases(keys: List<Key>, fields: List<CustomField>): List<String> {
        if (keys.isEmpty()) return emptyList()
        val runs = ArrayList<StringBuilder>()
        var last = 0L
        keys.sortedBy { it.at }.forEach { k ->
            if (runs.isEmpty() || k.at - last >= GAP_MS) runs.add(StringBuilder())
            runs.last().append(k.char)
            last = k.at
        }
        return runs.map { it.toString().trimEnd('#', '*') }
            .filter { r -> r.length >= 4 && r.all(Char::isDigit) && fieldFor(r, fields) == null }
            .distinct()
    }

    /** A recording is worth a macro: at least two keys, not just a number dialled again. */
    fun worthKeeping(keys: List<Key>): Boolean = keys.size >= 2
}
