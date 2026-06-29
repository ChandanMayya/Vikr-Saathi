package com.kex.vikrsaathi

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.kex.vikrsaathi.databinding.ActivityMainBinding
import com.kex.vikrsaathi.ui.navigation.BackNavigationGuard
import com.kex.vikrsaathi.util.SystemBarInsets

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        SystemBarInsets.applyMainActivity(
            activity = this,
            appBar = binding.appBarLayout,
            content = binding.navHostFragment
        )

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.dashboardFragment)
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    override fun onSupportNavigateUp(): Boolean {
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
}
