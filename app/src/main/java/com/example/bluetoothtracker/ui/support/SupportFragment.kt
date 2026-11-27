package com.example.bluetoothtracker.ui.support

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.bluetoothtracker.R
import com.example.bluetoothtracker.BuildConfig
import com.example.bluetoothtracker.databinding.FragmentSupportBinding
import com.google.android.material.color.MaterialColors

class SupportFragment : Fragment(R.layout.fragment_support) {

    private var _binding: FragmentSupportBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentSupportBinding.bind(view)

        binding.ivBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // Set the correct icon state when screen opens.
        updateSendState(
            binding.etMessage.text?.toString().orEmpty()
        )

        // Update icon color whenever message changes.
        binding.etMessage.doAfterTextChanged { text ->
            updateSendState(text?.toString().orEmpty())
        }

        binding.ivSend.setOnClickListener {
            sendEmail()
        }
    }

    private fun isValid(text: String): Boolean {
        return text.trim().length >= MIN_CHARS
    }

    /**
     * Enabled: selected theme primary color.
     * Disabled: grey with reduced opacity.
     */
    private fun updateSendState(text: String) {
        val length = text.trim().length
        binding.tvCharCount.text = "$length / $MIN_CHARS"
        val valid = length >= MIN_CHARS

        val color = if (valid) {

            MaterialColors.getColor(
                binding.ivSend,
                R.attr.themePrimaryColor
            )

        } else {

            ContextCompat.getColor(
                requireContext(),
                R.color.grey
            )
        }

        binding.ivSend.setColorFilter(color)
        binding.ivSend.isEnabled = valid
        binding.ivSend.alpha = if (valid) 1f else 0.5f
    }

    private fun sendEmail() {

        val message = binding.etMessage.text
            ?.toString()
            .orEmpty()
            .trim()

        if (!isValid(message)) {
            binding.etMessage.error =
                getString(R.string.support_hint)
            return
        }

        val mailbox = BuildConfig.SUPPORT_EMAIL
        if (mailbox.isEmpty()) return

        val intent = Intent(Intent.ACTION_SENDTO).apply {

            data = Uri.parse("mailto:")

            putExtra(
                Intent.EXTRA_EMAIL,
                arrayOf(mailbox)
            )

            putExtra(
                Intent.EXTRA_SUBJECT,
                getString(R.string.support_subject)
            )

            putExtra(
                Intent.EXTRA_TEXT,
                message
            )
        }

        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                requireContext(),
                R.string.support_no_email_app,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val MIN_CHARS = 20
    }
}
