package com.zai.vmccues.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit = {}, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as VmcApplication
    val repo = app.settings
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = CueSettings())
    val scroll = rememberScrollState()

    var showDisclaimer by remember { mutableStateOf(!settings.safetyAcknowledged) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scroll),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("Done")
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(64.dp))
        }

        // Mode section
        SectionCard(header = "Mode") {
            ModeOption("Off", settings.mode == ActivationMode.OFF) {
                scope.launch {
                    repo.setMode(ActivationMode.OFF)
                    OverlayService.stop(context)
                }
            }
            ModeOption("On", settings.mode == ActivationMode.ON) {
                scope.launch {
                    repo.setMode(ActivationMode.ON)
                    OverlayService.start(context)
                }
            }
            ModeOption("Automatic", settings.mode == ActivationMode.AUTOMATIC, showSeparator = false) {
                scope.launch {
                    repo.setMode(ActivationMode.AUTOMATIC)
                    OverlayService.start(context)
                }
            }
        }
        SectionFooter(when (settings.mode) {
            ActivationMode.OFF -> "Motion cues are off."
            ActivationMode.ON -> "Dots always shown while service runs."
            ActivationMode.AUTOMATIC -> "Dots appear when in a moving vehicle."
        })

        // Appearance section
        SectionCard(header = "Appearance") {
            // Pattern
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Pattern", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilterChip(
                        selected = settings.pattern == DotPattern.REGULAR,
                        onClick = { scope.launch { repo.setPattern(DotPattern.REGULAR) } },
                        label = { Text("Regular") },
                    )
                    androidx.compose.material3.FilterChip(
                        selected = settings.pattern == DotPattern.DYNAMIC,
                        onClick = { scope.launch { repo.setPattern(DotPattern.DYNAMIC) } },
                        label = { Text("Dynamic") },
                    )
                }
            }
            // Adaptive Contrast
            SwitchSetting(
                title = "Adaptive Contrast",
                checked = settings.adaptiveContrast,
                onCheckedChange = { v -> scope.launch { repo.setAdaptiveContrast(v) } },
            )
            // Larger Dots
            SwitchSetting(
                title = "Larger Dots",
                checked = settings.largerDots,
                onCheckedChange = { v -> scope.launch { repo.setLargerDots(v) } },
            )
            // More Dots
            SwitchSetting(
                title = "More Dots",
                checked = settings.moreDots,
                onCheckedChange = { v -> scope.launch { repo.setMoreDots(v) } },
            )
        }

        // Response section
        SectionCard(header = "Response") {
            SliderSetting(
                title = "Sensitivity",
                value = settings.sensitivity,
                valueRange = 0.5f..3.0f,
                format = { "${"%.1f".format(it)}\u00D7" },
                onValueChange = { v -> scope.launch { repo.setSensitivity(v) } },
            )
        }

        // Permissions
        PermissionsSection()

        // Advanced toggle
        SectionCard {
            RowSetting(title = "Advanced Settings", showSeparator = false, onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) "\u25B2" else "\u25BC", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Advanced settings
        AnimatedVisibility(visible = showAdvanced) {
            SectionCard(header = "Advanced") {
                SwitchSetting(
                    title = "Auto-Contrast",
                    subtitle = "Halo keeps dots visible on any background",
                    checked = settings.autoContrast,
                    onCheckedChange = { v -> scope.launch { repo.setAutoContrast(v) } },
                )
                SliderSetting("Filter Alpha", settings.filterAlpha, 0.10f..0.30f, { "%.2f".format(it) }) { v -> scope.launch { repo.setFilterAlpha(v) } }
                SliderSetting("Damping", settings.dampingCoef, 2.0f..10.0f, { "${"%.1f".format(it)} 1/s" }) { v -> scope.launch { repo.setDampingCoef(v) } }
                SliderSetting("Return to Center", settings.returnToCenterCoef, 0.5f..5.0f, { "${"%.1f".format(it)} 1/s" }) { v -> scope.launch { repo.setReturnToCenterCoef(v) } }
                SliderSetting("Input Clamp", settings.inputClamp, 3.0f..12.0f, { "${"%.1f".format(it)} m/s\u00B2" }) { v -> scope.launch { repo.setInputClamp(v) } }
                SliderSetting("Deadzone", settings.deadzone, 0.05f..0.5f, { "${"%.2f".format(it)} m/s\u00B2" }) { v -> scope.launch { repo.setDeadzone(v) } }
                SliderSetting("Base Opacity", settings.dotOpacity, 0.1f..1.0f, { "${(it * 100).toInt()}%" }) { v -> scope.launch { repo.setDotOpacity(v) } }
                SliderSetting("Edge Inset", settings.dotInsetDp, 8f..40f, { "${it.toInt()} dp" }) { v -> scope.launch { repo.setDotInsetDp(v) } }
                SliderSetting("Intensity Response", settings.intensityResponse, 0f..1f, { "${(it * 100).toInt()}%" }) { v -> scope.launch { repo.setIntensityResponse(v) } }
                SliderSetting("Entry Delay", settings.gateEntryDelayMs.toFloat(), 1000f..10000f, { "${(it / 1000).toInt()}s" }) { v -> scope.launch { repo.setGateEntryDelayMs(v.toLong()) } }
                SliderSetting("Exit Grace", settings.gateExitGraceMs.toFloat(), 3000f..15000f, { "${(it / 1000).toInt()}s" }, showSeparator = false) { v -> scope.launch { repo.setGateExitGraceMs(v.toLong()) } }
            }
        }

        // Reset
        SectionCard {
            RowSetting(title = "Reset to Defaults", showSeparator = false, onClick = { showResetDialog = true }) {}
        }

        // Footer
        Spacer(Modifier.height(32.dp))
        Text(
            text = "Vehicle Motion Cues v1.5.0",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        )
        Spacer(Modifier.height(32.dp))
    }

    // Dialogs
    if (showDisclaimer) {
        SafetyDisclaimer(onAck = {
            scope.launch { repo.ackSafety() }
            showDisclaimer = false
        })
    }
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Settings") },
            text = { Text("Reset all settings to defaults?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repo.reset() }
                    showResetDialog = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            },
        )
    }
}

