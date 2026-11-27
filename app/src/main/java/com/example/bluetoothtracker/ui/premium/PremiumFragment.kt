package com.example.bluetoothtracker.ui.premium

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.bluetoothtracker.R
import com.example.bluetoothtracker.data.billing.PremiumOffer
import com.example.bluetoothtracker.data.billing.PremiumUiState
import com.example.bluetoothtracker.databinding.FragmentPremiumBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PremiumFragment : Fragment(R.layout.fragment_premium) {
    private val viewModel: PremiumViewModel by viewModels()
    private var _binding: FragmentPremiumBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPremiumBinding.bind(view)

        binding.ivClose.setOnClickListener { findNavController().navigateUp() }
        binding.btnTrial.setOnClickListener {
            if (!binding.btnTrial.isEnabled) return@setOnClickListener
            when (viewModel.uiState.value) {
                is PremiumUiState.Error -> viewModel.retry()
                is PremiumUiState.ProductAvailable -> viewModel.startTrial(requireActivity())
                else -> Unit
            }
        }

        renderUi(viewModel.uiState.value)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { renderUi(it) } }
                launch {
                    viewModel.messages.collect { messageRes ->
                        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun renderUi(state: PremiumUiState) {
        val currentBinding = _binding ?: return
        when (state) {
            is PremiumUiState.Loading -> {
                currentBinding.tvPrice.text = getString(R.string.price_loading)
                currentBinding.btnTrial.isEnabled = false
                currentBinding.btnTrial.alpha = 0.6f
                currentBinding.btnTrial.text = getString(R.string.subscribe_now)
            }
            is PremiumUiState.ProductAvailable -> {
                currentBinding.tvPrice.text = formatOfferPrice(state.offer)
                currentBinding.btnTrial.isEnabled = true
                currentBinding.btnTrial.alpha = 1f
                currentBinding.btnTrial.text = if ((state.offer.trialDays ?: 0) > 0) {
                    getString(R.string.start_free_trial)
                } else {
                    getString(R.string.subscribe_now)
                }
            }
            is PremiumUiState.ProductUnavailable -> {
                currentBinding.tvPrice.text = getString(R.string.billing_unavailable)
                currentBinding.btnTrial.isEnabled = false
                currentBinding.btnTrial.alpha = 0.6f
                currentBinding.btnTrial.text = getString(R.string.subscribe_now)
            }
            is PremiumUiState.Error -> {
                currentBinding.tvPrice.text = getString(state.messageRes)
                currentBinding.btnTrial.isEnabled = true
                currentBinding.btnTrial.alpha = 1f
                currentBinding.btnTrial.text = getString(R.string.retry)
            }
            is PremiumUiState.PurchasePending -> {
                currentBinding.tvPrice.text = getString(R.string.purchase_pending)
                currentBinding.btnTrial.isEnabled = false
                currentBinding.btnTrial.alpha = 0.6f
                currentBinding.btnTrial.text = getString(R.string.purchase_pending_button)
            }
            is PremiumUiState.Active -> {
                currentBinding.tvPrice.text = state.offer?.let(::formatOfferPrice)
                    ?: getString(R.string.premium_active)
                currentBinding.btnTrial.isEnabled = false
                currentBinding.btnTrial.alpha = 0.6f
                currentBinding.btnTrial.text = getString(R.string.premium_active)
            }
        }
    }

    private fun formatOfferPrice(offer: PremiumOffer): String = when {
        (offer.trialDays ?: 0) > 0 -> getString(
            R.string.price_line_trial,
            offer.price,
            getString(offer.periodRes),
            offer.trialDays
        )
        else -> getString(R.string.price_line, offer.price, getString(offer.periodRes))
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
