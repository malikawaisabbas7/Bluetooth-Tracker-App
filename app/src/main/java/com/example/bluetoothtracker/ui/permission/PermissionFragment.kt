package com.example.bluetoothtracker.ui.permission

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.bluetoothtracker.R
import com.example.bluetoothtracker.core.PermissionHelper
import com.example.bluetoothtracker.databinding.FragmentPermissionBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PermissionFragment : Fragment(R.layout.fragment_permission) {

    private var _binding: FragmentPermissionBinding? = null
    private val binding get() = _binding!!

    private var navigating = false
    private var permanentlyDenied = false
    private var openedAppSettings = false

    private val mode: String
        get() = arguments?.getString(ARG_MODE) ?: MODE_FIRST_LAUNCH

    private val isScanRetry: Boolean
        get() = mode == MODE_SCAN_RETRY

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val ctx = context ?: return@registerForActivityResult
            if (PermissionHelper.hasAll(ctx)) {
                complete(granted = true)
                return@registerForActivityResult
            }

            if (!isScanRetry) {
                navigateToHome()
                return@registerForActivityResult
            }

            val missing = PermissionHelper.missingPermissions(ctx)
            permanentlyDenied = missing.any { perm ->
                !shouldShowRequestPermissionRationale(perm)
            }
            updateActionState()
            if (!permanentlyDenied) {
                Toast.makeText(ctx, R.string.permission_needed, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPermissionBinding.bind(view)
        navigating = false
        permanentlyDenied = false
        updateActionState()

        if (PermissionHelper.hasAll(requireContext())) {
            complete(granted = true)
            return
        }

        binding.btnAllowPermissions.setOnClickListener {
            val ctx = requireContext()
            val missing = PermissionHelper.missingPermissions(ctx)
            if (missing.isEmpty()) {
                complete(granted = true)
            } else if (permanentlyDenied) {
                openedAppSettings = true
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", ctx.packageName, null)
                    )
                )
            } else {
                permissionLauncher.launch(missing)
            }
        }

        binding.btnNotNow.setOnClickListener {
            complete(granted = false)
        }
    }

    override fun onResume() {
        super.onResume()
        if (openedAppSettings) {
            openedAppSettings = false
            val ctx = context ?: return
            if (PermissionHelper.hasAll(ctx)) {
                complete(granted = true)
            } else {
                permanentlyDenied = PermissionHelper.missingPermissions(ctx).any { perm ->
                    !shouldShowRequestPermissionRationale(perm)
                }
                updateActionState()
            }
        }
    }

    private fun updateActionState() {
        if (_binding == null) return
        binding.tvPermanentDenialHint.isVisible = permanentlyDenied
        binding.btnAllowPermissions.setText(
            if (permanentlyDenied) R.string.open_settings else R.string.allow_permissions
        )
    }

    private fun complete(granted: Boolean) {
        if (isScanRetry) {
            finishScanRetry(granted)
        } else {
            navigateToHome()
        }
    }

    private fun finishScanRetry(granted: Boolean) {
        if (navigating) return
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.permissionFragment) {
            navigating = true
            navController.previousBackStackEntry
                ?.savedStateHandle
                ?.set(KEY_SCAN_PERMISSION_RESULT, granted)
            navController.popBackStack()
        }
    }

    private fun navigateToHome() {
        if (navigating) return
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.permissionFragment) {
            navigating = true
            navController.navigate(R.id.action_permission_to_home)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_MODE = "mode"
        const val MODE_FIRST_LAUNCH = "FIRST_LAUNCH"
        const val MODE_SCAN_RETRY = "SCAN_RETRY"
        const val KEY_SCAN_PERMISSION_RESULT = "scan_permission_result"
    }
}
