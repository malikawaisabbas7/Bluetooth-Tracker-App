
package com.example.bluetoothtracker.ui

import android.app.Dialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.SystemClock
import android.os.Trace
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import com.example.bluetoothtracker.App
import com.example.bluetoothtracker.R
import com.example.bluetoothtracker.core.LocaleHelper
import com.example.bluetoothtracker.core.PendingLanguageReturn
import com.example.bluetoothtracker.core.PermissionHelper
import com.example.bluetoothtracker.core.SplashDebug
import com.example.bluetoothtracker.core.SplashDebugThemeDiag
import com.example.bluetoothtracker.core.ThemeTransitionDebug
import com.example.bluetoothtracker.data.settings.SettingsRepository
import com.example.bluetoothtracker.databinding.DialogExitBinding
import com.example.bluetoothtracker.domain.model.AppTheme
import com.example.bluetoothtracker.service.TrackingService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository
    private var appliedTheme: AppTheme = AppTheme.BLUE

    override fun onCreate(savedInstanceState: Bundle?) {
        Trace.beginSection("MainActivity.onCreate")
        val onCreateStartMs = SystemClock.elapsedRealtime()

        // Read saved theme before creating the UI.
        Trace.beginSection("MainActivity.themeLoad")
        val savedTheme = applicationContext
            .getSharedPreferences("theme_cache", MODE_PRIVATE)
            .getString("selected_theme", AppTheme.BLUE.name)

        appliedTheme = runCatching {
            AppTheme.valueOf(savedTheme ?: AppTheme.BLUE.name)
        }.getOrDefault(AppTheme.BLUE)

        // Apply the selected theme before super.onCreate() and layout inflation.
        val themeRes = themeStyle(appliedTheme)
        setTheme(themeRes)
        val themeLoadMs = SystemClock.elapsedRealtime() - onCreateStartMs
        Trace.endSection()

        Trace.beginSection("MainActivity.superOnCreate")
        val superStartMs = SystemClock.elapsedRealtime()
        super.onCreate(savedInstanceState)
        val superCreateMs = SystemClock.elapsedRealtime() - superStartMs
        Trace.endSection()

        val winBg = SplashDebugThemeDiag.describeWindowBackground(this)
        val scales = SplashDebugThemeDiag.animationScales(this)
        ThemeTransitionDebug.log(
            this,
            "MainActivity#${System.identityHashCode(this)} onCreate theme=$appliedTheme " +
                "winBg=$winBg scales=[$scales] locale=${LocaleHelper.currentLanguage().tag} " +
                "savedState=${savedInstanceState != null} recreations=${SplashDebugThemeDiag.currentRecreationCount()}"
        )

        val initialLightColor = resolveThemeColor(R.attr.themeLightColor)
        window.setBackgroundDrawable(ColorDrawable(initialLightColor))
        window.decorView.setBackgroundColor(initialLightColor)

        Trace.beginSection("MainActivity.setContentView")
        val inflateStartMs = SystemClock.elapsedRealtime()
        setContentView(R.layout.activity_main)
        val inflateMs = SystemClock.elapsedRealtime() - inflateStartMs
        Trace.endSection()

        SplashDebug.log(
            this,
            "MainActivity onCreate theme=$appliedTheme themeLoadMs=$themeLoadMs " +
                "superCreateMs=$superCreateMs inflateMs=$inflateMs " +
                "sinceAttachMs=${SystemClock.elapsedRealtime() - App.processAttachElapsedMs} " +
                "savedState=${savedInstanceState != null}"
        )

        val rootView = findViewById<View>(R.id.mainRoot)
        rootView?.doOnPreDraw {
            val sinceAttach = SystemClock.elapsedRealtime() - App.processAttachElapsedMs
            ThemeTransitionDebug.log(
                this,
                "MainActivity#${System.identityHashCode(this)} firstPreDrawReady"
            )
            SplashDebug.log(this, "MainActivity firstPreDrawReady sinceAttachMs=$sinceAttach")
        }
        val decor = window.decorView
        val isFreshLaunch = savedInstanceState == null
        val drawListener = object : ViewTreeObserver.OnDrawListener {
            private var logged = false
            override fun onDraw() {
                if (!logged) {
                    logged = true
                    val sinceAttach = SystemClock.elapsedRealtime() - App.processAttachElapsedMs
                    ThemeTransitionDebug.log(
                        this@MainActivity,
                        "MainActivity#${System.identityHashCode(this@MainActivity)} firstFrameDrawn"
                    )
                    SplashDebug.log(this@MainActivity, "MainActivity firstFrameDrawn sinceAttachMs=$sinceAttach")
                    decor.post {
                        decor.viewTreeObserver.removeOnDrawListener(this)
                        (application as? App)?.onFirstFrameDrawn()
                        if (isFreshLaunch) {
                            startBackgroundTrackingIfEnabled()
                        }
                    }
                }
            }
        }
        decor.viewTreeObserver.addOnDrawListener(drawListener)

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as? NavHostFragment
        val navController = navHost?.navController
        if (savedInstanceState != null && PendingLanguageReturn.isPending(this)) {
            if (navController != null) {
                val currentDestId = navController.currentDestination?.id
                val popped = if (currentDestId == R.id.languageFragment) {
                    val didPop = navController.popBackStack(R.id.settingsFragment, false)
                    if (didPop) {
                        navHost.childFragmentManager.executePendingTransactions()
                    }
                    didPop
                } else {
                    false
                }
                PendingLanguageReturn.consumePending(this)
                ThemeTransitionDebug.log(
                    this,
                    "MainActivity#${System.identityHashCode(this)} pendingLanguageReturnConsumed=true " +
                        "poppedToSettings=$popped destAfter=${navController.currentDestination?.label}"
                )
            } else {
                PendingLanguageReturn.clear(this)
                ThemeTransitionDebug.log(
                    this,
                    "MainActivity#${System.identityHashCode(this)} pendingLanguageReturnCleared=true reason=navControllerNull"
                )
            }
        } else if (savedInstanceState == null) {
            PendingLanguageReturn.clear(this)
        }
        ThemeTransitionDebug.log(
            this,
            "MainActivity#${System.identityHashCode(this)} navHostDest=${navController?.currentDestination?.label}"
        )

        applyThemeStatusBarAndInsets()

        setupBackConfirmation()

        observeThemeChanges()
        Trace.endSection()
    }

    private fun startBackgroundTrackingIfEnabled() {
        lifecycleScope.launch {
            val enabled = settingsRepository.settings
                .first()
                .backgroundTracking

            if (
                enabled &&
                PermissionHelper.hasAll(this@MainActivity) &&
                PermissionHelper.isBluetoothEnabled(this@MainActivity)
            ) {
                try {
                    TrackingService.start(this@MainActivity)
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ThemeTransitionDebug.log(this, "MainActivity#${System.identityHashCode(this)} onStart")
    }

    override fun onResume() {
        super.onResume()
        ThemeTransitionDebug.log(this, "MainActivity#${System.identityHashCode(this)} onResume")
    }

    override fun onPause() {
        ThemeTransitionDebug.log(this, "MainActivity#${System.identityHashCode(this)} onPause")
        super.onPause()
    }

    override fun onStop() {
        ThemeTransitionDebug.log(this, "MainActivity#${System.identityHashCode(this)} onStop")
        super.onStop()
    }

    override fun onDestroy() {
        ThemeTransitionDebug.log(this, "MainActivity#${System.identityHashCode(this)} onDestroy")
        super.onDestroy()
    }

    private fun observeThemeChanges() {

        lifecycleScope.launch {

            repeatOnLifecycle(Lifecycle.State.STARTED) {

                settingsRepository.settings
                    .map { it.appTheme }
                    .distinctUntilChanged()
                    .collect { selectedTheme ->

                        ThemeTransitionDebug.log(
                            this@MainActivity,
                            "MainActivity#${System.identityHashCode(this@MainActivity)} " +
                                "themeEmission=$selectedTheme applied=$appliedTheme"
                        )

                        applicationContext
                            .getSharedPreferences("theme_cache", MODE_PRIVATE)
                            .edit()
                            .putString("selected_theme", selectedTheme.name)
                            .apply()

                        if (shouldRecreateForTheme(selectedTheme, appliedTheme)) {
                            appliedTheme = selectedTheme
                            applicationContext
                                .getSharedPreferences("theme_cache", MODE_PRIVATE)
                                .edit()
                                .putString("selected_theme", selectedTheme.name)
                                .commit()
                            applyThemeInPlaceOrRecreate(selectedTheme)
                        }
                    }
            }
        }
    }

    /**
     * Applies [selectedTheme] in place by updating Activity theme, window/root/status-bar colors,
     * and re-inflating only the visible Fragment's view—eliminating Activity.recreate() black
     * flashes at any system animation scale (including 0x).
     */
    private fun applyThemeInPlaceOrRecreate(selectedTheme: AppTheme) {
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as? NavHostFragment
        val childFm = navHost?.childFragmentManager
        val currentFragment = childFm?.primaryNavigationFragment
        val scales = SplashDebugThemeDiag.animationScales(this)
        if (navHost == null || childFm == null || currentFragment == null) {
            val count = SplashDebugThemeDiag.nextRecreationCount()
            val reason = when {
                navHost == null -> "navHostNull"
                childFm == null -> "childFmNull"
                else -> "primaryNavNull(fragments=${childFm.fragments.map { it::class.simpleName }})"
            }
            ThemeTransitionDebug.log(
                this,
                "MainActivity#${System.identityHashCode(this)} path=RECREATE_FALLBACK " +
                    "reason=$reason theme=$selectedTheme scales=[$scales] count=$count"
            )
            recreate()
            return
        }

        ThemeTransitionDebug.log(
            this,
            "MainActivity#${System.identityHashCode(this)} path=IN_PLACE_START " +
                "theme=$selectedTheme scales=[$scales] primaryBefore=${currentFragment::class.simpleName}#${System.identityHashCode(currentFragment)} " +
                "state=${currentFragment.lifecycle.currentState} dest=${navHost.navController.currentDestination?.label}"
        )

        setTheme(themeStyle(selectedTheme))
        val newLightColor = resolveThemeColor(R.attr.themeLightColor)
        window.setBackgroundDrawable(ColorDrawable(newLightColor))
        window.decorView.setBackgroundColor(newLightColor)
        findViewById<View>(R.id.mainRoot)?.setBackgroundColor(newLightColor)
        findViewById<View>(R.id.nav_host)?.setBackgroundColor(newLightColor)

        val statusBarScrim = findViewById<View>(R.id.statusBarScrim)
        if (statusBarScrim != null) {
            updateStatusBarForDestination(navHost.navController.currentDestination?.id, statusBarScrim)
        }

        childFm.beginTransaction()
            .detach(currentFragment)
            .commitNow()
        childFm.beginTransaction()
            .attach(currentFragment)
            .setPrimaryNavigationFragment(currentFragment)
            .commitNow()

        val primaryAfter = childFm.primaryNavigationFragment
        ThemeTransitionDebug.log(
            this,
            "MainActivity#${System.identityHashCode(this)} path=IN_PLACE_COMPLETE " +
                "theme=$selectedTheme primaryAfter=${primaryAfter?.let { "${it::class.simpleName}#${System.identityHashCode(it)}" }} " +
                "dest=${navHost.navController.currentDestination?.label} " +
                "lightBg=0x${Integer.toHexString(newLightColor)} recreations=${SplashDebugThemeDiag.currentRecreationCount()}"
        )
    }

    private fun applyThemeStatusBarAndInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT

        val root = findViewById<View>(R.id.mainRoot)
        val statusBarScrim = findViewById<View>(R.id.statusBarScrim)
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as? NavHostFragment
        val navController = navHost?.navController

        updateStatusBarForDestination(navController?.currentDestination?.id, statusBarScrim)
        navController?.addOnDestinationChangedListener { _, destination, _ ->
            ThemeTransitionDebug.log(
                this,
                "MainActivity#${System.identityHashCode(this)} navDestination=${destination.label}"
            )
            updateStatusBarForDestination(destination.id, statusBarScrim)
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val topBars = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val bottomAndSides = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            if (statusBarScrim.layoutParams.height != topBars.top) {
                statusBarScrim.layoutParams = statusBarScrim.layoutParams.apply {
                    height = topBars.top
                }
            }

            v.setPadding(
                bottomAndSides.left,
                0,
                bottomAndSides.right,
                maxOf(bottomAndSides.bottom, ime.bottom)
            )
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun updateStatusBarForDestination(destinationId: Int?, statusBarScrim: View) {
        val useWhiteStatusBar = destinationId == R.id.homeFragment ||
            destinationId == R.id.premiumFragment ||
            destinationId == R.id.permissionFragment || 
                destinationId == R.id.onboardingFragment
        val statusBarColor = if (useWhiteStatusBar) {
            ContextCompat.getColor(this, R.color.white)
        } else {
            resolveThemeColor(R.attr.themeLightColor)
        }
        statusBarScrim.setBackgroundColor(statusBarColor)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = ColorUtils.calculateLuminance(statusBarColor) > 0.5
        controller.isAppearanceLightNavigationBars = true
    }

    private fun resolveThemeColor(attrRes: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attrRes, typedValue, true)
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColor(this, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }

    private fun themeStyle(theme: AppTheme): Int {
        return when (theme) {
            AppTheme.BLUE -> R.style.Theme_BluetoothTracker_Blue
            AppTheme.ORANGE -> R.style.Theme_BluetoothTracker_Orange
            AppTheme.BLACK -> R.style.Theme_BluetoothTracker_Black
            AppTheme.GREEN -> R.style.Theme_BluetoothTracker_Green
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        window.decorView.layoutDirection = newConfig.layoutDirection
        findViewById<View>(R.id.mainRoot)?.layoutDirection = newConfig.layoutDirection
        ViewCompat.requestApplyInsets(window.decorView)
        ThemeTransitionDebug.log(
            this,
            "MainActivity#${System.identityHashCode(this)} onConfigurationChanged " +
                "locale=${newConfig.locales[0]?.toLanguageTag()} layoutDir=${newConfig.layoutDirection}"
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun setupBackConfirmation() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val navHost = supportFragmentManager
                        .findFragmentById(R.id.nav_host) as? NavHostFragment
                    val controller = navHost?.navController
                    val currentId = controller?.currentDestination?.id
                    if (controller?.previousBackStackEntry == null || currentId == R.id.homeFragment) {
                        showExitConfirmationDialog()
                    } else if (controller.navigateUp() != true) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    private fun showExitConfirmationDialog() {
        val dialogBinding = DialogExitBinding.inflate(layoutInflater)
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(dialogBinding.root)
            setCancelable(true)
            setCanceledOnTouchOutside(true)
            window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(0.65f)
                setGravity(Gravity.CENTER)
            }
        }

        dialogBinding.btnExitYes.setOnClickListener {
            dialog.dismiss()
            finish()
        }
        dialogBinding.btnExitNo.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    companion object {
        internal fun shouldRecreateForTheme(selected: AppTheme, applied: AppTheme): Boolean =
            selected != applied
    }
}
