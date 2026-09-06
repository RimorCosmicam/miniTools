package com.rimor.minitools

import android.content.Context
import android.graphics.PixelFormat
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Auto-rotate on the cover screen, which Samsung does not offer.
 *
 * Not done by rotating the display — that route is closed. `wm user-rotation -d 1 lock 1` is
 * accepted and then ignored, and so is forcing the display to ignore orientation requests; the
 * panel reports mSupportAutoRotation=true and stays at 948x1048 regardless.
 *
 * What works is asking rather than telling. The display rotates to satisfy the topmost window
 * that expresses an orientation, and the cover screen never rotates because the thing on it
 * asks for SCREEN_ORIENTATION_NOSENSOR. So miniTools adds a window of its own — zero by zero,
 * invisible, untouchable — whose only property that matters is `screenOrientation = SENSOR`.
 * Everything behind it then turns with the phone.
 *
 * Inspired by CoverSpin.
 */
object Rotation {

    private var pivot: View? = null

    val isOn: Boolean get() = pivot != null

    fun enable(context: Context, display: Display): Boolean {
        if (pivot != null) return true
        return try {
            val windows = context.createDisplayContext(display)
                .createWindowContext(display, TYPE, null)
                .getSystemService(WindowManager::class.java)
            val view = View(context.applicationContext)
            windows.addView(view, params())
            pivot = view
            true
        } catch (e: Exception) {
            false
        }
    }

    fun disable(context: Context, display: Display): Boolean {
        val view = pivot ?: return true
        return try {
            context.createDisplayContext(display)
                .createWindowContext(display, TYPE, null)
                .getSystemService(WindowManager::class.java)
                .removeView(view)
            pivot = null
            true
        } catch (e: Exception) {
            pivot = null
            false
        }
    }

    fun toggle(context: Context, display: Display): Boolean =
        if (isOn) {
            disable(context, display); false
        } else {
            enable(context, display); true
        }

    /**
     * An accessibility overlay, so this costs no permission at all. If the window manager turns
     * out not to weigh these when it decides a display's rotation, the fallback is
     * TYPE_APPLICATION_OVERLAY and the "display over other apps" toggle — still a switch the user
     * flips themselves, never adb.
     */
    private const val TYPE = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

    private fun params() = WindowManager.LayoutParams(
        0,
        0,
        TYPE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        // The whole point. 4 is SCREEN_ORIENTATION_SENSOR.
        screenOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
        title = "miniTools/ROTATION"
    }
}
