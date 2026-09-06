package com.rimor.minitools

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View

/** What a zone can report. */
enum class Gesture { TAP, DOUBLE_TAP, HOLD, SWIPE_UP }

/**
 * An invisible rectangle that watches for gestures and reports them.
 *
 * There is nothing to draw. The zones are places on the panel, not controls — the corner strip
 * sits where the cover home has nothing, and the flash zone sits where the display physically is
 * not. Drawing anything in either would be decoration announcing itself, which the language
 * forbids outright.
 *
 * A tap has to wait to find out whether it is the first half of a double tap, so a single tap is
 * reported [DOUBLE_GAP_MS] late. That delay is the price of having both on one spot, and it is
 * only paid by the single tap.
 */
@SuppressLint("ViewConstructor")
class GestureZoneView(
    context: Context,
    private val supported: Set<Gesture>,
    private val onGesture: (Gesture) -> Unit,
) : View(context) {

    private var downY = 0f
    private var downAt = 0L
    private var handled = false
    private var pendingTap = false

    private val hold = Runnable {
        if (!handled) {
            handled = true
            pendingTap = false
            onGesture(Gesture.HOLD)
        }
    }

    private val singleTap = Runnable {
        if (pendingTap) {
            pendingTap = false
            onGesture(Gesture.TAP)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.rawY
                downAt = SystemClock.uptimeMillis()
                handled = false

                if (pendingTap && Gesture.DOUBLE_TAP in supported) {
                    // The second half of a double tap. The first half never fired.
                    removeCallbacks(singleTap)
                    pendingTap = false
                    handled = true
                    onGesture(Gesture.DOUBLE_TAP)
                    return true
                }
                if (Gesture.HOLD in supported) postDelayed(hold, HOLD_MS)
            }

            MotionEvent.ACTION_MOVE -> if (!handled && Gesture.SWIPE_UP in supported) {
                val travelled = downY - event.rawY
                val elapsed = SystemClock.uptimeMillis() - downAt
                if (travelled >= Zones.SWIPE_THRESHOLD_PX && elapsed <= Zones.SWIPE_TIMEOUT_MS) {
                    handled = true
                    removeCallbacks(hold)
                    onGesture(Gesture.SWIPE_UP)
                }
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(hold)
                if (handled) return true
                val quick = SystemClock.uptimeMillis() - downAt < HOLD_MS
                if (!quick) return true
                when {
                    Gesture.DOUBLE_TAP in supported -> {
                        // Hold the tap back long enough to see whether a second one follows.
                        pendingTap = true
                        postDelayed(singleTap, DOUBLE_GAP_MS)
                    }
                    Gesture.TAP in supported -> onGesture(Gesture.TAP)
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(hold)
                removeCallbacks(singleTap)
                pendingTap = false
            }
        }
        // Every touch inside the zone is consumed. The rectangles are small and in places nothing
        // else claims; letting a stray touch fall through would make the corner behave differently
        // depending on what happened to be underneath it.
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hold)
        removeCallbacks(singleTap)
        super.onDetachedFromWindow()
    }

    private companion object {
        /** Long enough not to fire on a knuckle brushing the island, short enough to feel deliberate. */
        const val HOLD_MS = 350L

        /** How long a single tap waits to find out it was alone. */
        const val DOUBLE_GAP_MS = 260L
    }
}
