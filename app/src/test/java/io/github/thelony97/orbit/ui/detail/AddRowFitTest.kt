package io.github.thelony97.orbit.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

/** The add row at the end of a contact page stays one line (no lone chip below). */
class AddRowFitTest {
    @Test fun allFitShowsAll() {
        assertEquals(3, AddRowFit.count(listOf(60, 70, 80), more = 60, gap = 8, max = 300))
    }

    @Test fun exactFitCountsGapsOnlyBetweenChips() {
        // 60 + 8 + 70 + 8 + 80 = 226
        assertEquals(3, AddRowFit.count(listOf(60, 70, 80), more = 60, gap = 8, max = 226))
    }

    @Test fun tooManyLeavesRoomForMore() {
        // Link, Foto, Geschenkidee, Feld at phone width: 3 + Mehr would be 69+75+134+3*8+70 = 372 > 360,
        // so two chips plus Mehr.
        assertEquals(2, AddRowFit.count(listOf(69, 75, 134, 69), more = 70, gap = 8, max = 360))
        // A wider fourth chip: three plus Mehr fit exactly (69+75+134+3*8+70 = 372).
        assertEquals(3, AddRowFit.count(listOf(69, 75, 134, 100), more = 70, gap = 8, max = 372))
    }

    @Test fun neverOnlyMore() {
        assertEquals(1, AddRowFit.count(listOf(200, 200), more = 70, gap = 8, max = 150))
    }
}
