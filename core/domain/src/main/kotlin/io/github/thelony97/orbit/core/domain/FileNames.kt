package io.github.thelony97.orbit.core.domain

/**
 * Names from other apps (DISPLAY_NAME) become file names when Orbit shares or opens a document.
 * Only the last path part survives, so a name like `../../files/orbit.json` can never point at
 * Orbit's own data.
 */
object FileNames {
    fun safe(name: String?, fallback: String): String {
        val last = name.orEmpty().replace('\\', '/').substringAfterLast('/')
            .filter { it >= ' ' && it != ':' }
            .trim()
        return if (last.isEmpty() || last == "." || last == "..") fallback else last.take(120)
    }
}
