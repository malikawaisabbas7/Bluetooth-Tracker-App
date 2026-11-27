package com.example.bluetoothtracker.ui.devices

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.example.bluetoothtracker.R
import com.example.bluetoothtracker.databinding.FragmentDeviceListBinding
import com.example.bluetoothtracker.domain.model.DeviceCategory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DeviceListFragment : Fragment(R.layout.fragment_device_list) {

    private val viewModel: DeviceListViewModel by viewModels()
    private var _binding: FragmentDeviceListBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceListBinding.bind(view)

        binding.tvTitle.setText(
            when (viewModel.category) {
                DeviceCategory.AIR_TAG -> R.string.air_tag
                DeviceCategory.OTHER_TAG -> R.string.other_tags
                DeviceCategory.BLUETOOTH_DEVICE -> R.string.bluetooth_devices
            }
        )
        binding.ivBack.setOnClickListener { findNavController().navigateUp() }

        val adapter = DeviceAdapter { device ->
            findNavController().navigate(
                DeviceListFragmentDirections.actionDeviceListToDeviceDetail(device.mac)
            )
        }
        binding.rvDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDevices.adapter = adapter

        // Disable item change animations so signal updates do not flicker
        (binding.rvDevices.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.devices.collect { list ->
                    adapter.submitList(list)
                    binding.emptyContainer.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.rvDevices.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
