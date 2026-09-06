package com.rimor.minitools

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * The service that is always there, holding the gestures open.
 *
 * Accessibility is the only way an ordinary app gets a window that outlives its own activity and
 * sits over whatever else is on the cover screen. It is used here for exactly that and nothing
 * more: no window content is read, no events are consumed, no keys are filtered. The service
 * exists to own two invisible rectangles and to launch one activity when they are touched.
 */
class ToolsService : AccessibilityService() {

    private lateinit var prefs: Prefs
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    private var coverDisplay: Display? = null
    private var windows: WindowManager? = null
    private var corner: View? = null
    private var flash: View? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefs = Prefs(this)
        listener = prefs.observe { syncZones() }

        coverDisplay = CoverDisplay.find(this)
        val display = coverDisplay
        if (display == null) {
            // No second panel. Nothing to do, and nothing to complain about — the app simply has
            // no surface on this device.
            return
        }
        // The zones belong to the cover display, so they are added through a context bound to it
        // rather than through the service's own, which is the main screen's.
        windows = createDisplayContext(display)
            .createWindowContext(display, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
            .getSystemService(WindowManager::class.java)
        syncZones()
    }

    override fun onDestroy() {
        listener?.let { prefs.stopObserving(it) }
        removeZone(corner); corner = null
        removeZone(flash); flash = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    // ---- the zones ----------------------------------------------------------------------

    private fun syncZones() {
        if (windows == null) return
        corner = reconcile(
            existing = corner,
            wanted = prefs.cornerSwipe,
            zone = Zones.CORNER,
            gesture = GestureZoneView.Gesture.SWIPE_UP,
        )
        flash = reconcile(
            existing = flash,
            wanted = prefs.flashPress,
            zone = Zones.FLASH,
            gesture = GestureZoneView.Gesture.LONG_PRESS,
        )
    }

    private fun reconcile(
        existing: View?,
        wanted: Boolean,
        zone: Zone,
        gesture: GestureZoneView.Gesture,
    ): View? {
        if (wanted && existing == null) return addZone(zone, gesture)
        if (!wanted && existing != null) removeZone(existing)
        return if (wanted) existing else null
    }

    private fun addZone(zone: Zone, gesture: GestureZoneView.Gesture): View? {
        val wm = windows ?: return null
        val view = GestureZoneView(createDisplayContext(coverDisplay!!), gesture) { fire() }
        val params = WindowManager.LayoutParams(
            zone.width,
            zone.height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                // Without NO_LIMITS the window is clipped to the part of the panel that is
                // actually a display, and the flash zone lives in the part that is not.
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = zone.left
            y = zone.top
            // The camera island is a cutout. A window that respects it cannot be put on top of
            // the flash, which is the entire point of the flash zone.
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "miniTools/${gesture.name}"
        }
        return try {
            wm.addView(view, params)
            view
        } catch (e: Exception) {
            null
        }
    }

    private fun removeZone(view: View?) {
        if (view == null) return
        runCatching { windows?.removeView(view) }
    }

    // ---- what a gesture does ------------------------------------------------------------

    private fun fire() {
        if (prefs.haptics) tick()
        val displayId = coverDisplay?.displayId ?: Display.DEFAULT_DISPLAY
        if (!Recents.open(this, displayId)) {
            // The launcher would not take the explicit start. The global action is coarser — it
            // picks its own display — but it is better than nothing happening at all.
            performGlobalAction(GLOBAL_ACTION_RECENTS)
        }
    }

    private fun tick() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createOneShot(12L, 90))
    }

    companion object {
        @Volatile
        private var instance: ToolsService? = null

        /** Whether the service is enabled and running, which is the app's only real precondition. */
        fun isRunning(): Boolean = instance != null
    }
}
