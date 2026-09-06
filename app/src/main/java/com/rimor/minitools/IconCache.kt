package com.rimor.minitools

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.ConcurrentHashMap

/**
 * App icons, decoded once.
 *
 * Ninety icons is ninety drawable loads and ninety bitmap draws, and doing that during
 * composition means doing it again every time the grid is rebuilt — which is on every sort, every
 * favourite and every scroll back to the top. They do not change while the app is running, so
 * they are kept.
 */
object IconCache {
    private const val SIZE_PX = 144

    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    /** Blocking, and meant to be called off the main thread. */
    fun load(context: Context, packageName: String): ImageBitmap? {
        cache[packageName]?.let { return it }
        val bitmap = runCatching {
            context.packageManager.getApplicationIcon(packageName)
                .toBitmap(SIZE_PX, SIZE_PX)
                .asImageBitmap()
        }.getOrNull() ?: return null
        cache[packageName] = bitmap
        return bitmap
    }

    fun cached(packageName: String): ImageBitmap? = cache[packageName]
}
