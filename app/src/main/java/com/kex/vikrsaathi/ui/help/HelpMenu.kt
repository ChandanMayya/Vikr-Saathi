package com.kex.vikrsaathi.ui.help

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import com.kex.vikrsaathi.R

fun Fragment.installHelpMenu(screen: HelpScreen) {
    installHelpMenu { screen }
}

fun Fragment.installHelpMenu(screenProvider: () -> HelpScreen) {
    requireActivity().addMenuProvider(object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_help, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (menuItem.itemId == R.id.action_help) {
                HelpOverlay.show(requireActivity(), screenProvider())
                return true
            }
            return false
        }
    }, viewLifecycleOwner, Lifecycle.State.RESUMED)
}

fun Fragment.handleHelpMenuItem(menuItem: MenuItem, screen: HelpScreen): Boolean {
    if (menuItem.itemId == R.id.action_help) {
        HelpOverlay.show(requireActivity(), screen)
        return true
    }
    return false
}

fun FragmentActivity.dismissHelpIfShowing(): Boolean = HelpOverlay.dismissIfShowing(this)
