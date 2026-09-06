package com.rimor.minitools

import android.content.Context

/**
 * The density of one display, set and put back.
 *
 * Opt-in and off by default — see Prefs.switcherDensity for why putting it back cannot be made
 * reliable, and docs/MEASUREMENTS.md for the events that prove it.
 */
object Density {

    fun permitted(context: Context): Boolean = WindowService.permitted(context)

    fun apply(context: Context, displayId: Int, density: Int): Boolean =
        permitted(context) && density > 0 &&
            WindowService.call("setForcedDisplayDensityForUser", displayId, density, 0)

    fun restore(context: Context, displayId: Int): Boolean =
        permitted(context) &&
            WindowService.call("clearForcedDisplayDensityForUser", displayId, 0)
}
