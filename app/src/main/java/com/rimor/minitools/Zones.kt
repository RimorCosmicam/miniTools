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
     * Margin is not free below, though. Samsung's cover launcher puts its aspect-ratio button at
     * Rect(426, 952 - 522, 1048) for apps it launched, and an accessibility overlay sits above it
     * — so a zone reaching to the bottom of the panel swallows that button entirely and there is
     * no way to press it while miniTools is installed. The zone stops at 950, two pixels clear.
     */
    val FLASH = Zone(left = 416, top = 868, right = 560, bottom = 950)

    /**
     * The bottom-left corner, for the swipe up — and nothing else's.
     *
     * The cover navigation bar is `Rect(0, 938 - 948, 1048)`, and its two buttons are not centred
     * on the panel: they are centred on the strip left of the camera island, at x 236. Back sits
     * at 163 and home at 310, 147 apart, so back's slot begins at x 90.
     *
     * The zone therefore stops at 88. Everything to the left of back is free — with the
     * navigation bar showing or without it — and everything from 90 rightwards belongs to
     * buttons somebody is trying to press. The first version of this reached to 280 and swallowed
     * back whole.
     *
     * It runs taller than the navigation bar to give the swipe somewhere to travel: 148px of
     * height against a 48px threshold.
     */
    val CORNER = Zone(left = 0, top = 900, right = 88, bottom = 1048)

    /** How far up a finger must travel inside [CORNER] before it counts as a swipe. */
    const val SWIPE_THRESHOLD_PX: Int = 48

    /** A swipe that takes longer than this is a rest, not a gesture. */
    const val SWIPE_TIMEOUT_MS: Long = 600L
}
