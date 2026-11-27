package com.example.bluetoothtracker.ui.home

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.ScaleAnimation
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.bluetoothtracker.R
import com.example.bluetoothtracker.core.PermissionHelper
import com.example.bluetoothtracker.databinding.FragmentHomeBinding
import com.example.bluetoothtracker.domain.model.DeviceCategory
import com.example.bluetoothtracker.ui.permission.PermissionFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home) {

    private val viewModel: HomeViewModel by viewModels()
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Animator for smooth IN/OUT pulse on the Start Scan button
    private var scanContinuousAnimator: ObjectAnimator? = null
    private var locationDialog: androidx.appcompat.app.AlertDialog? = null

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (PermissionHelper.isBluetoothEnabled(requireContext())) onScanClicked()
        }

    private val enableLocationLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (PermissionHelper.isLocationEnabled(requireContext())) onScanClicked()
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)
        setupClicks()
        observeState()
        observeScanPermissionResult()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            startContinuousAnimations()
        }
    }

    override fun onPause() {
        scanContinuousAnimator?.cancel()
        _binding?.btnScan?.clearAnimation()

        scanContinuousAnimator = null

        super.onPause()
    }

    private fun setupClicks() = with(binding) {
        btnScan.setOnClickListener { onScanClicked() }
        btnAirTag.setOnClickListener { openList(DeviceCategory.AIR_TAG) }
        btnBluetooth.setOnClickListener { openList(DeviceCategory.BLUETOOTH_DEVICE) }
        ivPremium.setOnClickListener { findNavController().navigate(R.id.action_home_to_premium) }
        ivInstruction.setOnClickListener { findNavController().navigate(R.id.action_home_to_instruction) }
        ivSettings.setOnClickListener { findNavController().navigate(R.id.action_home_to_settings) }
    }

    /** Subtle IN/OUT scale pulse on the Start Scan button; top Bluetooth ring stays static. */
    private fun startContinuousAnimations() {
        val b = _binding ?: return

        b.btnScan.post {
            if (_binding == null) return@post
            b.btnScan.pivotX = b.btnScan.width / 2f
            b.btnScan.pivotY = b.btnScan.height / 2f

            if (ValueAnimator.areAnimatorsEnabled()) {
                if (scanContinuousAnimator?.isRunning != true) {
                    scanContinuousAnimator?.cancel()
                    scanContinuousAnimator = ObjectAnimator.ofPropertyValuesHolder(
                        b.btnScan,
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 0.97f, 1.03f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.97f, 1.03f)
                    ).apply {
                        duration = 900L
                        repeatCount = ValueAnimator.INFINITE
                        repeatMode = ValueAnimator.REVERSE
                        interpolator = AccelerateDecelerateInterpolator()
                        start()
                    }
                }
            } else {
                b.btnScan.clearAnimation()
                val pulse = ScaleAnimation(
                    0.97f, 1.03f,
                    0.97f, 1.03f,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f
                ).apply {
                    duration = 900L
                    repeatCount = Animation.INFINITE
                    repeatMode = Animation.REVERSE
                    interpolator = AccelerateDecelerateInterpolator()
                }
                b.btnScan.startAnimation(pulse)
            }
        }
    }

    private fun render(state: HomeUiState) = with(binding) {
        tvCount.text = getString(R.string.devices_found, state.total)
        tvAirTagCount.text = state.airTagCount.toString()
        tvBluetoothCount.text = state.bluetoothCount.toString()

        val targetLabel = getString(if (state.isScanning) R.string.stop_scan else R.string.start_scan)
        if (tvScanLabel.text.toString() != targetLabel) {
            tvScanLabel.text = targetLabel
        }
    }

    private fun openList(category: DeviceCategory) {
        findNavController().navigate(HomeFragmentDirections.actionHomeToDeviceList(category.name))
    }

    private fun onScanClicked() {
        if (viewModel.isScanning) {
            viewModel.stopScan()
            return
        }
        val context = requireContext()
        when {
            !PermissionHelper.hasAll(context) -> {
                val navController = findNavController()
                if (navController.currentDestination?.id == R.id.homeFragment) {
                    navController.navigate(
                        R.id.action_home_to_permission,
                        bundleOf(PermissionFragment.ARG_MODE to PermissionFragment.MODE_SCAN_RETRY)
                    )
                }
            }
            !PermissionHelper.isBluetoothEnabled(context) ->
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            !PermissionHelper.isLocationEnabled(context) ->
                showLocationEnableDialog()
            else -> viewModel.startScan()
        }
    }

    private fun showLocationEnableDialog() {
        val ctx = context ?: return
        if (locationDialog?.isShowing == true) return
        locationDialog = androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle(R.string.perm_loc_title)
            .setMessage(R.string.perm_loc_desc)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                enableLocationLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton(R.string.not_now, null)
            .show()
    }

    private fun observeScanPermissionResult() {
        val navController = findNavController()
        navController.currentBackStackEntry
            ?.savedStateHandle
            ?.getLiveData<Boolean>(PermissionFragment.KEY_SCAN_PERMISSION_RESULT)
            ?.observe(viewLifecycleOwner) { granted ->
                navController.currentBackStackEntry
                    ?.savedStateHandle
                    ?.remove<Boolean>(PermissionFragment.KEY_SCAN_PERMISSION_RESULT)
                if (granted == true &&
                    viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                ) {
                    onScanClicked()
                }
            }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(it) } }
                launch {
                    viewModel.events.collect { event ->
                        if (event is HomeEvent.ScanFailed) {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.scan_failed, event.reason),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        locationDialog?.dismiss()
        locationDialog = null
        scanContinuousAnimator?.cancel()
        _binding?.btnScan?.clearAnimation()
        scanContinuousAnimator = null

        _binding = null
        super.onDestroyView()
    }
}