// --- Reusable Material components ---

@Composable
private fun SectionCard(
    header: String? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (header != null) {
            Text(
                text = header,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp, top = 24.dp, bottom = 8.dp),
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SectionFooter(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 32.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
    )
}

@Composable
private fun ModeOption(title: String, selected: Boolean, showSeparator: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
    if (showSeparator) {
        Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
    }
}

@Composable
private fun RowSetting(
    title: String,
    showSeparator: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        trailing()
    }
    if (showSeparator) {
        Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                uncheckedThumbColor = Color.White,
            ),
        )
    }
}

@Composable
private fun SliderSetting(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    showSeparator: Boolean = true,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(format(value), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
        if (showSeparator) {
            Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
        }
    }
}

// --- Permissions ---

@Composable
private fun PermissionsSection() {
    val context = LocalContext.current
    val arLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshKey by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    key(refreshKey) {
        SectionCard(header = "Permissions") {
            PermissionRow("Overlay", PermissionsHelper.hasOverlayPermission(context), "Open",
                Icons.Outlined.Warning, MaterialTheme.colorScheme.error,
            ) { runCatching { context.startActivity(PermissionsHelper.overlaySettingsIntent(context)) } }
            PermissionRow("Activity Recognition", PermissionsHelper.hasActivityRecognition(context), "Grant",
                Icons.Outlined.Warning, MaterialTheme.colorScheme.error,
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    arLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            PermissionRow("Notifications", PermissionsHelper.hasPostNotifications(context), "Grant",
                Icons.Outlined.Warning, MaterialTheme.colorScheme.error,
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            PermissionRow("Battery", PermissionsHelper.isBatteryOptExempt(context), "Open",
                Icons.Outlined.BatteryFull, MaterialTheme.colorScheme.tertiary,
                showSeparator = false,
            ) { runCatching { context.startActivity(PermissionsHelper.batteryOptSettingsIntent(context)) } }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String, granted: Boolean, actionLabel: String,
    icon: ImageVector, iconTint: Color,
    showSeparator: Boolean = true, onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !granted, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (granted) Icons.Outlined.CheckCircle else icon,
            contentDescription = null,
            tint = if (granted) MaterialTheme.colorScheme.primary else iconTint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (!granted) {
            Text(actionLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
    if (showSeparator) {
        Box(Modifier.fillMaxWidth().padding(start = 48.dp).height(0.5.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
    }
}

@Composable
private fun SafetyDisclaimer(onAck: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text(stringResource(R.string.safety_title)) },
        text = { Text(stringResource(R.string.safety_body)) },
        confirmButton = {
            TextButton(onClick = onAck) {
                Text(stringResource(R.string.safety_ack))
            }
        },
    )
}
