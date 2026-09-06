package com.rimor.minitools

import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * The Good Lock launcher widget, which is how anything gets onto the cover screen at all.
 *
 * Tapping it opens the toolbox on the cover display rather than on the main one — the phone is
 * folded when this is reachable, and an activity that lands on the panel behind your hand is an
 * activity nobody sees.
 */
class CoverWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val intent = Intent(context, CoverActivity::class.java)
        val options = ActivityOptions.makeBasic()
            .apply { launchDisplayId = CoverDisplay.idOrDefault(context) }
            .toBundle()
        val pending = PendingIntent.getActivity(
            context,
            11,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            options,
        )
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.cover_widget)
            views.setOnClickPendingIntent(R.id.cover_widget_root, pending)
            manager.updateAppWidget(id, views)
        }
    }
}
