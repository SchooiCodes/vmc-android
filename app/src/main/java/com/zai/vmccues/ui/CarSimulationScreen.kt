package com.zai.vmccues.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zai.vmccues.data.CueSettings
import com.zai.vmccues.data.DotPattern
import com.zai.vmccues.motion.VehicleFrame
import com.zai.vmccues.ui.components.PreviewUtilities
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Dev test screen — simple sliders to apply lateral/longitudinal forces
 * and see how the dot overlay responds. No car, no game, just a test tool.
 */
@Composable
fun CarSimulationScreen(
    settings: CueSettings,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var lateralInput by remember { mutableFloatStateOf(0f) }
    var longitudinalInput by remember { mutableFloatStateOf(0f) }

    // Smoothed values for the integrator
    var smoothLat by remember { mutableFloatStateOf(0f) }
    var smoothLon by remember { mutableFloatStateOf(0f) }

    // Integrator state
    var posX by remember { mutableFloatStateOf(0f) }
    var posY by remember { mutableFloatStateOf(0f) }
    var velX by remember { mutableFloatStateOf(0f) }
    var velY by remember { mutableFloatStateOf(0f) }

    // Auto-test mode
    var autoTest by remember { mutableStateOf(false) }
    var t by remember { mutableFloatStateOf(0f) }

    // Screen dimensions for dot layout
    var screenW by remember { mutableFloatStateOf(1080f) }
    var screenH by remember { mutableFloatStateOf(1920f) }

    LaunchedEffect(Unit) {
        var lastFrame = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { nanos ->
                val dt = if (lastFrame == 0L) 1f / 60f
                else ((nanos - lastFrame) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
                lastFrame = nanos
                t = nanos / 1_000_000_000f

                // Auto-test: cycle through lateral and longitudinal forces
                if (autoTest) {
                    lateralInput = sin(t * PI / 1.5).toFloat().coerceIn(-1f, 1f)
                    longitudinalInput = sin(t * PI / 2.0 + 1f).toFloat().coerceIn(-1f, 1f)
                }

                // Smooth inputs
                smoothLat += (lateralInput - smoothLat) * 6f * dt
                smoothLon += (longitudinalInput - smoothLon) * 6f * dt

                // Convert to forces (m/s²)
                val latForce = smoothLat * 4f
                val lonForce = smoothLon * 3f

                val ax = VehicleFrame.smoothDeadzone(latForce, settings.deadzone)
                    .coerceIn(-settings.inputClamp, settings.inputClamp)
                val ay = VehicleFrame.smoothDeadzone(lonForce, settings.deadzone)
                    .coerceIn(-settings.inputClamp, settings.inputClamp)

                // Integrate with damping
                velX += (ax - velX * settings.dampingCoef) * dt
                velY += (ay - velY * settings.dampingCoef) * dt
                posX += velX * dt
                posY += velY * dt
                posX -= posX * settings.returnToCenterCoef * dt
                posY -= posY * settings.returnToCenterCoef * dt
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Header
        Text(
            text = "Force Test",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp),
        )

        // Dot preview canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                screenW = size.width
                screenH = size.height

                // Dark background
                drawRect(Color(0xFF0D1117))

                // Draw test dots
                drawTestDots(
                    offsetX = -posX * settings.sensitivity * 18f,
                    offsetY = -posY * settings.sensitivity * 18f,
                    settings = settings,
                    density = density,
                )
            }

            // Force readout + sensor diagnostics
            val latN = (smoothLat * 4f)
            val lonN = (smoothLon * 3f)
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(12.dp),
            ) {
                Text(
                    text = "Force Input",
                    color = Color(0xFF90CAF9),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Lat: ${"%.2f".format(latN)} m/s²",
                    color = Color.White,
                    fontSize = 12.sp,
                )
                Text(
                    text = "Lon: ${"%.2f".format(lonN)} m/s²",
                    color = Color.White,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Sensor Status",
                    color = Color(0xFF90CAF9),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Mode: ${settings.mode}",
                    color = Color(0xFFB0BEC5),
                    fontSize = 11.sp,
                )
                Text(
                    text = "Sensitivity: ${"%.1f".format(settings.sensitivity)}\u00D7",
                    color = Color(0xFFB0BEC5),
                    fontSize = 11.sp,
                )
                Text(
                    text = "Deadzone: ${"%.2f".format(settings.deadzone)} m/s²",
                    color = Color(0xFFB0BEC5),
                    fontSize = 11.sp,
                )
                Text(
                    text = "Opacity: ${(settings.dotOpacity * 100).toInt()}%",
                    color = Color(0xFFB0BEC5),
                    fontSize = 11.sp,
                )
                Text(
                    text = "Dots: ${if (settings.moreDots) "More" else "Standard"} ${if (settings.largerDots) "+ Larger" else ""}",
                    color = Color(0xFFB0BEC5),
                    fontSize = 11.sp,
                )
            }
        }

        // Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Lateral slider
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

            Spacer(Modifier.height(4.dp))

            // Longitudinal slider
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

            Spacer(Modifier.height(8.dp))

            // Buttons
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
                        smoothLat = 0f
                        smoothLon = 0f
                        posX = 0f; posY = 0f
                        velX = 0f; velY = 0f
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
        }
    }
}

