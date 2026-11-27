package com.example.bluetoothtracker.ui.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.bluetoothtracker.R
import com.example.bluetoothtracker.core.PermissionHelper
import com.example.bluetoothtracker.data.local.SightingEntity
import com.example.bluetoothtracker.databinding.FragmentDeviceDetailBinding
import com.example.bluetoothtracker.domain.logic.DistanceFormatter
import com.example.bluetoothtracker.domain.logic.TrackingWarningLevel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.osmdroid.tileprovider.tilesource.XYTileSource
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.bundleOf
@AndroidEntryPoint
class DeviceDetailFragment : Fragment(R.layout.fragment_device_detail) {

    private val viewModel: DeviceDetailViewModel by viewModels()
    private var _binding: FragmentDeviceDetailBinding? = null
    private val binding get() = _binding!!

    private val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
    private val mapDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    private val mapTimeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    private var previousMapSignature: Pair<Double, Double>? = null
    private var restoredScrollForView = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceDetailBinding.bind(view)
        previousMapSignature = null
        restoredScrollForView = false

        binding.ivBack.setOnClickListener { findNavController().navigateUp() }
        val toggleIgnore = {
            val ignored = viewModel.state.value?.isIgnored ?: false
            if (viewModel.state.value != null) {
                viewModel.setIgnored(!ignored)
            }
        }
        binding.btnToggle.setOnClickListener { toggleIgnore() }
        binding.rowIgnoreDevice.setOnClickListener { toggleIgnore() }
        binding.tvDistance.setOnClickListener { tryAcquireDetailScan(showFeedback = true) }


        binding.mapView.apply {

            setTileSource(
                XYTileSource(
                    "OSM",
                    0,
                    19,
                    256,
                    ".png",
                    arrayOf("https://tile.openstreetmap.org/")
                )
            )

            @Suppress("DEPRECATION")
            setBuiltInZoomControls(false)
            setMultiTouchControls(true)
            setUseDataConnection(true)
            setTilesScaledToDpi(true)

            controller.setZoom(15.0)
        }


        binding.ivOpenMap.setOnClickListener {
            openExternalMap()
        }

