package com.rimor.minitools

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

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
