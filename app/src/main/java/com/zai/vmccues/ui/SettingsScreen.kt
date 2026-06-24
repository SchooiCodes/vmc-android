package com.zai.vmccues.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zai.vmccues.R
import com.zai.vmccues.VmcApplication
import com.zai.vmccues.data.ActivationMode
import com.zai.vmccues.data.CueSettings
import com.zai.vmccues.data.DotPattern
import com.zai.vmccues.overlay.OverlayService
import com.zai.vmccues.ui.components.ColorPickerDialog
import com.zai.vmccues.ui.components.GroupedSection
import com.zai.vmccues.ui.components.IosSegmentedControl
import com.zai.vmccues.ui.components.IosSlider
import com.zai.vmccues.ui.components.IosSwitch
import com.zai.vmccues.ui.components.LivePreview
import com.zai.vmccues.ui.components.SettingsRow
import com.zai.vmccues.ui.theme.IosTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as VmcApplication
    val repo = app.settings
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = CueSettings())
    val scroll = rememberScrollState()
    val colors = IosTheme.colors
    val typo = IosTheme.typography

    var showDisclaimer by remember { mutableStateOf(!settings.safetyAcknowledged) }
    var showColorPicker by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.groupedBackground)
            .verticalScroll(scroll),
    ) {
        // --- Large title + subtitle ---
        Text(
            text = stringResource(R.string.settings_title),
            style = typo.largeTitle,
            color = colors.label,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.settings_subtitle),
            style = typo.subheadline,
            color = colors.secondaryLabel,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        )

        // --- Live Preview ---
        Box(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            LivePreview(settings = settings)
        }

        // --- Section: Vehicle Motion Cues (mode picker) ---
        // The mode selection IS the toggle — no separate Quick Toggle card,
        // matching iOS Settings > Accessibility > Motion > Vehicle Motion Cues.
        GroupedSection(
            footer = when (settings.mode) {
                ActivationMode.OFF -> "Vehicle Motion Cues is off."
                ActivationMode.ON -> "Dots are always shown while the service is running."
                ActivationMode.AUTOMATIC -> "Dots appear only when the device detects you're in a moving vehicle."
            },
        ) {
            SettingsRow(
                title = "Off",
                trailing = { if (settings.mode == ActivationMode.OFF) CheckMark() },
                showSeparator = true,
                onClick = {
                    scope.launch {
                        repo.setMode(ActivationMode.OFF)
                        OverlayService.stop(context)
                    }
                },
            )
            SettingsRow(
                title = "On",
                trailing = { if (settings.mode == ActivationMode.ON) CheckMark() },
                showSeparator = true,
                onClick = {
                    scope.launch {
                        repo.setMode(ActivationMode.ON)
                        OverlayService.start(context)
                    }
                },
            )
            SettingsRow(
                title = "Automatic",
                trailing = { if (settings.mode == ActivationMode.AUTOMATIC) CheckMark() },
                showSeparator = false,
                onClick = {
                    scope.launch {
                        repo.setMode(ActivationMode.AUTOMATIC)
                        OverlayService.start(context)
                    }
                },
            )
        }

        // --- Section: Customize Appearance (iOS-exact: Pattern, Color, Larger Dots, More Dots) ---
        GroupedSection(header = "Customize Appearance") {
            SettingsRow(
                title = "Pattern",
                trailing = {
                    IosSegmentedControl(
                        segments = listOf("Regular", "Dynamic"),
                        selectedIndex = if (settings.pattern == DotPattern.REGULAR) 0 else 1,
                        onSelected = { i ->
                            scope.launch { repo.setPattern(if (i == 0) DotPattern.REGULAR else DotPattern.DYNAMIC) }
                        },
                    )
                },
                showSeparator = true,
            )
            SettingsRow(
                title = "Color",
                trailing = {
                    ColorSwatch(color = settings.dotColor, selected = false) { showColorPicker = true }
                },
                showSeparator = true,
                onClick = { showColorPicker = true },
            )
            SettingsRow(
                title = "Larger Dots",
                trailing = { IosSwitch(checked = settings.largerDots, onCheckedChange = { v -> scope.launch { repo.setLargerDots(v) } }) },
                showSeparator = true,
            )
            SettingsRow(
                title = "More Dots",
                trailing = { IosSwitch(checked = settings.moreDots, onCheckedChange = { v -> scope.launch { repo.setMoreDots(v) } }) },
                showSeparator = false,
            )
        }

        // --- Section: Advanced (dead-reckoning tuning + visual + gate timing) ---
        // Spec Part B.3/B.4/B.5: filterAlpha / dampingCoef / returnToCenterCoef / inputClamp
        // replace the old smoothing / maxDisplacement / centerExclusion knobs.
        GroupedSection(
            header = "Advanced",
            footer = "Fine-tune the dot field's dead-reckoning integrator, visual response, and context-gate timing. Not part of the iOS surface — kept here for power users.",
        ) {
            // Auto-Contrast toggle (moved here from Customize Appearance).
            SettingsRow(
                title = "Auto-Contrast",
                subtitle = "Halo keeps dots visible against any background",
                trailing = { IosSwitch(checked = settings.autoContrast, onCheckedChange = { v -> scope.launch { repo.setAutoContrast(v) } }) },
                showSeparator = true,
            )
            // --- Motion mapping: overall sensitivity multiplier ---
            SliderRow(
                title = "Sensitivity",
                value = settings.sensitivity,
                valueRange = 0.5f..2.0f,
                format = { "${"%.1f".format(it)}\u00D7" },
                onChange = { v -> scope.launch { repo.setSensitivity(v) } },
            )
            // --- Dead-reckoning integrator (spec Part B.3) ---
            SliderRow(
                title = "Filter Alpha",
                value = settings.filterAlpha,
                valueRange = 0.10f..0.30f,
                format = { "%.2f".format(it) },
                onChange = { v -> scope.launch { repo.setFilterAlpha(v) } },
            )
            SliderRow(
                title = "Damping Coefficient",
                value = settings.dampingCoef,
                valueRange = 2.0f..10.0f,
                format = { "${"%.1f".format(it)} 1/s" },
                onChange = { v -> scope.launch { repo.setDampingCoef(v) } },
            )
            SliderRow(
                title = "Return-to-Center",
                value = settings.returnToCenterCoef,
                valueRange = 0.5f..5.0f,
                format = { "${"%.1f".format(it)} 1/s" },
                onChange = { v -> scope.launch { repo.setReturnToCenterCoef(v) } },
            )
            SliderRow(
                title = "Input Clamp",
                value = settings.inputClamp,
                valueRange = 3.0f..12.0f,
                format = { "${"%.1f".format(it)} m/s\u00B2" },
                onChange = { v -> scope.launch { repo.setInputClamp(v) } },
            )
            // --- Signal conditioning (spec Part B.4) ---
            SliderRow(
                title = "Deadzone",
                value = settings.deadzone,
                valueRange = 0.05f..0.5f,
                format = { "${"%.2f".format(it)} m/s\u00B2" },
                onChange = { v -> scope.launch { repo.setDeadzone(v) } },
            )
            // --- Visual parameters ---
            SliderRow(
                title = "Base Opacity",
                value = settings.dotOpacity,
                valueRange = 0.1f..1.0f,
                format = { "${(it * 100).toInt()}%" },
                onChange = { v -> scope.launch { repo.setDotOpacity(v) } },
            )
            SliderRow(
                title = "Edge Inset",
                value = settings.dotInsetDp,
                valueRange = 8f..40f,
                format = { "${it.toInt()} dp" },
                onChange = { v -> scope.launch { repo.setDotInsetDp(v) } },
            )
            // --- Intensity response ---
            SliderRow(
                title = "Intensity Response",
                value = settings.intensityResponse,
                valueRange = 0f..1f,
                format = { "${(it * 100).toInt()}%" },
                onChange = { v -> scope.launch { repo.setIntensityResponse(v) } },
            )
            // --- Context gate timing (Automatic mode, spec Part B.5) ---
            SliderRow(
                title = "Entry Delay",
                value = settings.gateEntryDelayMs.toFloat(),
                valueRange = 1000f..10000f,
                format = { "${(it / 1000).toInt()}s" },
                onChange = { v -> scope.launch { repo.setGateEntryDelayMs(v.toLong()) } },
            )
            SliderRow(
                title = "Exit Grace",
                value = settings.gateExitGraceMs.toFloat(),
                valueRange = 3000f..15000f,
                format = { "${(it / 1000).toInt()}s" },
                onChange = { v -> scope.launch { repo.setGateExitGraceMs(v.toLong()) } },
                showSeparator = false,
            )
        }

        // --- Section: Permissions ---
        PermissionsSection()

        // --- Section: Reset to Defaults ---
        GroupedSection {
            SettingsRow(
                title = "Reset to Defaults",
                trailing = {},
                showSeparator = false,
                onClick = { scope.launch { repo.reset() } },
            )
        }

        // --- Footer ---
        Spacer(Modifier.height(40.dp))
        Text(
            text = "Vehicle Motion Cues\nA reconstruction of the iOS accessibility feature.",
            style = typo.footnote,
            color = colors.tertiaryLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(40.dp))
    }

    // --- Dialogs ---
    if (showDisclaimer) {
        SafetyDisclaimer(onAck = {
            scope.launch { repo.ackSafety() }
            showDisclaimer = false
        })
    }
    if (showColorPicker) {
        ColorPickerDialog(
            currentColor = settings.dotColor,
            onColorSelected = { c -> scope.launch { repo.setDotColor(c) } },
            onDismiss = { showColorPicker = false },
        )
    }
}

