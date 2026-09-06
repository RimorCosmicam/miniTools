package com.rimor.minitools

/**
 * A rectangle on the cover panel, in pixels.
 *
 * Deliberately not [android.graphics.Rect]: these numbers are the measured heart of the app and
 * they should be covered by tests that run in seconds on a laptop, which a framework class
 * stubbed out by the unit-test android.jar would prevent.
 */
data class Zone(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    fun contains(x: Int, y: Int): Boolean = x in left..right && y in top..bottom

    fun overlaps(other: Zone): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom
}

/**
 * Where the gestures live, in cover-display pixels.
 *
 * These are hardware facts, not layout, so they are written as pixels against the 948 x 1048
 * panel rather than as dp against whatever density the display happens to be running at. They
 * were measured rather than guessed — see docs/MEASUREMENTS.md.
 */
object Zones {

    /**
     * The flash, which is touchable even though it is not drawable.
     *
     * The camera island is a cutout in the *display*, not in the digitiser: the panel keeps
     * reporting touches under it. Twenty-seven deliberate taps on the flash landed in
     * x 444..501, y 916..980 — a cluster 57 x 64 wide, sitting just inside the cutout's left
     * edge, which begins at x 428.
     *
     * Margin here is free. Nothing else on the phone can use this rectangle, and a tap that
     * strays right onto the lenses, where the digitiser reports nothing, is simply lost rather
     * than delivered somewhere wrong.
     */
    val FLASH = Zone(left = 416, top = 868, right = 560, bottom = 1048)

    /**
     * The bottom-left corner, for the swipe up.
     *
     * Kept narrow and hard to the left because the bottom centre belongs to Samsung Pay, and a
     * strip that reached the middle would eat it. 280px is 30% of the panel's width.
     */
    val CORNER = Zone(left = 0, top = 952, right = 280, bottom = 1048)

    /** How far up a finger must travel inside [CORNER] before it counts as a swipe. */
    const val SWIPE_THRESHOLD_PX: Int = 48

    /** A swipe that takes longer than this is a rest, not a gesture. */
    const val SWIPE_TIMEOUT_MS: Long = 600L
}
