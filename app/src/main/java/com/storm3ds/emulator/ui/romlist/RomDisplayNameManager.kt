package com.storm3ds.emulator.ui.romlist

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

object RomDisplayNameManager {
    var currentMode: String = "file_name"
        private set

    // Keep strong reference because SharedPreferences stores listeners in a WeakHashMap.
    private var prefListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun init(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        currentMode = prefs.getString("rom_display_name_mode", "file_name") ?: "file_name"

        prefListener?.let { prefs.unregisterOnSharedPreferenceChangeListener(it) }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == "rom_display_name_mode") {
                currentMode = sp.getString("rom_display_name_mode", "file_name") ?: "file_name"
            }
        }
        prefListener = listener
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }
}