// --- Helper composables ---

@Composable
private fun CheckMark() {
    Text("\u2713", color = IosTheme.colors.blue, fontWeight = FontWeight.Bold)
}

@Composable
private fun ColorSwatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = IosTheme.colors
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(color))
            .then(
                if (selected) Modifier.border(3.dp, colors.blue, CircleShape)
                else Modifier.border(0.5.dp, colors.separator, CircleShape)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
    )
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
    showSeparator: Boolean = true,
) {
    val colors = IosTheme.colors
    val typo = IosTheme.typography
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = typo.body, color = colors.label, modifier = Modifier.weight(1f))
            Text(format(value), style = typo.subheadline, color = colors.secondaryLabel)
        }
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            IosSlider(value = value, onValueChange = onChange, valueRange = valueRange)
        }
        if (showSeparator) {
            Box(
                Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(colors.separator),
            )
        }
    }
}

@Composable
private fun PermissionsSection() {
    val context = LocalContext.current
    val colors = IosTheme.colors
    val activityRecognitionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshKey by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    @Suppress("UNUSED_EXPRESSION") refreshKey

    GroupedSection(
        header = "Permissions",
        footer = "Required for the overlay and Automatic mode.",
    ) {
        PermissionRow("Display over other apps", "Required to draw dots on top of other apps",
            PermissionsHelper.hasOverlayPermission(context), "Open",
            Icons.Outlined.Warning, colors.red,
        ) { runCatching { context.startActivity(PermissionsHelper.overlaySettingsIntent(context)) } }
        PermissionRow("Activity Recognition", "Required for Automatic mode",
            PermissionsHelper.hasActivityRecognition(context), "Grant",
            Icons.Outlined.Warning, colors.red,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        PermissionRow("Notifications", "Keeps the service alive",
            PermissionsHelper.hasPostNotifications(context), "Grant",
            Icons.Outlined.Warning, colors.red,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        PermissionRow("Battery Optimization", "Prevents the service from being killed",
            PermissionsHelper.isBatteryOptExempt(context), "Open",
            Icons.Outlined.BatteryFull, colors.orange,
            showSeparator = false,
        ) { runCatching { context.startActivity(PermissionsHelper.batteryOptSettingsIntent(context)) } }
    }
}

@Composable
private fun PermissionRow(
    title: String, subtitle: String, granted: Boolean, actionLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector, iconTint: Color,
    showSeparator: Boolean = true, onClick: () -> Unit,
) {
    val colors = IosTheme.colors
    SettingsRow(
        title = title,
        subtitle = subtitle,
        icon = {
            Icon(
                if (granted) Icons.Outlined.CheckCircle else icon,
                contentDescription = null,
                tint = if (granted) colors.green else iconTint,
                modifier = Modifier.size(22.dp),
            )
        },
        trailing = {
            if (granted) {
                Text("Granted", style = IosTheme.typography.subheadline, color = colors.green)
            } else {
                Text(actionLabel, style = IosTheme.typography.subheadline, color = colors.blue,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                    ) { onClick() })
            }
        },
        showSeparator = showSeparator,
        onClick = if (!granted) onClick else null,
    )
}

@Composable
private fun SafetyDisclaimer(onAck: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text(stringResource(R.string.safety_title)) },
        text = { Text(stringResource(R.string.safety_body)) },
        confirmButton = { TextButton(onClick = onAck) { Text(stringResource(R.string.safety_ack)) } },
    )
}
