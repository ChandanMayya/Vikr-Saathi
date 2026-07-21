package com.kex.vikrsaathi.ui.common

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.view.GravityCompat
import androidx.core.view.MenuProvider
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.kex.vikrsaathi.R
import com.kex.vikrsaathi.util.ListViewMode

/**
 * Installs the ⋮ toolbar action that opens an end drawer with View mode radios.
 */
fun Fragment.installListViewOptionsDrawer(
    drawerLayout: DrawerLayout,
    optionsTitleView: TextView,
    titleRes: Int,
    radioViewMode: RadioGroup,
    currentMode: () -> ListViewMode,
    onModeSelected: (ListViewMode) -> Unit
) {
    optionsTitleView.setText(titleRes)
    var suppressRadio = false

    fun syncRadios() {
        suppressRadio = true
        radioViewMode.check(
            when (currentMode()) {
                ListViewMode.COMPACT -> R.id.radioViewCompact
                ListViewMode.DETAILS -> R.id.radioViewDetails
                ListViewMode.COMFORTABLE -> R.id.radioViewComfortable
            }
        )
        suppressRadio = false
    }

    syncRadios()
    radioViewMode.setOnCheckedChangeListener { _, checkedId ->
        if (suppressRadio) return@setOnCheckedChangeListener
        val mode = when (checkedId) {
            R.id.radioViewCompact -> ListViewMode.COMPACT
            R.id.radioViewDetails -> ListViewMode.DETAILS
            else -> ListViewMode.COMFORTABLE
        }
        onModeSelected(mode)
    }

    requireActivity().addMenuProvider(object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_list_screen_options, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (menuItem.itemId == R.id.action_list_options) {
                syncRadios()
                drawerLayout.openDrawer(GravityCompat.END)
                return true
            }
            return false
        }
    }, viewLifecycleOwner, Lifecycle.State.RESUMED)
}
