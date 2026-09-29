package com.carthing.ui

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Look-and-feel preferences; observable from Compose, saved in shared preferences. */
class AppearanceSettings(context: Context) {
    private val prefs = context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)

    /** Wallpaper colours exist on Android 12+ only; older phones always use the app's palette. */
    val wallpaperColorsAvailable get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var useWallpaperColors by mutableStateOf(prefs.getBoolean(KEY_WALLPAPER, true))
        private set

    fun setWallpaperColors(enabled: Boolean) {
        useWallpaperColors = enabled
        prefs.edit().putBoolean(KEY_WALLPAPER, enabled).apply()
    }

    private companion object { const val KEY_WALLPAPER = "wallpaper_colors" }
}
