
package com.example.bluetoothtracker.ui.onboarding

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.bluetoothtracker.R
import com.example.bluetoothtracker.databinding.ItemOnboardingPageBinding

class OnboardingPagerAdapter :
    RecyclerView.Adapter<OnboardingPagerAdapter.PageViewHolder>() {

    override fun getItemCount(): Int =
        OnboardingViewModel.PAGE_COUNT

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): PageViewHolder {
        val binding = ItemOnboardingPageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PageViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: PageViewHolder,
        position: Int
    ) {
        holder.bind(position)
    }

    class PageViewHolder(
        private val binding: ItemOnboardingPageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(position: Int) {
            binding.premiumVisual.setPage(position)

            val titleRes = when (position) {
                0 -> R.string.onboarding_title_1
                1 -> R.string.onboarding_title_2
                else -> R.string.onboarding_title_3
            }

            val descriptionRes = when (position) {
                0 -> R.string.onboarding_desc_1
                1 -> R.string.onboarding_desc_2
                else -> R.string.onboarding_desc_3
            }

            binding.tvTitle.setText(titleRes)
            binding.tvDescription.setText(descriptionRes)
        }
    }
}
