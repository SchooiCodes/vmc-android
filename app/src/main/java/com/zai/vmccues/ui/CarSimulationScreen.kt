package com.zai.vmccues.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zai.vmccues.data.CueSettings
import com.zai.vmccues.motion.ForceVector
import com.zai.vmccues.overlay.OverlayService
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun CarSimulationScreen(
    settings: CueSettings,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var lateralInput by remember { mutableFloatStateOf(0f) }
    var longitudinalInput by remember { mutableFloatStateOf(0f) }
    var autoTest by remember { mutableStateOf(false) }
    var serviceRunning by remember { mutableStateOf(false) }

    val ctx = LocalContext.current

    LaunchedEffect(Unit) {
        OverlayService.start(ctx)
    }

    LaunchedEffect(Unit) {
        while (true) {
            serviceRunning = OverlayService.instance != null
            delay(500)
        }
    }

    LaunchedEffect(Unit) {
        var lastFrame = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { nanos ->
                val dt = if (lastFrame == 0L) 1f / 60f
                else ((nanos - lastFrame) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
                lastFrame = nanos

                if (autoTest) {
                    val t = nanos / 1_000_000_000f
                    lateralInput = sin(t * PI / 1.5).toFloat().coerceIn(-1f, 1f)
                    longitudinalInput = sin(t * PI / 2.0 + 1f).toFloat().coerceIn(-1f, 1f)
                }

                val latForce = lateralInput * 10f
                val lonForce = longitudinalInput * 8f
                OverlayService.instance?.getPipeline()?.injectSimulatedForce(latForce, lonForce)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Text(
            text = "Force Test",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp),
        )

        if (!serviceRunning) {
            Text(
                text = "Overlay service not running. Enable it in Settings first.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            Text(
                text = "Dragging sliders moves the real overlay dots",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        SliderControls(lateralInput, longitudinalInput, autoTest,
            onLateralChange = { lateralInput = it },
            onLongitudinalChange = { longitudinalInput = it },
            onAutoTestToggle = { autoTest = !autoTest },
            onReset = { lateralInput = 0f; longitudinalInput = 0f },
            onOpenSettings = onOpenSettings,
        )

        Spacer(Modifier.height(8.dp))
        SensorDiagnostics(settings)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SliderControls(
    lateralInput: Float,
    longitudinalInput: Float,
    autoTest: Boolean,
    onLateralChange: (Float) -> Unit,
    onLongitudinalChange: (Float) -> Unit,
    onAutoTestToggle: () -> Unit,
    onReset: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text("Lateral (turn)", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = lateralInput,
            onValueChange = { if (!autoTest) onLateralChange(it) },
            valueRange = -1f..1f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Text(
            text = "${"%.2f".format(lateralInput * 10f)} m/s\u00B2",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        Text("Longitudinal (accel/brake)", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = longitudinalInput,
            onValueChange = { if (!autoTest) onLongitudinalChange(it) },
            valueRange = -1f..1f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.secondary,
                activeTrackColor = MaterialTheme.colorScheme.secondary,
            ),
        )
        Text(
            text = "${"%.2f".format(longitudinalInput * 8f)} m/s\u00B2",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onAutoTestToggle, modifier = Modifier.weight(1f)) {
                Text(if (autoTest) "Stop Auto" else "Auto Test")
            }
            Button(
                onClick = onReset,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) { Text("Reset") }
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) { Text("Settings") }
        }
    }
}

@Composable
private fun SensorDiagnostics(settings: CueSettings) {
    val svc = OverlayService.instance
    val pipeline = svc?.getPipeline()
    val zeroVec = remember { ForceVector.ZERO }
    val zeroAccel = remember { Triple(0f, 0f, 0f) }

    val rawAccel by (pipeline?.rawAccelValues?.collectAsState() ?: remember { mutableStateOf(zeroAccel) })
    val rawForce by (pipeline?.rawForce?.collectAsState() ?: remember { mutableStateOf(zeroVec) })
    val filteredForce by (pipeline?.filteredForce?.collectAsState() ?: remember { mutableStateOf(zeroVec) })
    val dotOffset by (pipeline?.dotOffsetPx?.collectAsState() ?: remember { mutableStateOf(zeroVec) })

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(12.dp),
    ) {
        Text("Live Sensor Data", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text("Accel: X=${"%.2f".format(rawAccel.first)} Y=${"%.2f".format(rawAccel.second)} Z=${"%.2f".format(rawAccel.third)} m/s\u00B2",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Raw: lat=${"%.2f".format(rawForce.lateral)} lon=${"%.2f".format(rawForce.longitudinal)} m/s\u00B2",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Filtered: lat=${"%.2f".format(filteredForce.lateral)} lon=${"%.2f".format(filteredForce.longitudinal)} m/s\u00B2",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Offset: X=${"%.1f".format(dotOffset.lateral)} Y=${"%.1f".format(dotOffset.longitudinal)} px",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(6.dp))
        Text("Settings", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text("Mode: ${settings.mode} | Sens: ${"%.1f".format(settings.sensitivity)}\u00D7 | Dead: ${"%.2f".format(settings.deadzone)} m/s\u00B2",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Dots: ${if (settings.moreDots) "More" else "Std"} ${if (settings.largerDots) "+Larger" else ""} | Opacity: ${(settings.dotOpacity * 100).toInt()}%",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
