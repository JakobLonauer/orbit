package io.github.thelony97.orbit.util

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Fehlerprotokoll (PLAN R1a): a small local log of caught and uncaught errors, shown in
 * Einstellungen › Über. Never leaves the phone unless Jakob shares it. Capped at [maxBytes];
 * the older half is dropped when full.
 */
class ErrorLog(private val file: File, private val maxBytes: Int = 1024 * 1024) {

    companion object {
        /** The app's log, for places without a context (set in OrbitApp.onCreate). */
        @Volatile var shared: ErrorLog? = null
    }

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    @Synchronized
    fun log(where: String, t: Throwable? = null, now: Instant = Instant.now()) {
        val text = buildString {
            append(stamp.format(now)).append("  ").append(where).append('\n')
            if (t != null) append(StringWriter().also { t.printStackTrace(PrintWriter(it)) }.toString().take(4000))
            append('\n')
        }
        runCatching {
            file.parentFile?.mkdirs()
            if (file.exists() && file.length() + text.length > maxBytes) {
                val keep = file.readText().takeLast(maxBytes / 2)
                file.writeText(keep.substringAfter("\n\n", keep))
            }
            file.appendText(text)
        }
    }

    fun read(): String = runCatching { file.readText() }.getOrDefault("")

    @Synchronized
    fun clear() {
        file.delete()
    }

    /** Records uncaught exceptions, then lets the previous handler (crash dialog) run as usual. */
    fun installUncaughtHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            log("Absturz in ${thread.name}", e)
            previous?.uncaughtException(thread, e)
        }
    }
}
