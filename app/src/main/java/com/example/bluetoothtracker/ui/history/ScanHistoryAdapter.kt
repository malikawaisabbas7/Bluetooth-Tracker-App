package com.example.bluetoothtracker.ui.history

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.bluetoothtracker.R
import com.example.bluetoothtracker.data.local.SightingEntity
import com.example.bluetoothtracker.databinding.ItemScanHistoryBinding
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.WeakHashMap

class ScanHistoryAdapter :
    ListAdapter<SightingEntity, ScanHistoryAdapter.HistoryHolder>(DIFF) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id

    private val activeHolders =
        Collections.newSetFromMap(WeakHashMap<HistoryHolder, Boolean>())
    private val attachedHolders =
        Collections.newSetFromMap(WeakHashMap<HistoryHolder, Boolean>())

    private val tileSource = XYTileSource(
        "OSM",
        0,
        19,
        256,
        ".png",
        arrayOf("https://tile.openstreetmap.org/")
    )

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): HistoryHolder {
        val binding = ItemScanHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HistoryHolder(binding).also { activeHolders.add(it) }
    }

    override fun onBindViewHolder(
        holder: HistoryHolder,
        position: Int
    ) {
        holder.bind(getItem(position))
    }

    override fun onViewAttachedToWindow(holder: HistoryHolder) {
        super.onViewAttachedToWindow(holder)
        attachedHolders.add(holder)
        holder.resumeMap()
    }

    override fun onViewDetachedFromWindow(holder: HistoryHolder) {
        attachedHolders.remove(holder)
        holder.pauseMap()
        super.onViewDetachedFromWindow(holder)
    }

    override fun onViewRecycled(holder: HistoryHolder) {
        attachedHolders.remove(holder)
        holder.clearMap()
        super.onViewRecycled(holder)
    }

    override fun onFailedToRecycleView(holder: HistoryHolder): Boolean {
        attachedHolders.remove(holder)
        holder.clearMap()
        return super.onFailedToRecycleView(holder)
    }

    fun resumeAttachedMaps() {
        attachedHolders.toList().forEach { it.resumeMap() }
    }

    fun pauseAllMaps() {
        activeHolders.toList().forEach { it.pauseMap() }
    }

    /** Detaches all tracked MapViews when the host Fragment's view is destroyed. */
    fun releaseAllMaps() {
        attachedHolders.clear()
        activeHolders.toList().forEach { it.detachMap() }
        activeHolders.clear()
    }

    inner class HistoryHolder(
        private val binding: ItemScanHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var mapResumed = false
        private var hasMapLocation = false
        private var boundMapSignature: Pair<Double, Double>? = null
        private var cachedPinDrawable: Drawable? = null

        init {
            binding.mapView.apply {
                setTileSource(tileSource)
                @Suppress("DEPRECATION")
                setBuiltInZoomControls(false)
                setMultiTouchControls(true)
                setUseDataConnection(true)
                setTilesScaledToDpi(true)

                minZoomLevel = 3.0
                maxZoomLevel = 19.0

                controller.setZoom(15.0)
            }
        }

        fun bind(item: SightingEntity) {
            val date = Date(item.timestamp)
            val dateText = DATE_FORMAT.get()!!.format(date)
            val timeText = TIME_FORMAT.get()!!.format(date)

            hasMapLocation = item.hasLocation && isValidLocation(item.latitude, item.longitude)
            binding.mapSection.visibility = View.VISIBLE

            binding.tvDetectedDate.text = dateText
            binding.tvDetectedTime.text = timeText
            binding.ivOpenMap.isEnabled = hasMapLocation
            binding.ivOpenMap.alpha = if (hasMapLocation) 1f else 0.4f

            if (!hasMapLocation) {
                boundMapSignature = null
                binding.mapView.overlays.clear()
                binding.ivOpenMap.setOnClickListener(null)
                pauseMap()
                return
            }

            val newSignature = item.latitude to item.longitude
            if (boundMapSignature != newSignature || binding.mapView.overlays.isEmpty()) {
                boundMapSignature = newSignature
                binding.mapView.overlays.clear()
                binding.mapView.overlays.add(CopyrightOverlay(binding.root.context))

                val point = GeoPoint(item.latitude, item.longitude)
                binding.mapView.controller.setZoom(15.0)
                binding.mapView.controller.setCenter(point)

                val pinIcon = cachedPinDrawable ?: ContextCompat.getDrawable(
                    binding.root.context,
                    R.drawable.ic_scan_location_pin
                )?.also { cachedPinDrawable = it }

                val marker = Marker(binding.mapView).apply {
                    position = point
                    setAnchor(
                        Marker.ANCHOR_CENTER,
                        Marker.ANCHOR_BOTTOM
                    )
                    icon = pinIcon
                    title = binding.root.context.getString(R.string.recorded_scan_location)
                    setInfoWindow(null)
                }

                binding.mapView.overlays.add(marker)
                binding.mapView.invalidate()
            }

            binding.ivOpenMap.setOnClickListener {
                val context = binding.root.context
                val geoUri = Uri.parse(
                    "geo:${item.latitude},${item.longitude}?q=${item.latitude},${item.longitude}"
                )
                val geoIntent = Intent(Intent.ACTION_VIEW, geoUri)

                try {
                    context.startActivity(geoIntent)
                } catch (_: ActivityNotFoundException) {
                    val webUri = Uri.parse(
                        "https://www.openstreetmap.org/" +
                            "?mlat=${item.latitude}" +
                            "&mlon=${item.longitude}" +
                            "#map=16/${item.latitude}/${item.longitude}"
                    )
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(
                            context,
                            R.string.open_map_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        private fun isValidLocation(latitude: Double, longitude: Double): Boolean =
            latitude.isFinite() &&
                longitude.isFinite() &&
                latitude in -90.0..90.0 &&
                longitude in -180.0..180.0 &&
                !(latitude == 0.0 && longitude == 0.0)

        fun resumeMap() {
            if (hasMapLocation && !mapResumed) {
                binding.mapView.onResume()
                mapResumed = true
            }
        }

        fun pauseMap() {
            if (mapResumed) {
                binding.mapView.onPause()
                mapResumed = false
            }
        }

        fun clearMap() {
            pauseMap()
            boundMapSignature = null
            binding.mapView.overlays.clear()
        }

        fun detachMap() {
            clearMap()
            binding.mapView.onDetach()
        }
    }

    companion object {
        private val DATE_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat =
                SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        }

        private val TIME_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat =
                SimpleDateFormat("hh:mm a", Locale.getDefault())
        }

        private val DIFF = object : DiffUtil.ItemCallback<SightingEntity>() {
            override fun areItemsTheSame(
                oldItem: SightingEntity,
                newItem: SightingEntity
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: SightingEntity,
                newItem: SightingEntity
            ): Boolean = oldItem == newItem
        }
    }
}
