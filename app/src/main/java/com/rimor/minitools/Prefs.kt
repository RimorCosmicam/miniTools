package com.rimor.minitools

import android.content.Context
import android.content.SharedPreferences

/**
 * What the user chose, and nothing else.
 *
 * Both gestures are on out of the box. They do not overlap — opposite corners of the panel,
 * different actions — so there is no reason to make somebody pick one before the app does
 * anything, and either can be turned off once they know which one their thumb prefers.
 */
class Prefs(context: Context) {

    private val store: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var cornerSwipe: Boolean
        get() = store.getBoolean(KEY_CORNER, true)
        set(value) = store.edit().putBoolean(KEY_CORNER, value).apply()

    var flashPress: Boolean
        get() = store.getBoolean(KEY_FLASH, true)
        set(value) = store.edit().putBoolean(KEY_FLASH, value).apply()

    /** Whether the first run has been seen. The tour is the only place the gestures are named. */
    var onboarded: Boolean
        get() = store.getBoolean(KEY_ONBOARDED, false)
        set(value) = store.edit().putBoolean(KEY_ONBOARDED, value).apply()

    var haptics: Boolean
        get() = store.getBoolean(KEY_HAPTICS, true)
        set(value) = store.edit().putBoolean(KEY_HAPTICS, value).apply()

    // ---- the launcher -------------------------------------------------------------------

    var sortOrder: SortOrder
        get() = SortOrder.from(store.getString(KEY_SORT, null))
        set(value) = store.edit().putString(KEY_SORT, value.name).apply()

    /** The card behind the grid. Turning it off leaves the icons on the wallpaper. */
    var launcherBackground: Boolean
        get() = store.getBoolean(KEY_BACKGROUND, true)
        set(value) = store.edit().putBoolean(KEY_BACKGROUND, value).apply()

    /** The word "LAUNCHER" over the grid. */
    var launcherTitle: Boolean
        get() = store.getBoolean(KEY_TITLE, true)
        set(value) = store.edit().putBoolean(KEY_TITLE, value).apply()

    /** Lifted to the front whatever the sort. */
    var favourites: Set<String>
        get() = store.getStringSet(KEY_FAVOURITES, emptySet()).orEmpty()
        set(value) = store.edit().putStringSet(KEY_FAVOURITES, value).apply()

    /** Not listed at all. */
    var hidden: Set<String>
        get() = store.getStringSet(KEY_HIDDEN, emptySet()).orEmpty()
        set(value) = store.edit().putStringSet(KEY_HIDDEN, value).apply()

    /**
     * When each app was last opened *through miniTools*.
     *
     * This is the fallback for "recent use" when the system's usage access has not been granted.
     * It only knows about launches from this grid, which is less than the truth but is honest
     * about itself and costs no permission at all.
     */
    fun recordLaunch(packageName: String) {
        store.edit().putLong(KEY_USED_PREFIX + packageName, System.currentTimeMillis()).apply()
    }

    fun ownLaunchTimes(): Map<String, Long> =
        store.all.entries
            .filter { it.key.startsWith(KEY_USED_PREFIX) && it.value is Long }
            .associate { it.key.removePrefix(KEY_USED_PREFIX) to it.value as Long }

    /**
     * The density the switcher is shown at, or 0 for the panel's own.
     *
     * Native by default, and deliberately. 370 is the better-looking figure — it was arrived at
     * by looking at the panel — but an override cannot be put back reliably: the switcher's task
     * is translucent, so the cover home stays resumed behind it and announces itself while the
     * switcher is still on screen. "You left" and "the home behind you spoke" are the same event,
     * so the density gets restored a second or two after it is set, mid-use.
     *
     * A density that flickers is worse than one that is merely larger than you wanted, so the
     * override is opt-in and the panel is left alone unless somebody asks.
     */
    var switcherDensity: Int
        get() = store.getInt(KEY_DENSITY, DEFAULT_DENSITY)
        set(value) = store.edit().putInt(KEY_DENSITY, value).apply()

    /**
     * Whether an override of ours is currently in force.
     *
     * A display density is global and sticky, so if miniTools dies holding one the cover screen
     * stays that way. This is the note it leaves itself: on the next service connect, or the next
     * boot, an override that is still recorded here gets cleared. It is also why the restore only
     * ever undoes something we set, rather than clearing whatever it finds.
     */
    var densityApplied: Boolean
        get() = store.getBoolean(KEY_DENSITY_APPLIED, false)
        set(value) = store.edit().putBoolean(KEY_DENSITY_APPLIED, value).apply()

    fun observe(onChange: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        store.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun stopObserving(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        store.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private companion object {
        const val NAME = "minitools"
        const val KEY_CORNER = "gesture_corner_swipe"
        const val KEY_FLASH = "gesture_flash_press"
        const val KEY_HAPTICS = "haptics"
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_SORT = "launcher_sort"
        const val KEY_BACKGROUND = "launcher_background"
        const val KEY_TITLE = "launcher_title"
        const val KEY_FAVOURITES = "launcher_favourites"
        const val KEY_HIDDEN = "launcher_hidden"
        const val KEY_USED_PREFIX = "used/"
        const val KEY_DENSITY = "switcher_density"
        const val KEY_DENSITY_APPLIED = "switcher_density_applied"
        const val DEFAULT_DENSITY = 0
    }
}
