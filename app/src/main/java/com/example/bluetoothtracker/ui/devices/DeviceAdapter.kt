package com.example.bluetoothtracker.ui.devices

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.bluetoothtracker.R
import com.example.bluetoothtracker.data.local.DeviceEntity
import com.example.bluetoothtracker.databinding.ItemDeviceBinding
import com.example.bluetoothtracker.domain.logic.SignalLevel
import com.example.bluetoothtracker.domain.model.DeviceCategory

class DeviceAdapter(
    private val onClick: (DeviceEntity) -> Unit
) : ListAdapter<DeviceEntity, DeviceAdapter.ViewHolder>(Diff) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).mac.hashCode().toLong()
    }

    inner class ViewHolder(private val b: ItemDeviceBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: DeviceEntity) {
            val ctx = b.root.context

            b.tvName.text = when {
                !item.name.isNullOrBlank() -> item.name
                !item.companyName.isNullOrBlank() -> ctx.getString(R.string.company_device_format, item.companyName)
                else -> ctx.getString(R.string.unknown_device) + " (${item.mac.takeLast(5)})"
            }

            b.tvMac.text = item.mac

            val iconRes = when (item.category) {
                DeviceCategory.AIR_TAG.name -> R.drawable.ic_airtag
                DeviceCategory.OTHER_TAG.name -> R.drawable.ic_tag
                else -> R.drawable.ic_bluetooth
            }
            b.ivIcon.setImageResource(iconRes)

            updateSignalBars(item.rssi)

            b.root.setOnClickListener { onClick(item) }
        }

        fun updateSignalBars(rssi: Int) {
            val ctx = b.root.context
            val level = SignalLevel.fromRssi(rssi)
            val primaryColor = com.google.android.material.color.MaterialColors.getColor(
                ctx,
                R.attr.themePrimaryColor,
                ContextCompat.getColor(ctx, R.color.theme_blue_primary)
            )
            val on = ColorStateList.valueOf(primaryColor)
            val off = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.grey_light))

            listOf<View>(b.bar1, b.bar2, b.bar3, b.bar4).forEachIndexed { index, bar ->
                bar.backgroundTintList = if (index < level) on else off
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            val item = getItem(position)
            for (payload in payloads) {
                if (payload == PAYLOAD_SIGNAL) {
                    holder.updateSignalBars(item.rssi)
                }
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    private object Diff : DiffUtil.ItemCallback<DeviceEntity>() {
        override fun areItemsTheSame(a: DeviceEntity, b: DeviceEntity) = a.mac == b.mac

        override fun areContentsTheSame(a: DeviceEntity, b: DeviceEntity): Boolean {
            return a.name == b.name &&
                    a.companyName == b.companyName &&
                    a.category == b.category &&
                    SignalLevel.fromRssi(a.rssi) == SignalLevel.fromRssi(b.rssi)
        }

        override fun getChangePayload(oldItem: DeviceEntity, newItem: DeviceEntity): Any? {
            val metadataSame = oldItem.name == newItem.name &&
                    oldItem.companyName == newItem.companyName &&
                    oldItem.category == newItem.category
            return if (metadataSame && SignalLevel.fromRssi(oldItem.rssi) != SignalLevel.fromRssi(newItem.rssi)) {
                PAYLOAD_SIGNAL
            } else {
                null
            }
        }
    }

    companion object {
        private const val PAYLOAD_SIGNAL = "PAYLOAD_SIGNAL"
    }
}
