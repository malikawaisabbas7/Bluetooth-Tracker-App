package com.example.bluetoothtracker.ui.language

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.example.bluetoothtracker.R
import com.example.bluetoothtracker.core.AppLanguage
import com.example.bluetoothtracker.databinding.ItemLanguageBinding
import com.google.android.material.color.MaterialColors

class LanguageAdapter(
    private val items: List<AppLanguage>,
    private var selected: AppLanguage,
    private val onSelect: (AppLanguage) -> Unit
) : RecyclerView.Adapter<LanguageAdapter.ViewHolder>() {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = items[position].tag.hashCode().toLong()

    fun updateSelected(language: AppLanguage) {
        if (selected == language) return
        val oldIndex = items.indexOf(selected)
        val newIndex = items.indexOf(language)
        selected = language
        if (oldIndex >= 0) notifyItemChanged(oldIndex, PAYLOAD_SELECTION)
        if (newIndex >= 0) notifyItemChanged(newIndex, PAYLOAD_SELECTION)
    }

    inner class ViewHolder(
        private val binding: ItemLanguageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppLanguage) {
            binding.tvFlag.text = item.flagEmoji
            binding.tvLanguageName.text = item.nativeName
            binding.tvEnglishName.text = item.englishName
            binding.cardLanguage.contentDescription = "${item.nativeName}, ${item.englishName}"
            binding.cardLanguage.setOnClickListener { onSelect(item) }
            bindSelection(item)
        }

        fun bindSelection(item: AppLanguage) {
            val selectedItem = item == selected
            val ctx = binding.root.context
            val density = ctx.resources.displayMetrics.density

            val primaryColor = MaterialColors.getColor(
                binding.root,
                R.attr.themePrimaryColor,
                ContextCompat.getColor(ctx, R.color.theme_blue_primary)
            )
            val lightColor = MaterialColors.getColor(
                binding.root,
                R.attr.themeLightColor,
                ContextCompat.getColor(ctx, R.color.theme_blue_light)
            )
            val textColor = MaterialColors.getColor(
                binding.root,
                R.attr.themeTextColor,
                ContextCompat.getColor(ctx, R.color.theme_text_dark)
            )
            val surfaceWhite = ContextCompat.getColor(ctx, R.color.white)
            val neutralBorder = ColorUtils.setAlphaComponent(textColor, 36)

            binding.cardLanguage.apply {
                strokeWidth = ((if (selectedItem) 2f else 1f) * density).toInt().coerceAtLeast(1)
                strokeColor = if (selectedItem) primaryColor else neutralBorder
                setCardBackgroundColor(if (selectedItem) lightColor else surfaceWhite)
                cardElevation = (if (selectedItem) 4f else 1f) * density
                this.isSelected = selectedItem
            }

            binding.checkContainer.visibility = if (selectedItem) View.VISIBLE else View.GONE
            binding.tvLanguageName.setTextColor(if (selectedItem) primaryColor else textColor)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLanguageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            holder.bindSelection(items[position])
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount(): Int = items.size

    companion object {
        private const val PAYLOAD_SELECTION = "selection"
    }
}
