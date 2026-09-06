package com.rimor.minitools

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/** Starting somebody else's app, on the cover screen rather than on the screen behind your hand. */
object AppLaunch {
    private const val TAG = "miniTools"

    fun open(context: Context, app: LaunchableApp, displayId: Int): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(app.packageName, app.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        val options = ActivityOptions.makeBasic().apply { launchDisplayId = displayId }
        return try {
            context.startActivity(intent, options.toBundle())
            true
        } catch (e: Exception) {
            Log.w(TAG, "could not launch ${app.packageName} on display $displayId", e)
            false
        }
    }
}
