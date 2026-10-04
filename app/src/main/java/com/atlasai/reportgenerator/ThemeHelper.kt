package com.atlasai.reportgenerator

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeHelper {
    private const val PREFS = "atlas_ai"
    private const val KEY_DARK = "dark_mode"

    /** true = dark (default), false = light */
    fun isDark(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DARK, false)
    }

    fun setDark(context: Context, dark: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK, dark)
            .apply()
        apply(dark)
    }

    fun toggle(context: Context): Boolean {
        val next = !isDark(context)
        setDark(context, next)
        return next
    }

    fun applySaved(context: Context) {
        apply(isDark(context))
    }

    private fun apply(dark: Boolean) {
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
