package com.example.bluetoothtracker.ui.language

import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.bluetoothtracker.R
import com.example.bluetoothtracker.core.AppLanguage
import com.example.bluetoothtracker.core.LocaleHelper
import com.example.bluetoothtracker.core.PendingLanguageReturn
import com.example.bluetoothtracker.core.ThemeTransitionDebug
import com.example.bluetoothtracker.databinding.FragmentLanguageBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LanguageFragment : Fragment(R.layout.fragment_language) {

    private val viewModel: LanguageViewModel by viewModels()
    private var _binding: FragmentLanguageBinding? = null
    private val binding get() = _binding!!

    private var navigating = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLanguageBinding.bind(view)
        navigating = false
        ThemeTransitionDebug.log(context, "LanguageFragment onViewCreated")
        view.doOnPreDraw {
            ThemeTransitionDebug.log(context, "LanguageFragment firstPreDraw")
        }

        applyBottomBarInsets()

        binding.ivBack.visibility = if (viewModel.isFromSettings) View.VISIBLE else View.GONE
        binding.ivBack.setOnClickListener { findNavController().navigateUp() }

        val languages = AppLanguage.entries
        val adapter = LanguageAdapter(
            items = languages,
            selected = viewModel.pendingLanguage.value
        ) { chosen ->
            // Only update pending selection; never apply locale or recreate Activity on tap.
            viewModel.selectPendingLanguage(chosen)
        }

        val gridManager = GridLayoutManager(requireContext(), 2)
        binding.rvLanguages.layoutManager = gridManager
        binding.rvLanguages.setHasFixedSize(true)
        binding.rvLanguages.itemAnimator = null
        binding.rvLanguages.adapter = adapter

        val savedLayoutState: Parcelable? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            savedInstanceState?.getParcelable(KEY_GRID_STATE, Parcelable::class.java)
        } else {
            @Suppress("DEPRECATION")
            savedInstanceState?.getParcelable(KEY_GRID_STATE)
        }

        if (savedLayoutState != null) {
            gridManager.onRestoreInstanceState(savedLayoutState)
        } else {
            val selectedIndex = languages.indexOf(viewModel.pendingLanguage.value)
            if (selectedIndex > 0) {
                binding.rvLanguages.scrollToPosition(selectedIndex)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pendingLanguage.collect { pending ->
                    adapter.updateSelected(pending)
                    binding.tvToolbarFlag.text = pending.flagEmoji
                    binding.tvToolbarFlag.contentDescription = pending.nativeName
                }
            }
        }

        binding.btnContinue.setOnClickListener {
            if (navigating) return@setOnClickListener
            if (!viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                return@setOnClickListener
            }
            navigating = true
            viewLifecycleOwner.lifecycleScope.launch {
                val ctx = context
                val requestId = PendingLanguageReturn.nextRequestId()
                val currentLang = LocaleHelper.currentLanguage()
                val commitStart = android.os.SystemClock.elapsedRealtime()
                val committed = try {
                    viewModel.commitPendingLanguage()
                } catch (e: Exception) {
                    navigating = false
                    ThemeTransitionDebug.log(ctx, "LanguageFragment req=$requestId persistFailed=${e.message}")
                    return@launch
                }
                val commitMs = android.os.SystemClock.elapsedRealtime() - commitStart
                val navController = findNavController()
                val alreadyActive = ctx?.let { LocaleHelper.isLocaleAlreadyActive(it, committed) } == true
                ThemeTransitionDebug.log(
                    ctx,
                    "LanguageFragment req=$requestId continueClick current=${currentLang.tag} " +
                        "selected=${committed.tag} persistMs=$commitMs isFromSettings=${viewModel.isFromSettings} " +
                        "alreadyActive=$alreadyActive"
                )

                if (viewModel.isFromSettings) {
                    if (alreadyActive) {
                        navController.navigateUp()
                    } else {
                        ctx?.let { PendingLanguageReturn.markPending(it) }
                        ThemeTransitionDebug.log(
                            ctx,
                            "LanguageFragment req=$requestId invoking AppCompatDelegate.setApplicationLocales(${committed.tag})"
                        )
                        LocaleHelper.applyLanguage(committed)
                        val hostActivity = activity
                        if (isAdded && hostActivity != null && !hostActivity.isFinishing && !hostActivity.isDestroyed) {
                            ctx?.let { PendingLanguageReturn.clear(it) }
                            navController.navigateUp()
                        }
                    }
                } else {
                    if (!alreadyActive) {
                        ThemeTransitionDebug.log(
                            ctx,
                            "LanguageFragment req=$requestId firstLaunch applyLanguage(${committed.tag})"
                        )
                        LocaleHelper.applyLanguage(committed)
                    }
                    if (isAdded && navController.currentDestination?.id == R.id.languageFragment) {
                        navController.navigate(R.id.action_language_to_onboarding)
                    }
                }
            }
        }
    }

    private fun applyBottomBarInsets() {
        val baseBottomPadding = (18 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomBar) { v, insets ->
            val navBarBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, baseBottomPadding + navBarBottom)
            insets
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        _binding?.rvLanguages?.layoutManager?.onSaveInstanceState()?.let { state ->
            outState.putParcelable(KEY_GRID_STATE, state)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        binding.rvLanguages.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val KEY_GRID_STATE = "language_grid_state"
    }
}
