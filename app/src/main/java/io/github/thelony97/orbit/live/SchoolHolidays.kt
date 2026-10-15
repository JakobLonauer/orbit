package io.github.thelony97.orbit.live

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * Schulferien Oberösterreich (PLAN 9 D2) from OpenHolidays – fetched once a year, kept on the
 * phone; only asked for when an organisation's hours have a school-holiday rule („SH off“).
 */
object SchoolHolidays {

    private val ranges = ArrayList<Pair<LocalDate, LocalDate>>()
    @Volatile private var loadedYear = 0

    private fun file(ctx: Context, year: Int) = File(ctx.noBackupFilesDir, "school-holidays-$year.txt")

    /** Today in the school holidays (false while unknown). */
    fun today(): Boolean {
        val d = LocalDate.now()
        return synchronized(ranges) { ranges.any { (a, b) -> !d.isBefore(a) && !d.isAfter(b) } }
    }

    /** Loads (and once a year fetches) the year's holidays. */
    suspend fun ensure(ctx: Context) = withContext(Dispatchers.IO) {
        val year = LocalDate.now().year
        if (loadedYear == year) return@withContext
        val f = file(ctx, year)
        val text = if (f.exists()) f.readText() else fetch(year)?.also { runCatching { f.writeText(it) } }
        if (text != null) {
            val parsed = Regex("\"startDate\"\\s*:\\s*\"(\\d{4}-\\d{2}-\\d{2})\"[^}]*?\"endDate\"\\s*:\\s*\"(\\d{4}-\\d{2}-\\d{2})\"")
                .findAll(text).mapNotNull { m -> runCatching { LocalDate.parse(m.groupValues[1]) to LocalDate.parse(m.groupValues[2]) }.getOrNull() }.toList()
            synchronized(ranges) {
                ranges.clear()
                ranges.addAll(parsed)
            }
            loadedYear = year
        }
    }

    private fun fetch(year: Int): String? = runCatching {
        val url = URL("https://openholidaysapi.org/SchoolHolidays?countryIsoCode=AT&subdivisionCode=AT-4&validFrom=$year-01-01&validTo=$year-12-31&languageIsoCode=DE")
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 5_000
        c.readTimeout = 8_000
        c.setRequestProperty("Accept", "application/json")
        try {
            if (c.responseCode !in 200..299) null else c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
        } finally {
            c.disconnect()
        }
    }.getOrNull()
}
