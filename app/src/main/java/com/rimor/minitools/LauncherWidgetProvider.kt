package com.rimor.minitools

import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews

/**
 * The launcher's own entry in the Good Lock widget carousel — and the launcher itself.
 *
 * It gets a widget of its own rather than hanging off the toolbox, and the widget holds the grid
 * rather than a card that opens one. On a cover screen the carousel is the home screen; a card
 * there is a swipe and a tap spent on saying the tool's name.
 */
class LauncherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, build(context, id)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_CYCLE_ORDER) {
            val prefs = Prefs(context)
            val entries = SortOrder.entries
            prefs.sortOrder = entries[(prefs.sortOrder.ordinal + 1) % entries.size]
            refresh(context)
        }
    }

    private fun build(context: Context, widgetId: Int): RemoteViews {
        val prefs = Prefs(context)
        val views = RemoteViews(context.packageName, R.layout.launcher_widget)

        // Removing the background leaves the icons standing on the wallpaper.
        views.setInt(
            R.id.launcher_widget_root,
            "setBackgroundColor",
            if (prefs.launcherBackground) Color.BLACK else Color.TRANSPARENT,
        )
        views.setViewVisibility(
            R.id.launcher_widget_title,
            if (prefs.launcherTitle) View.VISIBLE else View.GONE,
        )
        views.setTextViewText(R.id.launcher_widget_order, prefs.sortOrder.label.uppercase())

        val adapter = Intent(context, LauncherWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            // A distinct data uri per widget, or the host reuses one factory for all of them.
            data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.launcher_widget_grid, adapter)
        views.setEmptyView(R.id.launcher_widget_grid, R.id.launcher_widget_empty)

        // One template for every cell, carrying the cover display; the cell's fill-in intent
        // supplies only which app. It has to be MUTABLE — a fill-in that cannot change the intent
        // is a grid where every icon opens the same thing — and from Android 14 a mutable
        // PendingIntent may not wrap an implicit intent. "MAIN/LAUNCHER with no component" is
        // implicit, so the template names our own trampoline, which is not.
        val options = ActivityOptions.makeBasic()
            .apply { launchDisplayId = CoverDisplay.idOrDefault(context) }
            .toBundle()
        val template = PendingIntent.getActivity(
            context,
            21,
            Intent(context, LaunchTrampolineActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            options,
        )
        views.setPendingIntentTemplate(R.id.launcher_widget_grid, template)

        // The order cycles where it is written. The title opens the full launcher, which is
        // where favourites, hiding and the rest of the personalisation live.
        views.setOnClickPendingIntent(
            R.id.launcher_widget_order,
            PendingIntent.getBroadcast(
                context,
                22,
                Intent(context, LauncherWidgetProvider::class.java).setAction(ACTION_CYCLE_ORDER),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        views.setOnClickPendingIntent(
            R.id.launcher_widget_title,
            PendingIntent.getActivity(
                context,
                23,
                Intent(context, LauncherActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                options,
            ),
        )
        return views
    }

    companion object {
        private const val ACTION_CYCLE_ORDER = "com.rimor.minitools.CYCLE_ORDER"

        /**
         * Redraw every launcher widget and tell the grid its contents moved.
         *
         * Called whenever something the widget shows is changed somewhere else — the order, a
         * favourite, a hidden app, the background or the title, all of which can be edited from
         * the full launcher while the widget is sitting behind it.
         */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, LauncherWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            val provider = LauncherWidgetProvider()
            ids.forEach { id -> manager.updateAppWidget(id, provider.build(context, id)) }
            manager.notifyAppWidgetViewDataChanged(ids, R.id.launcher_widget_grid)
        }
    }
}
