package com.rimor.minitools

import org.junit.Assert.assertEquals
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

    /**
     * Where the cover navigation bar's buttons are. The bar is Rect(0, 938 - 948, 1048) and its
     * two buttons are centred on the strip left of the camera island rather than on the panel:
     * back at 163, home at 310, 147 apart. Back's slot therefore begins at x 90.
     */
    private val backButtonLeftEdge = 90
    private val navButtonCentres = listOf(163 to 996, 310 to 996)

    /**
     * Samsung's cover launcher puts its aspect-ratio button at this rectangle for apps it
     * launched, and an accessibility overlay sits above it — so covering it makes the button
     * unpressable for as long as miniTools is installed.
     */
    @Test
    fun `flash zone leaves the aspect-ratio button alone`() {
        val button = Zone(left = 426, top = 952, right = 522, bottom = 1048)
        assertFalse("flash zone covers the aspect-ratio button", Zones.FLASH.overlaps(button))
    }

    /**
     * The zone stops short of that button, so the lowest of the measured taps now fall outside
     * it. Everything above the floor must still land.
     */
    @Test
    fun `flash zone contains every measured tap above its floor`() {
        val above = flashTaps.filter { (_, y) -> y <= Zones.FLASH.bottom }
        assertTrue("the button left no usable flash zone at all", above.size >= 8)
        above.forEach { (x, y) ->
            assertTrue("flash tap ($x, $y) fell outside the zone", Zones.FLASH.contains(x, y))
        }
    }

    /**
     * The reason the zone is narrow. Reaching to 280 swallowed the back button whole, and a
     * gesture strip that eats the system's own back is worse than no gesture strip.
     */
    @Test
    fun `corner zone never touches a navigation bar button`() {
        navButtonCentres.forEach { (x, y) ->
            assertFalse("corner zone covers the nav button at ($x, $y)", Zones.CORNER.contains(x, y))
        }
        assertTrue(
            "corner zone must end before back's slot begins at x $backButtonLeftEdge",
            Zones.CORNER.right < backButtonLeftEdge,
        )
    }

    /** It still has to be swipeable: enough height for the threshold, and it reaches the edge. */
    @Test
    fun `corner zone can hold the swipe it asks for`() {
        assertTrue(
            "not enough travel for a ${Zones.SWIPE_THRESHOLD_PX}px threshold",
            Zones.CORNER.height > Zones.SWIPE_THRESHOLD_PX * 2,
        )
        assertTrue("must reach the bottom edge", Zones.CORNER.bottom == CoverDisplay.HEIGHT_PX)
        assertTrue("must reach the left edge", Zones.CORNER.left == 0)
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

    @Test
    fun `zones are unchanged at the stock resolution`() {
        assertEquals(Zones.FLASH, Zones.FLASH.scaledTo(CoverDisplay.WIDTH_PX, CoverDisplay.HEIGHT_PX))
        assertEquals(Zones.CORNER, Zones.CORNER.scaledTo(CoverDisplay.WIDTH_PX, CoverDisplay.HEIGHT_PX))
    }

    /** A custom resolution scales the panel, so the flash is still under the same finger. */
    @Test
    fun `zones follow a custom resolution`() {
        val half = Zones.FLASH.scaledTo(474, 524)
        assertEquals(Zone(left = 208, top = 434, right = 280, bottom = 475), half)
        flashTaps.filter { (_, y) -> y <= Zones.FLASH.bottom }.forEach { (x, y) ->
            assertTrue("tap ($x, $y) lost at half resolution", half.contains(x / 2, y / 2))
        }
    }

    @Test
    fun `scaled zones stay on a scaled panel and apart`() {
        listOf(720 to 796, 1080 to 1194, 474 to 524).forEach { (w, h) ->
            val flash = Zones.FLASH.scaledTo(w, h)
            val corner = Zones.CORNER.scaledTo(w, h)
            listOf(flash, corner).forEach { z ->
                assertTrue("$z runs off a ${w}x$h panel", z.left >= 0 && z.top >= 0 && z.right <= w && z.bottom <= h)
            }
            assertFalse("zones overlap at ${w}x$h", flash.overlaps(corner))
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
