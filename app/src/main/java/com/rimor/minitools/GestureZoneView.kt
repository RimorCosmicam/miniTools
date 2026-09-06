package com.rimor.minitools

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View

/**
 * An invisible rectangle that watches for one gesture and reports it.
 *
 * There is nothing to draw here. The zones are places on the panel, not controls — the corner
 * strip sits where the cover home screen has nothing, and the flash zone sits where the display
 * physically is not. Drawing anything in either would be decoration announcing itself, which is
 * the one thing the language forbids outright.
 */
@SuppressLint("ViewConstructor")
class GestureZoneView(
    context: Context,
    private val gesture: Gesture,
    private val onTriggered: () -> Unit,
) : View(context) {

    enum class Gesture { SWIPE_UP, LONG_PRESS }

    private var downY = 0f
    private var downAt = 0L
    private var fired = false

    private val longPress = Runnable {
        if (!fired) {
            fired = true
            onTriggered()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.rawY
                downAt = SystemClock.uptimeMillis()
                fired = false
                if (gesture == Gesture.LONG_PRESS) {
                    postDelayed(longPress, LONG_PRESS_MS)
                }
            }

            MotionEvent.ACTION_MOVE -> if (gesture == Gesture.SWIPE_UP && !fired) {
                val travelled = downY - event.rawY
                val elapsed = SystemClock.uptimeMillis() - downAt
                if (travelled >= Zones.SWIPE_THRESHOLD_PX && elapsed <= Zones.SWIPE_TIMEOUT_MS) {
                    fired = true
                    onTriggered()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
            }
        }
        // Every touch inside the zone is consumed, gesture or not. The rectangles are small and
        // in places nothing else claims; letting a stray touch fall through to whatever is
        // underneath would make the corner behave differently depending on what is on screen.
        return true
    }

    private companion object {
        /** Long enough not to fire on a knuckle brushing the island, short enough to feel deliberate. */
        const val LONG_PRESS_MS = 350L
    }
}
