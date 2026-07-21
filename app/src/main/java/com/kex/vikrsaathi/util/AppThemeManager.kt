package com.kex.vikrsaathi.util

import androidx.appcompat.app.AppCompatDelegate
import java.util.Calendar

object AppThemeManager {

    private const val DAY_START_HOUR = 6
    private const val DAY_END_HOUR = 18

    fun apply(mode: ThemeMode): Boolean {
        val targetNightMode = nightModeFor(mode)
        val current = AppCompatDelegate.getDefaultNightMode()
        if (current == targetNightMode) return false
        AppCompatDelegate.setDefaultNightMode(targetNightMode)
        return true
    }

    fun nightModeFor(mode: ThemeMode): Int = when (mode) {
        ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        ThemeMode.AUTO -> if (isDaytime()) {
            AppCompatDelegate.MODE_NIGHT_NO
        } else {
            AppCompatDelegate.MODE_NIGHT_YES
        }
    }

    fun isDaytime(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour in DAY_START_HOUR until DAY_END_HOUR
    }
}
