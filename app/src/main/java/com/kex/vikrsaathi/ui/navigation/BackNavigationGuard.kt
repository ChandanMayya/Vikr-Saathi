package com.kex.vikrsaathi.ui.navigation

/**
 * Screens that need to confirm before leaving when there are unsaved edits.
 */
interface BackNavigationGuard {
    /**
     * @param navigate invoked when the user chooses to leave without blocking
     * @return true if navigation was intercepted (e.g. dialog shown)
     */
    fun interceptBackNavigation(navigate: () -> Unit): Boolean
}
