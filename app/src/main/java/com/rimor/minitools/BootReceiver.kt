package com.rimor.minitools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The other half of the net.
 *
 * A display density override survives a reboot. If the phone went down while the switcher was
 * open, the cover screen would come back up at the switcher's density and stay there, with the
 * service not yet running to notice. So the note miniTools left itself is read here too, as
 * early as the system will let anything run.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = Prefs(context)
        if (!prefs.densityApplied) return
        if (Density.restore(context, CoverDisplay.idOrDefault(context))) {
            prefs.densityApplied = false
        }
    }
}
