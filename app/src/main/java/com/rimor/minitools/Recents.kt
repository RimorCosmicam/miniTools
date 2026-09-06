package com.rimor.minitools

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import android.view.Display

/**
 * One UI's own task switcher, put on the cover screen.
 *
 * Nothing here draws a recents screen or imitates one. Samsung already ships a good one — it
 * lives in the launcher, it renders correctly on the cover panel at the panel's native 420dpi,
 * and the only thing standing between it and the cover screen is that nothing ever asks for it
 * there. miniTools asks.
 *
 * The density is deliberately left alone. Lowering it only shrinks the chrome — the task cards
 * are thumbnails scaled to the display and do not change size — so an override would buy a
 * slightly smaller "Close all" button in exchange for a privileged permission and a setting
 * that has to be put back on the way out.
 */
object Recents {
    private const val TAG = "miniTools"

    private val COMPONENT = ComponentName(
        "com.sec.android.app.launcher",
        "com.android.quickstep.RecentsActivity",
    )

    fun isAvailable(context: Context): Boolean =
        context.packageManager.resolveActivity(intent(), 0) != null

    /** Returns true if the switcher was asked for; false if the launcher would not take it. */
    fun open(context: Context, display: Display?): Boolean = try {
        val displayId = display?.displayId ?: Display.DEFAULT_DISPLAY
        val options = ActivityOptions.makeBasic().apply {
            launchDisplayId = displayId
            // Ask for the safe area. Left to itself the switcher lays out into the full
            // 948 x 1048, which puts "Close all" and the bottom of every card underneath the
            // camera island — the island is a cutout in the display, so that part is simply not
            // there to be seen.
            safeBounds(display)?.let { setLaunchBounds(it) }
        }
        options.requestFreeform()
        context.startActivity(intent(), options.toBundle())
        true
    } catch (e: Exception) {
        Log.w(TAG, "recents refused on display ${display?.displayId}", e)
        false
    }

    /**
     * The panel minus the bits of it that are not really there.
     *
     * Read from the display's own cutout rather than written down as 828, because the number is
     * a property of the panel and this app should not be the second place it is recorded.
     */
    fun safeBounds(display: Display?): Rect? {
        val cutout = display?.cutout ?: return null
        val mode = display.mode
        val width = mode.physicalWidth
        val height = mode.physicalHeight
        if (width <= 0 || height <= 0) return null
        val bounds = Rect(
            cutout.safeInsetLeft,
            cutout.safeInsetTop,
            width - cutout.safeInsetRight,
            height - cutout.safeInsetBottom,
        )
        return if (bounds.width() > 0 && bounds.height() > 0 && bounds != Rect(0, 0, width, height)) {
            bounds
        } else {
            null
        }
    }

    /**
     * Launch bounds are only honoured for a task that is allowed to have any, which in practice
     * means freeform. The setter is not public API, so it is asked for by name and the whole
     * thing degrades to an ordinary fullscreen launch when the name is not there.
     */
    private fun ActivityOptions.requestFreeform() {
        runCatching {
            ActivityOptions::class.java
                .getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                .invoke(this, WINDOWING_MODE_FREEFORM)
        }.onFailure { Log.i(TAG, "no setLaunchWindowingMode; fullscreen it is") }
    }

    /** WindowConfiguration.WINDOWING_MODE_FREEFORM. */
    private const val WINDOWING_MODE_FREEFORM = 5

    private fun intent() = Intent(Intent.ACTION_MAIN).apply {
        component = COMPONENT
        addCategory(Intent.CATEGORY_DEFAULT)
        // NEW_TASK because there is no activity behind this, and TASK_ON_HOME so dismissing the
        // switcher falls back to the cover home rather than to whatever launched it.
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
    }
}
