package com.example.bluetoothtracker.ui.history

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bluetoothtracker.R
import com.example.bluetoothtracker.databinding.FragmentScanHistoryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ScanHistoryFragment :
    Fragment(R.layout.fragment_scan_history) {

    private var _binding: FragmentScanHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScanHistoryViewModel by viewModels()

    private var historyAdapter: ScanHistoryAdapter? = null

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentScanHistoryBinding.bind(view)

        binding.ivBack.setOnClickListener {
            findNavController().navigateUp()
        }

        val adapter = ScanHistoryAdapter()
        historyAdapter = adapter

        binding.rvHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter

            itemAnimator = null

            setItemViewCacheSize(3)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                viewModel.sightings.collect { sightings ->
                    adapter.submitList(sightings) {
                        viewModel.layoutManagerState?.let { state ->
                            _binding?.rvHistory?.layoutManager?.onRestoreInstanceState(state)
                            viewModel.layoutManagerState = null
                        }
                    }

                    binding.tvLocationDisclaimer.visibility =
                        if (sightings.isEmpty()) View.GONE
                        else View.VISIBLE

                    binding.tvEmpty.visibility =
                        if (sightings.isEmpty()) View.VISIBLE
                        else View.GONE
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        historyAdapter?.resumeAttachedMaps()
    }

    override fun onPause() {
        historyAdapter?.pauseAllMaps()
        viewModel.layoutManagerState = _binding?.rvHistory?.layoutManager?.onSaveInstanceState()
        super.onPause()
    }

    override fun onDestroyView() {
        viewModel.layoutManagerState = _binding?.rvHistory?.layoutManager?.onSaveInstanceState()
            ?: viewModel.layoutManagerState
        historyAdapter?.releaseAllMaps()
        binding.rvHistory.adapter = null
        historyAdapter = null
        _binding = null
        super.onDestroyView()
    }
}
