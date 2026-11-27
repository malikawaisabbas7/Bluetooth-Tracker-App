package com.example.bluetoothtracker.ui.splash

import android.animation.ValueAnimator
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.bluetoothtracker.R
import com.example.bluetoothtracker.core.Constants
import com.example.bluetoothtracker.core.SplashDebug
import com.example.bluetoothtracker.data.settings.AppSettings
import com.example.bluetoothtracker.data.settings.SettingsRepository
import com.example.bluetoothtracker.databinding.FragmentSplashBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SplashFragment : Fragment(R.layout.fragment_splash) {

    @Inject lateinit var settingsRepository: SettingsRepository

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!

    private val stateMachine = SplashStateMachine()
    private var initJob: Job? = null
    private var cachedSettings: AppSettings? = null
    private var presentationStartElapsedMs: Long = 0L

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentSplashBinding.bind(view)
        SplashDebug.log(context, "SplashFragment onViewCreated")

        updateProgress(0)
        loadStartupSettings()
    }

    override fun onStart() {
        super.onStart()
        if (stateMachine.hasNavigated) return

        val radarView = _binding?.radarSweep ?: return
        radarView.doOnPreDraw { laidOutRadar ->
            if (_binding == null || !isAdded || stateMachine.hasNavigated) return@doOnPreDraw
            SplashDebug.log(
                context,
                "Splash firstPreDraw ready size=${laidOutRadar.width}x${laidOutRadar.height}"
            )
            startSplashController()
        }
    }

    override fun onStop() {
        if (!stateMachine.hasNavigated) {
            stateMachine.onCancelled()
            _binding?.radarSweep?.stopRadar(notifyCancel = true)
            SplashDebug.log(context, "Splash frameCallbackCancelled reason=onStop")
        }
        super.onStop()
    }

    private fun loadStartupSettings() {
        initJob?.cancel()
        initJob = viewLifecycleOwner.lifecycleScope.launch {
            val settings = settingsRepository.settings.first()
            cachedSettings = settings
            SplashDebug.log(
                context,
                "Splash initializationReady languageSelected=${settings.languageSelected} " +
                    "onboardingCompleted=${settings.onboardingCompleted}"
            )
            if (stateMachine.onInitializationReady()) {
                executeNavigation(settings)
            }
        }
    }

    private fun startSplashController() {
        if (stateMachine.hasNavigated) return
        val b = _binding ?: return

        stateMachine.resetForStart()
        updateProgress(0)

        val animatorsEnabled = ValueAnimator.areAnimatorsEnabled()
        val durationScale = runCatching {
            Settings.Global.getFloat(
                requireContext().contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
        }.getOrDefault(1.0f)

        SplashDebug.log(
            context,
            "Splash engine=INDEPENDENT_CUSTOM_TIMELINE configuredDurationMs=${Constants.SPLASH_DURATION_MS} " +
                "systemAnimatorsEnabled=$animatorsEnabled systemDurationScale=$durationScale"
        )

        presentationStartElapsedMs = SystemClock.elapsedRealtime()
        SplashDebug.log(context, "Splash presentationStart sweeps=${SplashStateMachine.RADAR_SWEEPS}")

        b.radarSweep.startSweep(
            sweeps = SplashStateMachine.RADAR_SWEEPS,
            durationMs = Constants.SPLASH_DURATION_MS,
            onProgress = { rawFraction ->
                val milestone = stateMachine.onProgress(rawFraction)
                updateProgress(stateMachine.lastProgressPercent)
                if (milestone != null) {
                    SplashDebug.log(context, "Splash progress=$milestone%")
                }
            },
            onCancel = {
                stateMachine.onCancelled()
                SplashDebug.log(context, "Splash frameCallbackCancelled reason=stopRadar")
            },
            onComplete = {
                val actualElapsed = SystemClock.elapsedRealtime() - presentationStartElapsedMs
                SplashDebug.log(
                    context,
                    "Splash radarCompleted actualElapsedMs=$actualElapsed"
                )
                updateProgress(100)
                if (stateMachine.onAnimationCompleted()) {
                    cachedSettings?.let { executeNavigation(it) }
                }
            }
        )
    }

    private fun updateProgress(percent: Int) {
        val currentBinding = _binding ?: return
        currentBinding.progressBar.progress = percent
        currentBinding.tvLoading.text = getString(R.string.loading_percent, percent)
    }

    private fun executeNavigation(settings: AppSettings) {
        if (!isAdded) return
        val destinationAction = when {
            !settings.languageSelected -> R.id.action_splash_to_language
            !settings.onboardingCompleted -> R.id.action_splash_to_onboarding
            else -> R.id.action_splash_to_home
        }
        SplashDebug.log(context, "Splash navigationRequested action=$destinationAction")
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.splashFragment &&
            stateMachine.markNavigated()
        ) {
            navController.navigate(destinationAction)
            SplashDebug.log(context, "Splash navigationExecuted action=$destinationAction")
        }
    }

    override fun onDestroyView() {
        SplashDebug.log(context, "SplashFragment onDestroyView")
        initJob?.cancel()
        initJob = null
        stateMachine.onCancelled()
        _binding?.radarSweep?.stopRadar(notifyCancel = false)
        _binding = null
        super.onDestroyView()
    }
}
