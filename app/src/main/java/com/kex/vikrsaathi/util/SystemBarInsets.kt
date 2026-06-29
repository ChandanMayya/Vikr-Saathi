package com.kex.vikrsaathi.util

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Handles edge-to-edge window insets so bottom actions stay above the 3-button nav bar.
 */
object SystemBarInsets {

    fun applyMainActivity(activity: AppCompatActivity, appBar: View, content: View) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .isAppearanceLightStatusBars = false

        ViewCompat.setOnApplyWindowInsetsListener(appBar) { view, windowInsets ->
            val top = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = top)
            windowInsets
        }

        ViewCompat.setOnApplyWindowInsetsListener(content) { view, windowInsets ->
            val bottom = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.updatePadding(bottom = bottom)
            windowInsets
        }

        ViewCompat.requestApplyInsets(appBar)
        ViewCompat.requestApplyInsets(content)
    }

    fun applyBottomNavigationBarPadding(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val bottom = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.updatePadding(bottom = bottom)
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)
    }

    fun applyFullscreenBottomControls(activity: AppCompatActivity, root: View) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        applyBottomNavigationBarPadding(root)
    }
}