private fun DrawScope.drawTestDots(
    offsetX: Float,
    offsetY: Float,
    settings: CueSettings,
    density: Float,
) {
    val w = size.width
    val h = size.height
    val inset = 20f * density
    val dotColor = Color(settings.dotColor)

    val sideCount = if (settings.moreDots) 10 else 6
    val endCount = if (settings.moreDots) 4 else 2
    val exclusion = 0.35f
    val baseRadius = (if (settings.largerDots) 11f else 6.5f) * density * 0.8f
    val ringWidth = 0.5f * density

    // Intensity for opacity
    val force = hypot(offsetX.toDouble(), offsetY.toDouble()).toFloat()
    val intensity = (force / 40f).coerceIn(0f, 1f)
    val alpha = (settings.dotOpacity + intensity * settings.intensityResponse).coerceIn(0.1f, 1f)

    // Ring color
    val ringColor = if (PreviewUtilities.isLightColor(settings.dotColor)) Color.Black else Color.White

    fun drawDot(cx: Float, cy: Float, dx: Float, dy: Float, sizeMul: Float) {
        val x = cx + dx
        val y = cy + dy
        val r = baseRadius * sizeMul
        if (r < 0.5f) return

        if (settings.autoContrast) {
            drawCircle(
                color = ringColor.copy(alpha = alpha * 0.4f),
                radius = r + ringWidth,
                center = Offset(x, y),
            )
        }
        drawCircle(
            color = dotColor.copy(alpha = alpha),
            radius = r,
            center = Offset(x, y),
        )
    }

    // Left dots (lateral)
    val leftX = inset
    val excludeTop = h * exclusion
    val excludeBot = h * (1f - exclusion)
    val availH = excludeBot - excludeTop
    for (i in 0 until sideCount) {
        val frac = (i + 0.5f) / sideCount
        val y = excludeTop + frac * availH
        val sizeMul = if (i == sideCount / 2) 1.2f else 0.8f + (i % 2) * 0.2f
        drawDot(leftX, y, -offsetX, 0f, sizeMul)
    }

    // Right dots (lateral)
    val rightX = w - inset
    for (i in 0 until sideCount) {
        val frac = (i + 0.5f) / sideCount
        val y = excludeTop + frac * availH
        val sizeMul = if (i == sideCount / 2) 1.2f else 0.8f + (i % 2) * 0.2f
        drawDot(rightX, y, -offsetX, 0f, sizeMul)
    }

    // Top dots (longitudinal)
    val topY = inset
    val excludeLeft = w * exclusion
    val excludeRight = w * (1f - exclusion)
    val availW = excludeRight - excludeLeft
    for (i in 0 until endCount) {
        val frac = (i + 0.5f) / endCount
        val x = excludeLeft + frac * availW
        drawDot(x, topY, 0f, -offsetY, 0.9f)
    }

    // Bottom dots (longitudinal)
    val botY = h - inset
    for (i in 0 until endCount) {
        val frac = (i + 0.5f) / endCount
        val x = excludeLeft + frac * availW
        drawDot(x, botY, 0f, -offsetY, 0.9f)
    }
}
