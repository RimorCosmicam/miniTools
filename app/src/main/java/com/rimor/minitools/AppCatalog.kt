package com.rimor.minitools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

/** One thing that can be launched, reduced to what the grid and the ordering need. */
data class LaunchableApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val installedAt: Long,
)

/** How the grid is ordered. The names are what the control shows, so they are short. */
enum class SortOrder(val label: String) {
    AZ("A–Z"),
    ZA("Z–A"),
    INSTALLED("NEW"),
    USED("USED"),
    ;

    companion object {
        fun from(name: String?): SortOrder = entries.firstOrNull { it.name == name } ?: AZ
    }
}

object AppCatalog {

    fun load(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> =
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            // miniTools does not list itself. A launcher that can launch the launcher it is
            // inside is a loop with a picture of itself in it.
            if (activity.packageName == context.packageName) return@mapNotNull null
            LaunchableApp(
                packageName = activity.packageName,
                activityName = activity.name,
                label = info.loadLabel(pm).toString(),
                installedAt = runCatching {
                    pm.getPackageInfo(activity.packageName, 0).firstInstallTime
                }.getOrDefault(0L),
            )
        }.distinctBy { it.packageName }
    }

    /**
     * The grid's order, as a pure function of what it is given.
     *
     * Hidden apps are dropped, favourites are lifted to the front, and everything is sorted
     * inside its own group — so a favourite that sorts late still sits at the top, and the
     * favourites themselves are still in the order the user asked for rather than in whatever
     * order they happened to be starred.
     */
    fun arrange(
        apps: List<LaunchableApp>,
        order: SortOrder,
        favourites: Set<String>,
        hidden: Set<String>,
        lastUsed: Map<String, Long>,
    ): List<LaunchableApp> {
        val visible = apps.filterNot { it.packageName in hidden }
        val comparator = when (order) {
            SortOrder.AZ -> compareBy(String.CASE_INSENSITIVE_ORDER) { it: LaunchableApp -> it.label }
            SortOrder.ZA -> compareBy(String.CASE_INSENSITIVE_ORDER) { it: LaunchableApp -> it.label }.reversed()
            SortOrder.INSTALLED -> compareByDescending { it: LaunchableApp -> it.installedAt }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
            SortOrder.USED -> compareByDescending { it: LaunchableApp -> lastUsed[it.packageName] ?: 0L }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
        }
        val (starred, rest) = visible.partition { it.packageName in favourites }
        return starred.sortedWith(comparator) + rest.sortedWith(comparator)
    }
}
