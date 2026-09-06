package com.rimor.minitools

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

/**
 * The cover display, found rather than assumed.
 *
 * It is display 1 on every Z Flip 7 that has been looked at, and hard-coding that would work
 * today. It is still a runtime fact, and an id that is right until it is not is the kind of
 * thing that breaks on the one phone nobody tested. So it is looked up: the cover screen is
 * the built-in panel that is not the default one, and on this hardware it is 948 x 1048.
 */
object CoverDisplay {
    const val WIDTH_PX = 948
    const val HEIGHT_PX = 1048

    fun find(context: Context): Display? {
        val displays = context.getSystemService(DisplayManager::class.java)?.displays ?: return null
        val candidates = displays.filter { it.displayId != Display.DEFAULT_DISPLAY && it.isValid }
        return candidates.firstOrNull { it.matchesCoverPanel() } ?: candidates.firstOrNull()
    }

    fun idOrDefault(context: Context): Int =
        find(context)?.displayId ?: Display.DEFAULT_DISPLAY

    private fun Display.matchesCoverPanel(): Boolean {
        val w = mode.physicalWidth
        val h = mode.physicalHeight
        return (w == WIDTH_PX && h == HEIGHT_PX) || (w == HEIGHT_PX && h == WIDTH_PX)
    }
}
