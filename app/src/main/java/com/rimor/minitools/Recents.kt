package com.rimor.minitools

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
    fun open(context: Context, displayId: Int): Boolean = try {
        val options = ActivityOptions.makeBasic().apply { launchDisplayId = displayId }
        context.startActivity(intent(), options.toBundle())
        true
    } catch (e: Exception) {
        Log.w(TAG, "recents refused on display $displayId", e)
        false
    }

    /**
     * Undo what a rotation did to Samsung's launcher, without a reboot.
     *
     * The launcher keeps one saved set of window insets and falls back to it whenever it cannot
     * read live ones. On this phone it never can: the cover panel always reports
     * isValidWindowInsets=false, so the saved value is the only value it ever uses. A rotation
     * writes that value while the panel is sideways — the camera cutout's 220 lands on the right
     * instead of the bottom — and every switcher afterwards is laid out against it.
     *
     * Nothing repairs it from the cover side. But the saved value is global, and it is rewritten
     * whenever the launcher does read valid insets. So the switcher is started once on the inner
     * display, which overwrites the sideways insets with upright ones.
     *
     * On a Flip with no inner panel that display is permanently off, and starting an activity
     * there neither wakes it nor disturbs the cover screen — the repair is invisible. The insets
     * it writes are the inner display's rather than the cover's, so the result sits slightly
     * lower than it does after a boot. Slightly low beats sideways.
     */
    fun repair(context: Context): Boolean = try {
        val options = ActivityOptions.makeBasic().apply { launchDisplayId = Display.DEFAULT_DISPLAY }
        context.startActivity(intent(), options.toBundle())
        true
    } catch (e: Exception) {
        Log.w(TAG, "could not repair the switcher", e)
        false
    }

    /**
     * The exact repair, which is not ours to perform.
     *
     * Only the launcher's own start-up recomputes the cover's insets correctly — boot does it,
     * and so does a force-stop. Nothing an ordinary app can reach reproduces it: not a density
     * change, not a virtual display at the same size, not restarting the switcher. Force-stopping
     * another package needs a signature permission.
     *
     * So miniTools opens the page where the button lives and lets the user press it.
     */
    fun openLauncherAppInfo(context: Context, displayId: Int): Boolean = try {
        val options = ActivityOptions.makeBasic().apply { launchDisplayId = displayId }
        context.startActivity(
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.fromParts("package", LAUNCHER_PACKAGE, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            options.toBundle(),
        )
        true
    } catch (e: Exception) {
        Log.w(TAG, "could not open the launcher's app info", e)
        false
    }

    private const val LAUNCHER_PACKAGE = "com.sec.android.app.launcher"

    private fun intent() = Intent(Intent.ACTION_MAIN).apply {
        component = COMPONENT
        addCategory(Intent.CATEGORY_DEFAULT)
        // NEW_TASK because there is no activity behind this, and TASK_ON_HOME so dismissing the
        // switcher falls back to the cover home rather than to whatever launched it.
        //
        // CLEAR_TASK is what makes the list current. Started without it, an existing
        // RecentsActivity is merely resumed — the system's task list has already moved on, but
        // the switcher redraws the one it built when it was last created, so an app you used a
        // moment ago is missing from it. Samsung's own gesture never hits this because it enters
        // through quickstep, which reloads the model on the way in; an explicit start does not.
        // Clearing the task forces a fresh instance, and a fresh instance reads the list again.
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_TASK_ON_HOME,
        )
    }
}
