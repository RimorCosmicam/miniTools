package com.rimor.minitools

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.graphics.drawable.toBitmap

/**
 * The grid behind the widget.
 *
 * A widget cannot run the app's Compose, so the same list is rebuilt here out of RemoteViews.
 * [AppCatalog.arrange] is shared with the activity, so the two surfaces cannot disagree about
 * what order they are in or what is hidden — only about how it is drawn.
 */
class LauncherWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        LauncherGridFactory(applicationContext)
}

private class LauncherGridFactory(private val context: Context) :
    RemoteViewsService.RemoteViewsFactory {

    private var apps: List<LaunchableApp> = emptyList()
    private var favourites: Set<String> = emptySet()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val prefs = Prefs(context)
        favourites = prefs.favourites
        apps = AppCatalog.arrange(
            apps = AppCatalog.load(context),
            order = prefs.sortOrder,
            favourites = favourites,
            hidden = prefs.hidden,
            lastUsed = UsageAccess.lastUsed(context, prefs.ownLaunchTimes()),
        )
    }

    override fun onDestroy() {
        apps = emptyList()
    }

    override fun getCount(): Int = apps.size

    override fun getViewAt(position: Int): RemoteViews {
        val app = apps.getOrNull(position) ?: return RemoteViews(context.packageName, R.layout.launcher_widget_cell)
        val views = RemoteViews(context.packageName, R.layout.launcher_widget_cell)

        // Kept small deliberately. Every cell's bitmap is carried across to the widget host, and
        // a grid this long at full icon resolution is a transaction nobody's launcher wants.
        runCatching {
            context.packageManager.getApplicationIcon(app.packageName).toBitmap(ICON_PX, ICON_PX)
        }.getOrNull()?.let { views.setImageViewBitmap(R.id.cell_icon, it) }

        views.setTextViewText(R.id.cell_label, app.label.uppercase())
        // A favourite is simply the bright one.
        views.setTextColor(
            R.id.cell_label,
            if (app.packageName in favourites) Color.WHITE else DIM,
        )

        // The template carries the display id; this only says which app.
        views.setOnClickFillInIntent(
            R.id.cell_root,
            Intent().apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(app.packageName, app.activityName)
            },
        )
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = apps.getOrNull(position)?.let {
        (it.packageName + "/" + it.activityName).hashCode().toLong()
    } ?: position.toLong()

    override fun hasStableIds(): Boolean = true

    private companion object {
        const val ICON_PX = 96
        const val DIM = 0x94FFFFFF.toInt()
    }
}
