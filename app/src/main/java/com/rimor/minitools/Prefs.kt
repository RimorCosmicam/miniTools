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

    var haptics: Boolean
        get() = store.getBoolean(KEY_HAPTICS, true)
        set(value) = store.edit().putBoolean(KEY_HAPTICS, value).apply()

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
    }
}
