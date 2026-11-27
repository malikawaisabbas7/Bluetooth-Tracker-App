package com.example.bluetoothtracker.ui.settings

import android.app.Activity
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.bluetoothtracker.R
import com.example.bluetoothtracker.core.Constants
import com.example.bluetoothtracker.core.LocaleHelper
import com.example.bluetoothtracker.core.PermissionHelper
import com.example.bluetoothtracker.core.ThemeTransitionDebug
import com.example.bluetoothtracker.data.settings.AppSettings
import com.example.bluetoothtracker.databinding.DialogInfoBinding
import com.example.bluetoothtracker.databinding.DialogRateBinding
import com.example.bluetoothtracker.databinding.FragmentSettingsBinding
import com.example.bluetoothtracker.domain.model.AppTheme
import com.example.bluetoothtracker.domain.model.DistanceUnit
import com.example.bluetoothtracker.service.TrackingService
import com.example.bluetoothtracker.ui.common.openLink
import com.example.bluetoothtracker.databinding.ItemThemeOptionBinding
import com.google.android.material.color.MaterialColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
@AndroidEntryPoint
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val viewModel: SettingsViewModel by viewModels()

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val unitOptions = listOf(
        DistanceUnit.METER,
        DistanceUnit.FOOT
    )

    private val durationOptionsMs = listOf(
        25_000L,
        45_000L,
        120_000L,
        300_000L
    )

    private var trackingOn = false
    private var unitIndex = 0
    private var durationIndex = 0
    private var selectedTheme = AppTheme.BLUE
    private var settingsLoaded = false
    private var restoredScrollForView = false
    private var activationState = TrackingActivationState.OFF
    private var awaitingLocationSettingsReturn = false
    private var bluetoothStateReceiver: BroadcastReceiver? = null
    private var locationDialog: AlertDialog? = null
    private var trackingInfoDialog: Dialog? = null

    private val enableBluetoothLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val ctx = context ?: return@registerForActivityResult
            if (PermissionHelper.isBluetoothEnabled(ctx)) {
                continueTrackingActivation()
            } else if (result.resultCode == Activity.RESULT_OK) {
                // On some devices ACTION_REQUEST_ENABLE returns RESULT_OK while adapter is still STATE_TURNING_ON.
                waitForBluetoothStateOn(ctx)
            } else {
                cancelTrackingActivation()
            }
        }

    private val enableLocationLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            awaitingLocationSettingsReturn = false
            val ctx = context ?: return@registerForActivityResult
            if (PermissionHelper.isLocationEnabled(ctx)) {
                continueTrackingActivation()
            } else {
                cancelTrackingActivation()
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            // Start tracking after the notification prompt completes so the
            // foreground notification is displayed if permission was granted.
            startBackgroundTrackingService()
        }

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            val ctx = context ?: return@registerForActivityResult
            if (PermissionHelper.hasAll(
                    ctx,
                    PermissionHelper.trackingPermissions()
                )) {
                continueTrackingActivation()
            } else {
                cancelTrackingActivation()
                Toast.makeText(
                    ctx,
                    R.string.tracking_permission_needed,
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentSettingsBinding.bind(view)
        restoredScrollForView = false
        ThemeTransitionDebug.log(
            context,
            "SettingsFragment onViewCreated savedScrollY=${viewModel.savedScrollY}"
        )
        view.doOnPreDraw {
            ThemeTransitionDebug.log(context, "SettingsFragment firstPreDraw")
        }

        setupSpinner(
            binding.spinnerUnit,
            R.array.unit_labels
        ) { position ->
            if (position != unitIndex) {
                viewModel.setUnit(unitOptions[position])
            }
        }

        setupSpinner(
            binding.spinnerDuration,
            R.array.duration_labels
        ) { position ->
            if (position != durationIndex) {
                viewModel.setScanDuration(durationOptionsMs[position])
            }
        }

        setupClicks()
        expandToggleTouchTarget()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                launch {
                    viewModel.settings.collect { settings ->
                        render(settings)
                    }
                }
                launch {
                    TrackingService.isRunning.collect { running ->
                        if (running && activationState == TrackingActivationState.STARTING) {
                            activationState = TrackingActivationState.ON
                            viewModel.setPendingTrackingActivation(false)
                        }
                        if (_binding != null) {
                            renderTrackingToggle(
                                viewModel.settings.value.backgroundTracking,
                                running
                            )
                        }
                    }
                }
                launch {
                    TrackingService.lastStartError.collect { error ->
                        if (error != null && activationState == TrackingActivationState.STARTING) {
                            activationState = TrackingActivationState.ERROR
                            viewModel.setPendingTrackingActivation(false)
                            viewModel.setBackgroundTracking(false)
                            context?.let {
                                Toast.makeText(it, R.string.tracking_permission_needed, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupClicks() = with(binding) {

        ivBack.setOnClickListener {
            findNavController().navigateUp()
        }

        rowAppTheme.setOnClickListener {
            showThemeSelectionDialog()
        }

        rowLanguage.setOnClickListener {
            if (findNavController().currentDestination?.id == R.id.settingsFragment) {
                findNavController().navigate(
                    SettingsFragmentDirections.actionSettingsToLanguage(isFromSettings = true)
                )
            }
        }

        ivTrackingInfo.setOnClickListener {
            showTrackingInfo()
        }

        tvTracking.setOnClickListener {
            onTrackingToggleClicked()
        }

        rowRemoveAds.setOnClickListener {
            findNavController().navigate(
                R.id.action_settings_to_premium
            )
        }

        rowSupport.setOnClickListener {
            findNavController().navigate(
                R.id.action_settings_to_support
            )
        }

        rowRate.setOnClickListener {
            showRateDialog()
        }

        rowShare.setOnClickListener {
            shareApp()
        }

        rowPrivacy.setOnClickListener {
            requireContext().openLink(Constants.PRIVACY_URL)
        }

        rowTerms.setOnClickListener {
            requireContext().openLink(Constants.TERMS_URL)
        }
    }

    private fun showThemeSelectionDialog() {

        val themes = AppTheme.entries.toList()

        val density = resources.displayMetrics.density
        val horizontalPadding = (12 * density).toInt()
        val verticalPadding = (8 * density).toInt()

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL

            setPadding(
                horizontalPadding,
                verticalPadding,
                horizontalPadding,
                verticalPadding
            )
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.choose_app_theme)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .create()

        themes.forEach { theme ->

            val itemBinding = ItemThemeOptionBinding.inflate(
                layoutInflater,
                container,
                false
            )

            itemBinding.radioSelected.buttonTintList =
                android.content.res.ColorStateList.valueOf(
                    theme.primaryColor.toInt()
                )

            val themeLabel = getString(theme.labelRes)
            itemBinding.tvThemeName.text = themeLabel

            itemBinding.root.contentDescription = themeLabel

            itemBinding.root.isFocusable = true

            itemBinding.circlePrimary.background =
                createColorCircle(theme.primaryColor.toInt())

            itemBinding.circleLight.background =
                createColorCircle(theme.lightColor.toInt())

            itemBinding.circleWhite.background =
                createColorCircle(Color.WHITE)

            itemBinding.radioSelected.isChecked =
                theme == selectedTheme

            itemBinding.root.setOnClickListener {

                if (theme != selectedTheme) {
                    ThemeTransitionDebug.log(context, "SettingsFragment themeSelected=$theme")
                    pendingThemeScrollY = _binding?.scrollSettings?.scrollY ?: 0
                    viewModel.setAppTheme(theme)
                }

                ThemeTransitionDebug.log(context, "SettingsFragment themeDialogDismiss")
                dialog.dismiss()
            }

            container.addView(itemBinding.root)
        }

        dialog.show()

        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f * density
                setColor(Color.WHITE)
            }
        )
    }



    private fun createColorCircle(color: Int): GradientDrawable {

        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)

            setStroke(
                (resources.displayMetrics.density * 1).toInt()
                    .coerceAtLeast(1),
                Color.parseColor("#33000000")
            )
        }
    }


    private fun setupSpinner(
        spinner: Spinner,
        labelsRes: Int,
        onPicked: (Int) -> Unit
    ) {
        val labels = resources.getStringArray(labelsRes)

        spinner.adapter = ArrayAdapter(
            requireContext(),
            R.layout.item_spinner,
            labels
        ).also {
            it.setDropDownViewResource(
                R.layout.item_spinner_dropdown
            )
        }

        spinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    v: View?,
                    position: Int,
                    id: Long
                ) {
                    if (settingsLoaded) {
                        onPicked(position)
                    }
                }

                override fun onNothingSelected(
                    parent: AdapterView<*>?
                ) = Unit
            }
    }

    private fun render(
        settings: AppSettings
    ) = with(binding) {

        // Ignore Spinner callbacks until persisted settings have been rendered.
        selectedTheme = settings.appTheme

        tvSelectedTheme.text = getString(selectedTheme.labelRes)
        tvSelectedLanguage.text = LocaleHelper.currentLanguage(settings.languageTag).nativeName

        unitIndex = unitOptions.indexOf(
            settings.unit
        ).coerceAtLeast(0)

        durationIndex = durationOptionsMs.indexOf(
            settings.scanDurationMs
        ).coerceAtLeast(0)

        if (spinnerUnit.selectedItemPosition != unitIndex) {
            spinnerUnit.setSelection(unitIndex, false)
        }

        if (spinnerDuration.selectedItemPosition != durationIndex) {
            spinnerDuration.setSelection(durationIndex, false)
        }

        settingsLoaded = true

        rowRemoveAds.visibility = if (settings.isPremium) View.GONE else View.VISIBLE

        renderTrackingToggle(settings.backgroundTracking, TrackingService.isRunning.value)

        if (!restoredScrollForView) {
            restoredScrollForView = true
            val targetY = if (pendingThemeScrollY > 0) {
                pendingThemeScrollY.also { pendingThemeScrollY = 0 }
            } else {
                viewModel.savedScrollY
            }
            if (targetY > 0) {
                scrollSettings.post { _binding?.scrollSettings?.scrollTo(0, targetY) }
            }
        }
    }

    private fun expandToggleTouchTarget() {
        val toggle = binding.tvTracking
        val parent = toggle.parent as? View ?: return
        parent.post {
            if (_binding == null) return@post
            val minSizePx = (48f * resources.displayMetrics.density).toInt()
            val rect = android.graphics.Rect()
            toggle.getHitRect(rect)
            val extraH = ((minSizePx - rect.height()) / 2).coerceAtLeast(0)
            val extraW = ((minSizePx - rect.width()) / 2).coerceAtLeast(0)
            rect.inset(-extraW, -extraH)
            parent.touchDelegate = android.view.TouchDelegate(rect, toggle)
        }
    }

    private fun renderTrackingToggle(persistedEnabled: Boolean, serviceRunning: Boolean) = with(binding) {
        trackingOn = persistedEnabled && (serviceRunning || activationState == TrackingActivationState.STARTING)

        tvTracking.setBackgroundResource(
            if (trackingOn) {
                R.drawable.bg_toggle_on
            } else {
                R.drawable.bg_toggle_off
            }
        )

        tvTracking.setText(
            if (trackingOn) {
                R.string.toggle_on
            } else {
                R.string.toggle_off
            }
        )

        val toggleTextColor = if (trackingOn) {
            MaterialColors.getColor(
                tvTracking,
                com.google.android.material.R.attr.colorOnPrimary
            )
        } else {
            ContextCompat.getColor(
                requireContext(),
                R.color.theme_text_dark
            )
        }

        tvTracking.setTextColor(toggleTextColor)
    }

    private fun onTrackingToggleClicked() {
        val ctx = context ?: return
        if (trackingOn) {
            cancelTrackingActivation()
            viewModel.setBackgroundTracking(false)
            TrackingService.stop(ctx)
        } else {
            if (activationState == TrackingActivationState.STARTING ||
                activationState == TrackingActivationState.CHECKING_REQUIREMENTS
            ) {
                return
            }
            viewModel.setPendingTrackingActivation(true)
            continueTrackingActivation()
        }
    }

    private fun continueTrackingActivation() {
        val ctx = context ?: return
        activationState = TrackingActivationState.CHECKING_REQUIREMENTS
        when {
            !PermissionHelper.hasAll(ctx, PermissionHelper.trackingPermissions()) -> {
                activationState = TrackingActivationState.WAITING_FOR_PERMISSION
                permissionLauncher.launch(PermissionHelper.trackingPermissions())
            }
            !PermissionHelper.isBluetoothEnabled(ctx) -> {
                activationState = TrackingActivationState.WAITING_FOR_BLUETOOTH
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
            !PermissionHelper.isLocationEnabled(ctx) -> {
                activationState = TrackingActivationState.WAITING_FOR_LOCATION
                showLocationEnableDialog()
            }
            else -> {
                val notifPerms = PermissionHelper.notificationPermissions()
                if (notifPerms.isNotEmpty() && !PermissionHelper.hasAll(ctx, notifPerms)) {
                    activationState = TrackingActivationState.WAITING_FOR_PERMISSION
                    notificationPermissionLauncher.launch(notifPerms)
                } else {
                    startBackgroundTrackingService()
                }
            }
        }
    }

    private fun waitForBluetoothStateOn(ctx: Context) {
        unregisterBluetoothReceiver()
        val appCtx = ctx.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_ON) {
                    unregisterBluetoothReceiver()
                    if (viewModel.pendingTrackingActivation.value) {
                        continueTrackingActivation()
                    }
                } else if (state == BluetoothAdapter.STATE_OFF) {
                    unregisterBluetoothReceiver()
                    cancelTrackingActivation()
                }
            }
        }
        bluetoothStateReceiver = receiver
        ContextCompat.registerReceiver(
            appCtx,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun unregisterBluetoothReceiver() {
        bluetoothStateReceiver?.let { receiver ->
            runCatching { context?.applicationContext?.unregisterReceiver(receiver) }
        }
        bluetoothStateReceiver = null
    }

    private fun cancelTrackingActivation() {
        unregisterBluetoothReceiver()
        activationState = TrackingActivationState.OFF
        viewModel.setPendingTrackingActivation(false)
    }

    private fun showLocationEnableDialog() {
        val ctx = context ?: return
        if (locationDialog?.isShowing == true) return
        locationDialog = AlertDialog.Builder(ctx)
            .setTitle(R.string.perm_loc_title)
            .setMessage(R.string.perm_loc_desc)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                awaitingLocationSettingsReturn = true
                enableLocationLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton(R.string.not_now) { _, _ ->
                cancelTrackingActivation()
            }
            .setOnCancelListener {
                cancelTrackingActivation()
            }
            .show()
    }

    private fun startBackgroundTrackingService() {
        val ctx = context ?: return
        activationState = TrackingActivationState.STARTING
        viewModel.setPendingTrackingActivation(false)
        viewModel.setBackgroundTracking(true)
        try {
            TrackingService.start(ctx)
            if (TrackingService.isRunning.value) {
                activationState = TrackingActivationState.ON
            }
        } catch (e: Exception) {
            Log.e("SettingsFragment", "Failed to start TrackingService", e)
            activationState = TrackingActivationState.ERROR
            viewModel.setBackgroundTracking(false)
            Toast.makeText(ctx, R.string.tracking_permission_needed, Toast.LENGTH_LONG).show()
        }
    }

    private fun showTrackingInfo() {
        val ctx = context ?: return
        if (trackingInfoDialog?.isShowing == true) return

        val dialogBinding = DialogInfoBinding.inflate(layoutInflater)
        dialogBinding.tvBody.text =
            resources.getStringArray(R.array.instr_background).joinToString(separator = "\n\n")

        val density = resources.displayMetrics.density
        val horizontalMargin = (20 * density).toInt()
        val verticalMargin = (24 * density).toInt()

        dialogBinding.root.elevation = 8f * density

        val centerWrapper = FrameLayout(ctx).apply {
            setPadding(horizontalMargin, verticalMargin, horizontalMargin, verticalMargin)
            setBackgroundColor(Color.TRANSPARENT)
            addView(
                dialogBinding.root,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
        }

        val scrollWrapper = ScrollView(ctx).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            setBackgroundColor(Color.TRANSPARENT)
            addView(
                centerWrapper,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val dialog = Dialog(ctx)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(scrollWrapper)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        dialogBinding.ivClose.setOnClickListener {
            dialog.dismiss()
        }

        trackingInfoDialog = dialog
        dialog.show()

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.35f)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun showRateDialog() {

        val d = DialogRateBinding.inflate(layoutInflater)

        val stars = listOf(
            d.star1,
            d.star2,
            d.star3,
            d.star4,
            d.star5
        )

        var rating = 0

        val selectedStarColor = android.graphics.Color.parseColor("#FFB300")
        val unselectedStarColor = android.graphics.Color.parseColor("#BDBDBD")


        fun paint() {

            stars.forEachIndexed { index, star ->
                star.setColorFilter(
                    if (index < rating) selectedStarColor
                    else unselectedStarColor
                )
            }

            d.btnRate.setBackgroundResource(
                if (rating > 0) {
                    R.drawable.bg_button_lime
                } else {
                    R.drawable.bg_button_disabled
                }
            )

            // Text color based on selected app theme
            val textColor = if (rating > 0) {
                com.google.android.material.color.MaterialColors.getColor(
                    d.btnRate,
                    com.google.android.material.R.attr.colorOnPrimary
                )
            } else {
                ContextCompat.getColor(
                    requireContext(),
                    R.color.theme_text_secondary
                )
            }

            d.btnRate.setTextColor(textColor)

            d.btnRate.setText(
                when (rating) {
                    in 1..4 -> R.string.send_feedback
                    else -> R.string.rate_button
                }
            )

            d.btnRate.isEnabled = rating > 0
        }


        stars.forEachIndexed { index, star ->
            star.setOnClickListener {
                rating = index + 1
                paint()
            }
        }

        paint()

        val dialog = Dialog(requireContext())
        dialog.setContentView(d.root)

        d.ivClose.setOnClickListener {
            dialog.dismiss()
        }

        d.btnRate.setOnClickListener {

            if (rating == 0) {
                return@setOnClickListener
            }

            dialog.dismiss()

            when (rating) {

                in 1..4 -> {
                    findNavController().navigate(
                        R.id.action_settings_to_support
                    )
                }

                5 -> {
                    openStorePage()
                }
            }
        }

        dialog.show()

        dialog.window?.apply {

            setBackgroundDrawableResource(
                android.R.color.transparent
            )

            addFlags(
                WindowManager.LayoutParams.FLAG_DIM_BEHIND
            )

            setDimAmount(0.65f)
            setGravity(Gravity.CENTER)

            setLayout(
                (
                        resources.displayMetrics.widthPixels *
                                0.95f
                        ).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun openStorePage() {

        val pkg = requireContext().packageName

        try {

            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=$pkg")
                )
            )

        } catch (e: Exception) {

            requireContext().openLink(
                "https://play.google.com/store/apps/details?id=$pkg"
            )
        }
    }

    private fun shareApp() {

        val text = getString(
            R.string.share_text,
            requireContext().packageName
        )

        val send = Intent(Intent.ACTION_SEND).apply {

            type = "text/plain"

            putExtra(
                Intent.EXTRA_TEXT,
                text
            )
        }

        startActivity(
            Intent.createChooser(
                send,
                getString(R.string.share_chooser)
            )
        )
    }

    override fun onPause() {
        _binding?.scrollSettings?.let { viewModel.savedScrollY = it.scrollY }
        super.onPause()
    }

    override fun onDestroyView() {
        ThemeTransitionDebug.log(context, "SettingsFragment onDestroyView")
        _binding?.scrollSettings?.let { viewModel.savedScrollY = it.scrollY }
        unregisterBluetoothReceiver()
        locationDialog?.dismiss()
        locationDialog = null
        trackingInfoDialog?.dismiss()
        trackingInfoDialog = null
        settingsLoaded = false
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private var pendingThemeScrollY: Int = 0
    }
}
