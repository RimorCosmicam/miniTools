package com.rimor.minitools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The zones are measurements, and a measurement that quietly drifts is worse than one that was
 * never taken. These pin the two rectangles to the taps they were derived from: every one of the
 * 27 flash taps captured off the raw digitiser must still land inside the zone.
 */
class ZonesTest {

    private val flashTaps = listOf(
        492 to 945, 489 to 966, 483 to 964, 485 to 963, 486 to 957, 471 to 965,
        464 to 951, 467 to 940, 470 to 954, 481 to 961, 474 to 954, 474 to 966,
        462 to 930, 477 to 926, 496 to 948, 493 to 948, 452 to 980, 501 to 967,
        455 to 967, 457 to 973, 444 to 975, 500 to 916, 450 to 965, 468 to 967,
        464 to 964, 483 to 958, 493 to 926,
    )

    private val cornerTaps = listOf(100 to 997, 95 to 966)

    @Test
    fun `flash zone contains every measured flash tap`() {
        flashTaps.forEach { (x, y) ->
            assertTrue("flash tap ($x, $y) fell outside the zone", Zones.FLASH.contains(x, y))
        }
    }

    @Test
    fun `corner zone contains every measured corner tap`() {
        cornerTaps.forEach { (x, y) ->
            assertTrue("corner tap ($x, $y) fell outside the zone", Zones.CORNER.contains(x, y))
        }
    }

    /**
     * The bottom centre is Samsung Pay's, and leaving it alone is the whole reason the swipe was
     * put in a corner rather than along the edge.
     */
    @Test
    fun `corner zone leaves the bottom centre alone`() {
        val centre = CoverDisplay.WIDTH_PX / 2
        assertFalse(Zones.CORNER.contains(centre, CoverDisplay.HEIGHT_PX - 1))
        assertTrue("corner must not reach the middle", Zones.CORNER.right < centre)
    }

    /** Neither zone may run off the panel, or the window lands somewhere it cannot be touched. */
    @Test
    fun `zones stay on the panel`() {
        listOf(Zones.FLASH, Zones.CORNER).forEach { zone ->
            assertTrue("$zone starts off-panel", zone.left >= 0 && zone.top >= 0)
            assertTrue("$zone runs off the right", zone.right <= CoverDisplay.WIDTH_PX)
            assertTrue("$zone runs off the bottom", zone.bottom <= CoverDisplay.HEIGHT_PX)
            assertTrue("$zone is empty", zone.width > 0 && zone.height > 0)
        }
    }

    /** Opposite corners of the panel. They must never overlap. */
    @Test
    fun `zones do not overlap`() {
        assertFalse(Zones.FLASH.overlaps(Zones.CORNER))
    }

    /**
     * The flash zone has to reach into the camera cutout, which begins at x 428, y 828 — that is
     * the point of it. A zone that stopped at the cutout would be a zone over nothing.
     */
    @Test
    fun `flash zone reaches into the camera cutout`() {
        assertTrue(Zones.FLASH.right > 428)
        assertTrue(Zones.FLASH.bottom > 828)
    }
}
