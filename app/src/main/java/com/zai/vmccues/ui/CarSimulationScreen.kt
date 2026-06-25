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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zai.vmccues.data.ActivationMode
import com.zai.vmccues.data.CueSettings
import com.zai.vmccues.overlay.OverlayService
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Dev test screen — feeds simulated forces into the REAL overlay pipeline
 * so the actual system dots move. No separate canvas.
 */
@Composable
fun CarSimulationScreen(
    settings: CueSettings,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var lateralInput by remember { mutableFloatStateOf(0f) }
    var longitudinalInput by remember { mutableFloatStateOf(0f) }
    var autoTest by remember { mutableStateOf(false) }
    var t by remember { mutableFloatStateOf(0f) }
    var serviceRunning by remember { mutableStateOf(false) }

    val ctx = LocalContext.current

    // Ensure the overlay service is running in ON mode for testing
    LaunchedEffect(Unit) {
        OverlayService.start(ctx)
    }

    // Check if service is running
    LaunchedEffect(Unit) {
        while (true) {
            serviceRunning = OverlayService.instance != null
            delay(500)
        }
    }

    // Feed forces into the real pipeline
    LaunchedEffect(Unit) {
        var lastFrame = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { nanos ->
                val dt = if (lastFrame == 0L) 1f / 60f
                else ((nanos - lastFrame) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
                lastFrame = nanos
                t = nanos / 1_000_000_000f

                if (autoTest) {
                    lateralInput = sin(t * PI / 1.5).toFloat().coerceIn(-1f, 1f)
                    longitudinalInput = sin(t * PI / 2.0 + 1f).toFloat().coerceIn(-1f, 1f)
                }

                // Convert slider input (-1..1) to force (m/s²)
                val latForce = lateralInput * 6f
                val lonForce = longitudinalInput * 5f

                // Inject into the real pipeline
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("Lateral (turn)", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = lateralInput,
                onValueChange = { if (!autoTest) lateralInput = it },
                valueRange = -1f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Text(
                text = "${"%.2f".format(lateralInput * 6f)} m/s²",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            Text("Longitudinal (accel/brake)", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = longitudinalInput,
                onValueChange = { if (!autoTest) longitudinalInput = it },
                valueRange = -1f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.secondary,
                    activeTrackColor = MaterialTheme.colorScheme.secondary,
                ),
            )
            Text(
                text = "${"%.2f".format(longitudinalInput * 5f)} m/s²",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { autoTest = !autoTest },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (autoTest) "Stop Auto" else "Auto Test")
                }
                Button(
                    onClick = {
                        lateralInput = 0f
                        longitudinalInput = 0f
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text("Reset")
                }
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text("Settings")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Diagnostics
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                    .padding(12.dp),
            ) {
                Text("Diagnostics", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("Mode: ${settings.mode}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Sensitivity: ${"%.1f".format(settings.sensitivity)}\u00D7", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Deadzone: ${"%.2f".format(settings.deadzone)} m/s²", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Dots: ${if (settings.moreDots) "More" else "Standard"} ${if (settings.largerDots) "+ Larger" else ""}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Opacity: ${(settings.dotOpacity * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
