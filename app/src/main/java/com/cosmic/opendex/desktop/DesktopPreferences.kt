package com.cosmic.opendex.desktop

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Manages persistent desktop configurations (pinned shortcuts, wallpaper settings).
 */
class DesktopPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "opendex_preferences"
        private const val KEY_PINNED_APPS = "key_pinned_desktop_apps"
        private const val KEY_WALLPAPER_TYPE = "key_wallpaper_type"
        private const val KEY_WALLPAPER_PRESET = "key_wallpaper_preset"

        const val WALLPAPER_TYPE_PRESET = "PRESET"
        const val WALLPAPER_TYPE_CUSTOM = "CUSTOM"
    }

    fun getPinnedApps(): List<String> {
        val jsonString = prefs.getString(KEY_PINNED_APPS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isPinned(componentName: String): Boolean {
        return getPinnedApps().contains(componentName)
    }

    fun pinApp(componentName: String) {
        val current = getPinnedApps().toMutableList()
        if (!current.contains(componentName)) {
            current.add(componentName)
            savePinnedApps(current)
        }
    }

    fun unpinApp(componentName: String) {
        val current = getPinnedApps().toMutableList()
        if (current.remove(componentName)) {
            savePinnedApps(current)
        }
    }

    fun savePinnedApps(list: List<String>) {
        val jsonArray = JSONArray()
        for (item in list) {
            jsonArray.put(item)
        }
        prefs.edit().putString(KEY_PINNED_APPS, jsonArray.toString()).apply()
    }

    fun getWallpaperType(): String {
        return prefs.getString(KEY_WALLPAPER_TYPE, WALLPAPER_TYPE_PRESET) ?: WALLPAPER_TYPE_PRESET
    }

    fun getWallpaperPresetIndex(): Int {
        return prefs.getInt(KEY_WALLPAPER_PRESET, 0)
    }

    fun setWallpaperPreset(presetIndex: Int) {
        prefs.edit()
            .putString(KEY_WALLPAPER_TYPE, WALLPAPER_TYPE_PRESET)
            .putInt(KEY_WALLPAPER_PRESET, presetIndex)
            .apply()
    }

    fun setCustomWallpaper() {
        prefs.edit()
            .putString(KEY_WALLPAPER_TYPE, WALLPAPER_TYPE_CUSTOM)
            .apply()
    }
}
