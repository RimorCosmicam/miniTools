package com.rimor.minitools

import android.app.Activity
import android.os.Bundle

/**
 * The widget's cells launch through here rather than straight at the app they name.
 *
 * A collection widget needs one mutable PendingIntent template that every cell fills in. From
 * Android 14 a mutable PendingIntent may not wrap an implicit intent — and "MAIN/LAUNCHER with no
 * component" is exactly that — so the template points at this activity, which is explicit, and
 * the cells fill in the package and activity as extras instead.
 *
 * It draws nothing and lives for one call. It also gives the widget somewhere to record the
 * launch, which the grid needs for its "recent use" order and could not do from a PendingIntent
 * that went straight to another app.
 */
class LaunchTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent?.getStringExtra(EXTRA_PACKAGE)
        val activityName = intent?.getStringExtra(EXTRA_ACTIVITY)
        if (packageName != null && activityName != null) {
            Prefs(this).recordLaunch(packageName)
            AppLaunch.open(
                context = this,
                app = LaunchableApp(
                    packageName = packageName,
                    activityName = activityName,
                    label = packageName,
                    installedAt = 0L,
                ),
                displayId = CoverDisplay.idOrDefault(this),
            )
        }
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_PACKAGE = "com.rimor.minitools.extra.PACKAGE"
        const val EXTRA_ACTIVITY = "com.rimor.minitools.extra.ACTIVITY"
    }
}
