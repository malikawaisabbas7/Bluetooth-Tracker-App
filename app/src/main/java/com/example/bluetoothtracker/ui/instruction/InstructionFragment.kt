package com.example.bluetoothtracker.ui.instruction

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.ArrayRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.bluetoothtracker.R
import com.example.bluetoothtracker.databinding.FragmentInstructionBinding
import com.example.bluetoothtracker.databinding.ItemBulletBinding

class InstructionFragment : Fragment(R.layout.fragment_instruction) {

    private var _binding: FragmentInstructionBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentInstructionBinding.bind(view)

        binding.ivBack.setOnClickListener { findNavController().navigateUp() }
        fillBullets(binding.listHow, R.array.instr_how)
        fillBullets(binding.listNotification, R.array.instr_notification)
        fillBullets(binding.listDisable, R.array.instr_disable)
        fillBullets(binding.listBackground, R.array.instr_background)
    }


    private fun fillBullets(
        container: LinearLayout,
        @ArrayRes arrayRes: Int
    ) {
        container.removeAllViews()

        val inflater = LayoutInflater.from(requireContext())

        resources.getStringArray(arrayRes).forEach { text ->

            val item = ItemBulletBinding.inflate(
                inflater,
                container,
                false
            )

            item.tvBullet.apply {
                this.text = text
                textSize = 15f
            }

            container.addView(item.root)
        }
    }


    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
