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

    /**
     * Every launchable activity on the phone, miniTools included.
     *
     * It used to exclude itself, which was right when the launcher was a screen you switched into
     * — opening it from inside itself would have been a loop. Now that the launcher is a card
     * drawn over whatever is already there, miniTools is just another place to go, and leaving it
     * out only meant there was no way to reach its own settings from the panel you had open.
     */
    fun load(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        // Flags 0, emphatically not MATCH_DEFAULT_ONLY. That flag keeps only activities whose
        // filter declares CATEGORY_DEFAULT, and a launcher entry declares MAIN and LAUNCHER — it
        // has no reason to declare DEFAULT and most do not. Asking for it silently returned a
        // fraction of the phone's apps and looked like a visibility problem.
        val resolved: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
        return resolved.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            LaunchableApp(
                packageName = activity.packageName,
                activityName = activity.name,
                label = info.loadLabel(pm).toString(),
                installedAt = runCatching {
                    pm.getPackageInfo(activity.packageName, 0).firstInstallTime
                }.getOrDefault(0L),
            )
        }.distinctBy { it.packageName to it.activityName }
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
