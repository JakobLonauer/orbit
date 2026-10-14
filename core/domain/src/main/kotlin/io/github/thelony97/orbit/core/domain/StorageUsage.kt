package io.github.thelony97.orbit.core.domain

/**
 * Speicher-Übersicht (PLAN 3.13 / 3.10): which of Orbit's files belong to which part. Fixed rules
 * on the file name only – the app lists `filesDir`, `filesDir/photos` and the database files.
 */
object StorageUsage {
    enum class Part(val label: String) {
        PHOTOS("Fotos"),
        DOCUMENTS("Dokumente"),
        HISTORY("Verlauf"),
        CONTACTS("Kontakte"),
        SAFETY("Sicherheitskopien"),
    }

    /** One file as found on disk; [inPhotos] = it lies in the photo/attachment folder. */
    data class FileInfo(val name: String, val bytes: Long, val inPhotos: Boolean = false)

    data class Slice(val part: Part, val bytes: Long, val files: Int)

    fun classify(f: FileInfo): Part? = when {
        f.inPhotos && f.name.endsWith(".pdf", ignoreCase = true) -> Part.DOCUMENTS
        f.inPhotos -> Part.PHOTOS
        f.name.startsWith("orbit.db") -> Part.HISTORY
        // Copies made before a data upgrade or of an unreadable file – kept on purpose.
        f.name.matches(Regex("orbit\\.v\\d+\\.bak\\.json")) || f.name.startsWith("orbit-broken-") -> Part.SAFETY
        f.name == "orbit.json" || f.name == "caller-index.json" -> Part.CONTACTS
        else -> null
    }

    /** Non-empty parts, largest first (stable order for equal sizes). */
    fun slices(files: List<FileInfo>): List<Slice> =
        files.mapNotNull { f -> classify(f)?.let { it to f } }
            .groupBy({ it.first }, { it.second })
            .map { (part, fs) -> Slice(part, fs.sumOf { it.bytes.coerceAtLeast(0) }, fs.size) }
            .filter { it.bytes > 0 }
            .sortedWith(compareByDescending<Slice> { it.bytes }.thenBy { it.part.ordinal })

    /** de-AT sizes like the system shows them (1000-based): „900 kB“, „9,8 MB“, „182 MB“, „1,2 GB“. */
    fun format(bytes: Long): String {
        val units = listOf("B", "kB", "MB", "GB", "TB")
        var v = bytes.coerceAtLeast(0).toDouble()
        var u = 0
        while (v >= 1000 && u < units.lastIndex) { v /= 1000; u++ }
        // 999,6 kB would round to „1000 kB“: that is 1 MB.
        if (u in 1 until units.lastIndex && v >= 999.5) { v /= 1000; u++ }
        val text = if (u == 0 || v >= 100) Math.round(v).toString()
        else java.lang.String.format(java.util.Locale.forLanguageTag("de-AT"), "%.1f", v).removeSuffix(",0")
        return "$text ${units[u]}"
    }
}
