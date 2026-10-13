package io.github.thelony97.orbit.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarDayTest {
    @Test
    fun roundTripWithoutYear() {
        val json = Json { explicitNulls = false }
        val day = CalendarDay(29, 2)
        val text = json.encodeToString(CalendarDay.serializer(), day)
        assertEquals("""{"day":29,"month":2}""", text)
        assertEquals(day, json.decodeFromString(CalendarDay.serializer(), text))
    }
}
