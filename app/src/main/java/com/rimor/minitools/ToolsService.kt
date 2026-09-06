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

    private var launcher: OverlayHost? = null

    /** Whether the switcher has actually appeared since the density was set. */
    private var switcherSeen = false

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    /** If the switcher never arrives, the density still has to come back. */
    private val giveUp = Runnable { if (prefs.densityApplied) restoreDensity() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefs = Prefs(this)
        listener = prefs.observe { syncZones() }

        // An override we set and never cleared — because we were killed, or the phone rebooted
        // while the switcher was up — would otherwise leave the cover screen at the wrong density
        // with nothing to put it back. This is the net under that.
        if (prefs.densityApplied) {
            restoreDensity()
        }

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
        handler.removeCallbacks(giveUp)
        launcher?.dismiss()
        MontToast.dismiss()
        if (prefs.densityApplied) restoreDensity()
        listener?.let { prefs.stopObserving(it) }
        removeZone(corner); corner = null
        removeZone(flash); flash = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    /**
     * The only reason this service listens to anything.
     *
     * The density is put back when the switcher stops being what is on screen — but not before it
     * has been on screen. Between setting the density and the switcher actually appearing, other
     * windows come and go and every one of them is a package that is not the launcher; restoring
     * on the first of those undid the density before the switcher was ever built, which is
     * exactly the bug this two-step guards against.
     *
     * So: arm on seeing the switcher, restore on the first thing that follows it. No window
     * content is read; the event is used for nothing but the name of the package that now holds
     * the window.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!prefs.densityApplied) return
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == RECENTS_PACKAGE) {
            switcherSeen = true
        } else if (switcherSeen) {
            restoreDensity()
        }
    }

    override fun onInterrupt() = Unit

    // ---- the zones ----------------------------------------------------------------------

    private fun syncZones() {
        if (windows == null) return
        corner = reconcile(
            existing = corner,
            wanted = prefs.cornerSwipe,
            zone = Zones.CORNER,
            gestures = setOf(Gesture.SWIPE_UP),
        )
        flash = reconcile(
            existing = flash,
            wanted = prefs.flashPress,
            zone = Zones.FLASH,
            gestures = setOf(Gesture.TAP, Gesture.DOUBLE_TAP, Gesture.HOLD),
        )
    }

    private fun reconcile(
        existing: View?,
        wanted: Boolean,
        zone: Zone,
        gestures: Set<Gesture>,
    ): View? {
        if (wanted && existing == null) return addZone(zone, gestures)
        if (!wanted && existing != null) removeZone(existing)
        return if (wanted) existing else null
    }

    private fun addZone(zone: Zone, gestures: Set<Gesture>): View? {
        val wm = windows ?: return null
        val view = GestureZoneView(createDisplayContext(coverDisplay!!), gestures, ::dispatch)
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
            title = "miniTools/" + gestures.joinToString("+") { it.name }
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

    private fun dispatch(gesture: Gesture) {
        val action = prefs.actionFor(gesture)
        if (action == Action.NONE) return
        if (prefs.haptics) tick()
        when (action) {
            Action.RECENTS -> openRecents()
            Action.LAUNCHER -> openLauncher()
            Action.ROTATE -> toggleRotation()
            Action.NONE -> Unit
        }
    }

    private fun openRecents() {
        // Before the start, never after: the switcher is built fresh on every open and reads the
        // density that is in force at the moment it is created.
        applyDensity()
        val displayId = coverDisplay?.displayId ?: Display.DEFAULT_DISPLAY
        if (!Recents.open(this, displayId)) {
            // The launcher would not take the explicit start. The global action is coarser — it
            // picks its own display — but it is better than nothing happening at all.
            performGlobalAction(GLOBAL_ACTION_RECENTS)
        }
    }

    /**
     * Over what you were doing, not instead of it. Starting the activity changed apps, which is
     * the wrong shape entirely for something you open to reach a different app.
     */
    private fun openLauncher() {
        val display = coverDisplay ?: return
        val host = launcher ?: OverlayHost(this, display).also { launcher = it }
        if (host.isShowing) {
            host.dismiss()
            return
        }
        host.show { MiniToolsTheme { LauncherOverlay(onDismiss = { host.dismiss() }) } }
    }

    private fun toggleRotation() {
        val display = coverDisplay ?: return
        val on = Rotation.toggle(this, display)
        say(if (on) "Rotation On" else "Rotation Off")
    }

    /** A word, on the panel the gesture happened on rather than the one behind your hand. */
    private fun say(text: String) {
        val display = coverDisplay ?: return
        MontToast.show(this, display, text)
    }

    /**
     * Set the density before the switcher is started, never after.
     *
     * The switcher is started with CLEAR_TASK, so it is built fresh every time — and a fresh
     * activity reads the density that is in force when it is created. That is what makes this
     * work without force-stopping Samsung's launcher, which declares keepalive.density=true and
     * would otherwise carry its old layout straight through the change.
     */
    private fun applyDensity() {
        val display = coverDisplay ?: return
        val density = prefs.switcherDensity
        if (density <= 0 || !Density.permitted(this)) return
        if (Density.apply(this, display.displayId, density)) {
            prefs.densityApplied = true
            switcherSeen = false
            // A launch that never lands would otherwise leave the panel at the wrong density
            // until something else happened to put it back.
            handler.removeCallbacks(giveUp)
            handler.postDelayed(giveUp, GIVE_UP_MS)
        }
    }

    private fun restoreDensity() {
        handler.removeCallbacks(giveUp)
        switcherSeen = false
        val displayId = coverDisplay?.displayId ?: CoverDisplay.idOrDefault(this)
        if (Density.restore(this, displayId)) {
            prefs.densityApplied = false
        }
    }

    private fun tick() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createOneShot(12L, 90))
    }

    companion object {
        private const val RECENTS_PACKAGE = "com.sec.android.app.launcher"

        /** Long enough for the switcher to appear, short enough not to strand the panel. */
        private const val GIVE_UP_MS = 8_000L

        @Volatile
        private var instance: ToolsService? = null

        /** Whether the service is enabled and running, which is the app's only real precondition. */
        fun isRunning(): Boolean = instance != null
    }
}