        binding.tvSeeMore.paintFlags = binding.tvSeeMore.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.tvSeeMore.setOnClickListener {
            if (findNavController().currentDestination?.id == R.id.deviceDetailFragment) {
                findNavController().navigate(
                    R.id.action_deviceDetail_to_scanHistory,
                    bundleOf("mac" to viewModel.mac)
                )
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { state -> state?.let(::render) } }
                launch {
                    viewModel.scanErrors.collect { reason ->
                        context?.let {
                            Toast.makeText(
                                it,
                                getString(R.string.scan_failed, reason),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        tryAcquireDetailScan(showFeedback = false)
    }

    private fun tryAcquireDetailScan(showFeedback: Boolean) {
        val ctx = context ?: return
        when {
            !PermissionHelper.hasBleScanPermissions(ctx) -> {
                if (showFeedback) {
                    Toast.makeText(ctx, R.string.permission_needed, Toast.LENGTH_SHORT).show()
                }
            }
            !PermissionHelper.isBluetoothEnabled(ctx) -> {
                if (showFeedback) {
                    Toast.makeText(
                        ctx,
                        getString(R.string.scan_failed, "Bluetooth is off"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            else -> viewModel.acquireScan()
        }
    }

    override fun onStop() {
        viewModel.releaseScan()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        _binding?.mapView?.onResume()
    }

    override fun onPause() {
        _binding?.nestedScrollView?.let { viewModel.savedScrollY = it.scrollY }
        _binding?.mapView?.onPause()
        super.onPause()
    }

    private fun render(state: DeviceDetailUiState) = with(binding) {
        // Color the inner circle and outer border by distance: Far = Green, Near = Orange, Very Near = Red.

        val (mainColorRes, lightColorRes) = when {
            !state.hasFreshSignal -> R.color.grey to R.color.grey_light
            state.distanceMeters < 3.5 ->
                R.color.red to R.color.red_light

            state.distanceMeters <= 8.65 ->
                R.color.theme_orange_primary to R.color.theme_orange_light

            else ->
                R.color.theme_green_primary to R.color.theme_green_light
        }

        val mainColor = ContextCompat.getColor(requireContext(), mainColorRes)
        val lightColor = ContextCompat.getColor(requireContext(), lightColorRes)
        (tvDistance.background?.mutate() as? LayerDrawable)?.let { layers ->
            (layers.findDrawableByLayerId(R.id.outer_ring)?.mutate() as? GradientDrawable)?.setColor(lightColor)
            (layers.findDrawableByLayerId(R.id.distance_ring)?.mutate() as? GradientDrawable)?.setColor(mainColor)
            (layers.findDrawableByLayerId(R.id.inner_circle)?.mutate() as? GradientDrawable)?.setColor(mainColor)
            tvDistance.background = layers
            tvDistance.invalidate()
        }

        tvDistance.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))

        tvDistance.text = if (state.hasFreshSignal) {
            DistanceFormatter.format(state.distanceMeters, state.unit).replaceFirst(Regex("\\s+"), "\n")
        } else {
            "—"
        }

        val summary = state.encounterSummary
        when (summary.warningLevel) {
            TrackingWarningLevel.POTENTIAL_UNWANTED_TRACKING -> {
                layoutEncounterWarning.visibility = View.VISIBLE
                tvWarningTitle.setText(R.string.warning_suspicious)
                tvWarningTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
                tvWarningSummary.text = getString(
                    R.string.encounter_summary_format,
                    summary.encounterCount,
                    summary.distinctLocationCount
                )
            }
            TrackingWarningLevel.REPEATED_ENCOUNTER -> {
                layoutEncounterWarning.visibility = View.VISIBLE
                tvWarningTitle.setText(R.string.warning_repeated)
                tvWarningTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.theme_orange_primary))
                tvWarningSummary.text = getString(
                    R.string.encounter_summary_format,
                    summary.encounterCount,
                    summary.distinctLocationCount
                )
            }
            else -> {
                layoutEncounterWarning.visibility = View.GONE
            }
        }

        tvName.text = when {
            !state.name.isNullOrBlank() -> state.name
            !state.company.isNullOrBlank() -> getString(R.string.company_device_format, state.company)
            else -> "${getString(R.string.unknown_device)} (${state.mac.takeLast(5)})"
        }
        tvMac.text = state.mac
        tvFirstSeen.text = dateFormat.format(Date(state.firstSeenAt))
        tvRssi.text = if (state.hasFreshSignal) getString(R.string.rssi_value, state.rssi) else "—"
        tvBonding.setText(if (state.isBonded) R.string.bonded else R.string.unbonded)
        tvCompany.text = state.company ?: getString(R.string.unknown_company)

        btnToggle.apply {
            text = if (state.isIgnored) getString(R.string.toggle_on) else getString(R.string.toggle_off)
            setBackgroundResource(if (state.isIgnored) R.drawable.bg_toggle_on else R.drawable.bg_toggle_off)
            setTextColor(
                if (state.isIgnored) {
                    com.google.android.material.color.MaterialColors.getColor(
                        this,
                        com.google.android.material.R.attr.colorOnPrimary
                    )
                } else {
                    ContextCompat.getColor(requireContext(), R.color.theme_text_dark)
                }
            )
            isSelected = state.isIgnored
            contentDescription = getString(
                R.string.ignore_device_state,
                getString(if (state.isIgnored) R.string.toggle_on else R.string.toggle_off)
            )
        }

        mapContainer.visibility = View.VISIBLE
        tvSeeMore.visibility = if (state.sightings.isEmpty()) View.GONE else View.VISIBLE

        val latest = state.sightings
            .filter { it.hasLocation && isValidLocation(it.latitude, it.longitude) }
            .maxWithOrNull(compareBy<SightingEntity> { it.timestamp }.thenBy { it.id })

        val hasPhoneLocation = state.phoneLocation?.let {
            isValidLocation(it.first, it.second)
        } == true
        tvNoLocation.visibility =
            if (latest == null && !hasPhoneLocation) View.VISIBLE else View.GONE

        layoutDetectedInfo.visibility =
            if (latest != null) View.VISIBLE else View.GONE

        if (latest != null) {
            val detectedAt = Date(latest.timestamp)

            tvDetectedDate.text = mapDateFormat.format(detectedAt)

            tvDetectedTime.text =
                mapTimeFormat.format(detectedAt)
        }

        updateMap(state.sightings, state.phoneLocation)

        if (!restoredScrollForView) {
            restoredScrollForView = true
            val targetY = viewModel.savedScrollY
            if (targetY > 0) {
                nestedScrollView.post { _binding?.nestedScrollView?.scrollTo(0, targetY) }
            }
        }
    }

    private fun isValidLocation(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
                !(latitude == 0.0 && longitude == 0.0)

    private fun updateMap(
        sightings: List<SightingEntity>,
        phoneLocation: Pair<Double, Double>?
    ) {
        val latest = sightings
            .filter { it.hasLocation && isValidLocation(it.latitude, it.longitude) }
            .maxWithOrNull(compareBy<SightingEntity> { it.timestamp }.thenBy { it.id })

        // Saved scan location takes priority; live phone location is only a fallback.
        val location = when {
            latest != null -> latest.latitude to latest.longitude
            phoneLocation != null && isValidLocation(phoneLocation.first, phoneLocation.second) ->
                phoneLocation
            else -> null
        }

        // Also compare the fallback location, so a location-only update refreshes the map.
        if (location == previousMapSignature) return
        previousMapSignature = location

        val map = binding.mapView
        map.overlays.clear()
        map.overlays.add(CopyrightOverlay(requireContext()))

        if (location == null) {
            map.invalidate()
            return
        }

        val point = GeoPoint(location.first, location.second)
        map.overlays.add(

            Marker(map).apply {

                position = point

                icon = ResourcesCompat.getDrawable(
                    resources,
                    R.drawable.ic_scan_location_pin,
                    requireContext().theme
                )

                setAnchor(
                    Marker.ANCHOR_CENTER,
                    Marker.ANCHOR_BOTTOM
                )

                title = if (latest != null) {
                    getString(R.string.recorded_scan_location)
                } else {
                    getString(R.string.phone_location)
                }

                setInfoWindow(null)
            }

        )

        map.controller.setZoom(15.0)
        map.controller.setCenter(point)
        map.invalidate()
    }

    private fun openExternalMap() {
        val state = viewModel.state.value ?: return
        val latest = state.sightings
            .filter { it.hasLocation && isValidLocation(it.latitude, it.longitude) }
            .maxWithOrNull(compareBy<SightingEntity> { it.timestamp }.thenBy { it.id })
        val phoneLoc = state.phoneLocation
        val location = when {
            latest != null -> latest.latitude to latest.longitude
            phoneLoc != null && isValidLocation(phoneLoc.first, phoneLoc.second) -> phoneLoc
            else -> null
        } ?: return

        openCoordinatesInMap(location.first, location.second)
    }

    private fun openCoordinatesInMap(lat: Double, lng: Double) {
        if (!isValidLocation(lat, lng)) return

        val mapsUri = Uri.parse(
            "https://www.openstreetmap.org/?mlat=$lat&mlon=$lng#map=17/$lat/$lng"
        )
        val intent = Intent(Intent.ACTION_VIEW, mapsUri)

        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                requireContext(),
                R.string.open_map_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        _binding?.nestedScrollView?.let { viewModel.savedScrollY = it.scrollY }
        binding.mapView.onDetach()
        previousMapSignature = null
        _binding = null
        super.onDestroyView()
    }
}
