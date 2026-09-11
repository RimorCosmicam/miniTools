package com.rimor.minitools

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
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

    /** The gestures each zone was built for; a change of assignment rebuilds it. */
    private var cornerGestures: Set<Gesture> = emptySet()
    private var flashGestures: Set<Gesture> = emptySet()

    /** The upright panel size the zones were last laid out against. */
    private var laidOutFor: Pair<Int, Int>? = null

    /** A change of resolution moves every zone, so they are laid out again when it happens. */
    private val displayChanged = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == coverDisplay?.displayId) syncZones()
        }

        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit
    }

    private var launcher: OverlayHost? = null

    /**
     * Apps come and go while the service is running, and a launcher showing yesterday's list is
     * a launcher you stop trusting. Registered here rather than in the manifest because a
     * long-lived service can hold a receiver the system would not otherwise deliver to.
     */
    private val packagesChanged = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: android.content.Intent) {
            intent.data?.schemeSpecificPart?.let { IconCache.forget(it) }
            LauncherWidgetProvider.refresh(this@ToolsService)
        }
    }

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

        registerReceiver(
            packagesChanged,
            android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_PACKAGE_ADDED)
                addAction(android.content.Intent.ACTION_PACKAGE_REMOVED)
                addAction(android.content.Intent.ACTION_PACKAGE_REPLACED)
                addAction(android.content.Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
        )

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
        getSystemService(DisplayManager::class.java)?.registerDisplayListener(displayChanged, handler)
        syncZones()
    }

    override fun onDestroy() {
        handler.removeCallbacks(giveUp)
        runCatching { unregisterReceiver(packagesChanged) }
        runCatching { getSystemService(DisplayManager::class.java)?.unregisterDisplayListener(displayChanged) }
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

    /**
     * Build exactly the zones that have something to do, at the size the panel is now.
     *
     * A zone with no action assigned does not exist: an invisible window that takes touches and
     * does nothing is the worst thing this app could leave on a screen. Switched off, there are
     * none at all. The flash zone is also built for only the gestures it carries, so a single tap
     * does not wait to see whether a double tap follows when no double tap is assigned.
     */
    private fun syncZones() {
        if (windows == null) return
        val on = prefs.enabled
        val (w, h) = uprightPanelSize()
        val wantedFlash =
            if (on) FLASH_GESTURES.filterTo(mutableSetOf()) { prefs.actionFor(it) != Action.NONE } else emptySet()
        val wantedCorner =
            if (on && prefs.actionFor(Gesture.SWIPE_UP) != Action.NONE) setOf(Gesture.SWIPE_UP) else emptySet()
        val relayout = (w to h) != laidOutFor

        if (relayout || wantedFlash != flashGestures) {
            removeZone(flash)
            flash = if (wantedFlash.isEmpty()) null else addZone(Zones.FLASH.scaledTo(w, h), wantedFlash)
            flashGestures = wantedFlash
        }
        if (relayout || wantedCorner != cornerGestures) {
            removeZone(corner)
            corner = if (wantedCorner.isEmpty()) null else addZone(Zones.CORNER.scaledTo(w, h), wantedCorner)
            cornerGestures = wantedCorner
        }
        laidOutFor = w to h

        if (!on) {
            dropRotation()
            launcher?.dismiss()
            launcher = null
        }
    }

    /**
     * The cover panel's size now, turned upright. Now rather than physical, so a custom
     * resolution is honoured; upright, so a rotation is not mistaken for one.
     */
    private fun uprightPanelSize(): Pair<Int, Int> {
        val bounds = windows?.maximumWindowMetrics?.bounds
        val w = bounds?.width() ?: CoverDisplay.WIDTH_PX
        val h = bounds?.height() ?: CoverDisplay.HEIGHT_PX
        return if (w > h) h to w else w to h
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
        if (!prefs.enabled) return
        val action = prefs.actionFor(gesture)
        if (action == Action.NONE) return
        if (prefs.haptics) tick()
        when (action) {
            Action.RECENTS -> openRecents()
            Action.LAUNCHER -> openLauncher()
            Action.ROTATE -> if (BuildConfig.HAS_ROTATION) toggleRotation()
            Action.NONE -> Unit
        }
    }

    private fun openRecents() {
        // Rotation comes off first, and stays off.
        //
        // Rotating while the switcher is open corrupts Samsung's launcher permanently: the cards
        // keep rendering against the wrong geometry afterwards and nothing brings them back but
        // restarting the launcher, which an ordinary app cannot do — am kill will not touch it,
        // because it is the home process. Since the damage cannot be repaired it has to be
        // avoided, so the switcher is never allowed to be on screen while the panel can turn.
        //
        // Two taps puts rotation back. That is deliberate: an automatic restore would have to
        // guess when the switcher closed, and this is the exact guess that has already proved
        // unreliable — the cover home announces itself from behind the switcher while it is still
        // in use.
        dropRotation()

        // The switcher opens at the panel's own density, every single time.
        //
        // Not "assume there is no override" — clear one. An override left behind by anything at
        // all, an earlier build, a crash, an experiment, makes the cards the wrong size against
        // thumbnails captured at another one, and the result reads as a broken switcher rather
        // than as a setting somebody forgot to put back.
        forceNativeDensity()
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
        launcher?.let { open ->
            launcher = null
            if (open.isShowing) {
                open.dismiss()
                return
            }
        }
        // A new host every time. A host's lifecycle and saved state are spent when it is
        // dismissed, and showing it again throws — which is why the launcher opened once and then
        // only buzzed.
        val host = OverlayHost(this, display)
        val shown = host.show {
            MiniToolsTheme {
                LauncherOverlay(onDismiss = {
                    host.dismiss()
                    if (launcher === host) launcher = null
                })
            }
        }
        if (shown) launcher = host
    }

    private fun dropRotation() {
        val display = coverDisplay ?: return
        if (Rotation.isOn) Rotation.disable(this, display)
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
     * Put the cover panel back to its own density and leave it there.
     *
     * Cheap, idempotent, and a no-op without the permission — in which case there was never an
     * override of ours to clear anyway.
     */
    private fun forceNativeDensity() {
        val displayId = coverDisplay?.displayId ?: return
        if (!Density.permitted(this)) return
        Density.restore(this, displayId)
        prefs.densityApplied = false
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

        private val FLASH_GESTURES = listOf(Gesture.TAP, Gesture.DOUBLE_TAP, Gesture.HOLD)

        /** Long enough for the switcher to appear, short enough not to strand the panel. */
        private const val GIVE_UP_MS = 8_000L

        @Volatile
        private var instance: ToolsService? = null

        /** Whether the service is enabled and running, which is the app's only real precondition. */
        fun isRunning(): Boolean = instance != null
    }
}
