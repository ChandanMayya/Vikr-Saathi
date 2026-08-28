package com.kex.vikrsaathi

import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.kex.vikrsaathi.databinding.ActivityMainBinding
import com.kex.vikrsaathi.ui.help.HelpOverlay
import com.kex.vikrsaathi.ui.navigation.BackNavigationGuard
import com.kex.vikrsaathi.ui.security.AppLockGateController
import com.kex.vikrsaathi.util.AppThemeManager
import com.kex.vikrsaathi.util.SystemBarInsets
import com.kex.vikrsaathi.util.ThemeMode

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var lockGate: AppLockGateController
    private var keepSystemSplash = true
    private var splashFinished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { keepSystemSplash }
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as VikrSaathiApp
        lockGate = AppLockGateController(
            activity = this,
            overlayRoot = binding.lockOverlay.root,
            appLockManager = app.appLockManager,
            onUnlocked = {}
        )

        showSplashBranding()
        setSupportActionBar(binding.toolbar)
        SystemBarInsets.applyMainActivity(
            activity = this,
            appBar = binding.appBarLayout,
            content = binding.contentShell
        )

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.dashboardFragment)
        )
        setupActionBarWithNavController(navController, appBarConfiguration)

        onBackPressedDispatcher.addCallback(this) {
            if (lockGate.handleBackPress()) return@addCallback
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }
    }

    override fun onStop() {
        (application as VikrSaathiApp).appLockManager.markBackground()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        val app = application as VikrSaathiApp
        if (app.settingsRepository.themeMode == ThemeMode.AUTO &&
            AppThemeManager.apply(ThemeMode.AUTO)
        ) {
            recreate()
            return
        }
        if (splashFinished && !lockGate.isShowing() && app.appLockManager.shouldLockOnResume()) {
            app.appLockManager.lock()
            lockGate.show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (lockGate.isShowing()) return false
        if (HelpOverlay.dismissIfShowing(this)) return true
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val currentFragment = navHostFragment.childFragmentManager.primaryNavigationFragment
        if (currentFragment is BackNavigationGuard) {
            var navigated = false
            val intercepted = currentFragment.interceptBackNavigation {
                navigated = navController.navigateUp(appBarConfiguration)
            }
            if (intercepted || navigated) return true
        }
        return navController.navigateUp(appBarConfiguration) ||
            super.onSupportNavigateUp()
    }

    private fun showSplashBranding() {
        val overlay = binding.splashOverlay.root
        val content = binding.splashOverlay.splashContent
        content.alpha = 0f

        overlay.post {
            keepSystemSplash = false
            content.animate()
                .alpha(1f)
                .setDuration(SPLASH_FADE_IN_MS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { scheduleSplashDismiss(overlay) }
                .start()
        }
    }

    private fun scheduleSplashDismiss(overlay: View) {
        overlay.postDelayed({
            if (isFinishing) return@postDelayed
            overlay.animate()
                .alpha(0f)
                .setDuration(SPLASH_FADE_OUT_MS)
                .withEndAction {
                    if (!isFinishing) {
                        overlay.isVisible = false
                        splashFinished = true
                        evaluateAppLock()
                    }
                }
                .start()
        }, SPLASH_HOLD_MS)
    }

    private fun evaluateAppLock() {
        val manager = (application as VikrSaathiApp).appLockManager
        if (manager.shouldShowLock()) {
            manager.lock()
            lockGate.show()
        }
    }

    companion object {
        private const val SPLASH_FADE_IN_MS = 500L
        private const val SPLASH_HOLD_MS = 2_000L
        private const val SPLASH_FADE_OUT_MS = 450L
    }
}
