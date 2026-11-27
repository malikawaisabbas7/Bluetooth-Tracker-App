
package com.example.bluetoothtracker.ui.onboarding

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.example.bluetoothtracker.R
import com.example.bluetoothtracker.core.ThemeTransitionDebug
import com.example.bluetoothtracker.databinding.FragmentOnboardingBinding
import com.google.android.material.color.MaterialColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OnboardingFragment : Fragment(R.layout.fragment_onboarding) {

    private val viewModel: OnboardingViewModel by viewModels()

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = requireNotNull(_binding)

    private var navigating = false

    private var pagerAdapter: OnboardingPagerAdapter? = null

    private val pageChangeCallback =
        object : ViewPager2.OnPageChangeCallback() {

            override fun onPageSelected(position: Int) {
                viewModel.setPage(position)
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentOnboardingBinding.bind(view)

        navigating = false

        ThemeTransitionDebug.log(
            context,
            "OnboardingFragment onViewCreated"
        )

        view.doOnPreDraw {
            ThemeTransitionDebug.log(
                context,
                "OnboardingFragment firstPreDraw"
            )
        }

        setupViewPager()
        setupBackPress()
        setupClickListeners()
        observePageChanges()
    }

    private fun setupViewPager() {
        val adapter = OnboardingPagerAdapter()
        pagerAdapter = adapter

        binding.viewPager.apply {
            this.adapter = adapter
            offscreenPageLimit = 1

            // Restore the saved page without animation.
            setCurrentItem(
                viewModel.pageIndex.value,
                false
            )

            registerOnPageChangeCallback(pageChangeCallback)
        }
    }

    private fun setupBackPress() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {

                    if (!viewModel.previousPage()) {
                        isEnabled = false

                        requireActivity()
                            .onBackPressedDispatcher
                            .onBackPressed()
                    }
                }
            }
        )
    }

    private fun setupClickListeners() {
        binding.tvSkip.setOnClickListener {
            finishOnboarding()
        }

        binding.btnBack.setOnClickListener {
            viewModel.previousPage()
        }

        binding.btnNext.setOnClickListener {

            if (!viewModel.nextPage()) {
                finishOnboarding()
            }
        }
    }

    private fun observePageChanges() {
        viewLifecycleOwner.lifecycleScope.launch {

            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.pageIndex.collect { page ->
                    renderPage(page)
                }
            }
        }
    }

    private fun renderPage(page: Int) {
        val currentBinding = _binding ?: return

        with(currentBinding) {

            if (viewPager.currentItem != page) {
                viewPager.setCurrentItem(page, false)
            }



            tvSkip.visibility =
                if (page < OnboardingViewModel.LAST_PAGE) {
                    View.VISIBLE
                } else {
                    View.INVISIBLE
                }

            btnBack.visibility =
                if (page > 0) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

            btnNext.setText(
                if (page == OnboardingViewModel.LAST_PAGE) {
                    R.string.get_started
                } else {
                    R.string.next
                }
            )

            updatePageIndicators(page)
        }
    }

    private fun updatePageIndicators(page: Int) {
        val currentBinding = _binding ?: return

        val primaryColor = MaterialColors.getColor(
            currentBinding.root,
            R.attr.themePrimaryColor,
            ContextCompat.getColor(
                requireContext(),
                R.color.theme_blue_primary
            )
        )

        val inactiveColor = ContextCompat.getColor(
            requireContext(),
            R.color.grey_light
        )

        val density = resources.displayMetrics.density

        val activeWidth = (24f * density).toInt()
        val inactiveWidth = (8f * density).toInt()

        val dots = listOf(
            currentBinding.dot1,
            currentBinding.dot2,
            currentBinding.dot3
        )

        dots.forEachIndexed { index, dot ->

            val isActive = index == page

            dot.backgroundTintList = ColorStateList.valueOf(
                if (isActive) primaryColor else inactiveColor
            )

            val targetWidth =
                if (isActive) activeWidth else inactiveWidth

            if (dot.layoutParams.width != targetWidth) {
                dot.layoutParams = dot.layoutParams.apply {
                    width = targetWidth
                }
            }
        }
    }

    private fun finishOnboarding() {

        if (navigating) return

        if (!viewLifecycleOwner.lifecycle.currentState.isAtLeast(
                Lifecycle.State.STARTED
            )
        ) {
            return
        }

        navigating = true

        // Preserve the existing completion and navigation behavior.
        viewLifecycleOwner.lifecycleScope.launch {

            viewModel.completeOnboarding()

            val navController = findNavController()

            if (
                navController.currentDestination?.id ==
                R.id.onboardingFragment
            ) {
                navController.navigate(
                    R.id.action_onboarding_to_permission
                )
            }
        }
    }

    override fun onDestroyView() {

        _binding?.viewPager?.apply {
            unregisterOnPageChangeCallback(pageChangeCallback)
            adapter = null
        }

        pagerAdapter = null
        _binding = null

        super.onDestroyView()
    }
}
