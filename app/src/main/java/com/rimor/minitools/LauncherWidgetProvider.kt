package com.rimor.minitools

import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * The launcher's own entry in the Good Lock widget carousel.
 *
 * It gets one rather than hanging off the toolbox: a launcher you have to open a settings screen
 * to reach is a launcher nobody reaches.
 */
class LauncherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val intent = Intent(context, LauncherActivity::class.java)
        val options = ActivityOptions.makeBasic()
            .apply { launchDisplayId = CoverDisplay.idOrDefault(context) }
            .toBundle()
        val pending = PendingIntent.getActivity(
            context,
            12,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            options,
        )
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.launcher_widget)
            views.setOnClickPendingIntent(R.id.launcher_widget_root, pending)
            manager.updateAppWidget(id, views)
        }
    }
}
