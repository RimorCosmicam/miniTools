package com.rimor.minitools

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/**
 * When each app was last used, according to the system.
 *
 * "Recent use" is the one sort order that cannot be answered from the package manager, and the
 * only way to answer it properly is usage access — a special permission, granted in Settings, and
 * far more than a launcher should demand before it will sort. So it is optional: without it the
 * launcher falls back to the launches it made itself, which is a smaller truth honestly obtained.
 */
object UsageAccess {

    fun granted(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun lastUsed(context: Context, fallback: Map<String, Long>): Map<String, Long> {
        if (!granted(context)) return fallback
        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return fallback
        val now = System.currentTimeMillis()
        val stats = runCatching {
            usage.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - WINDOW_MS, now)
        }.getOrNull().orEmpty()
        if (stats.isEmpty()) return fallback
        val system = stats
            .groupBy { it.packageName }
            .mapValues { (_, entries) -> entries.maxOf { it.lastTimeUsed } }
        // Our own record still counts where the system has nothing to say.
        return fallback + system
    }

    /** A month is long enough to order a launcher and short enough to stay cheap to query. */
    private const val WINDOW_MS = 30L * 24 * 60 * 60 * 1000
}
