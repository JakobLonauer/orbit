package io.github.thelony97.orbit.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlyPathTest {

    @Test fun linzToViennaGoesOutSoBothAreVisible() {
        val hop = FlyPath.top(48.30, 14.29, 13.0, 48.21, 16.37, 13.0, 1080.0)
        assertNotNull(hop)
        hop!!
        assertTrue(hop.zoom in 7.0..8.5)
        assertEquals(48.255, hop.lat, 1e-9)
        assertEquals(15.33, hop.lng, 1e-9)
    }

    @Test fun neighboursAndOverviewNeedNoArc() {
        // 100 m apart in the same city.
        assertNull(FlyPath.top(48.3000, 14.2900, 13.0, 48.3009, 14.2900, 13.0, 1080.0))
        // From the Austria overview straight in.
        assertNull(FlyPath.top(48.2, 13.5, 4.6, 48.30, 14.29, 13.0, 1080.0))
        assertNull(FlyPath.top(48.3, 14.29, 13.0, 48.3, 14.29, 15.0, 1080.0))
    }

    @Test fun neverFurtherOutThanTheWholeWorld() {
        assertTrue(FlyPath.top(48.2, 16.3, 12.0, -33.9, 151.2, 12.0, 1080.0)!!.zoom in 1.0..2.5)
        assertEquals(1.0, FlyPath.top(0.0, -170.0, 10.0, 0.0, 170.0, 10.0, 1080.0)!!.zoom, 1e-9)
    }
}
