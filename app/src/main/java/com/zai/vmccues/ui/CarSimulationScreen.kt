package com.zai.vmccues.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zai.vmccues.data.CueSettings
import com.zai.vmccues.data.DotPattern
import com.zai.vmccues.motion.VehicleFrame
import com.zai.vmccues.ui.components.PreviewUtilities
import com.zai.vmccues.ui.theme.IosTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun CarSimulationScreen(
    settings: CueSettings,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var steerInput by remember { mutableFloatStateOf(0f) }
    var accelInput by remember { mutableFloatStateOf(0f) }

    // Smoothed inputs for natural feel
    var smoothSteer by remember { mutableFloatStateOf(0f) }
    var smoothAccel by remember { mutableFloatStateOf(0f) }

    // Integrator state
    var posX by remember { mutableFloatStateOf(0f) }
    var posY by remember { mutableFloatStateOf(0f) }
    var velX by remember { mutableFloatStateOf(0f) }
    var velY by remember { mutableFloatStateOf(0f) }
    var smoothAx by remember { mutableFloatStateOf(0f) }
    var smoothAy by remember { mutableFloatStateOf(0f) }

    // Car visual angle
    var carAngle by remember { mutableFloatStateOf(0f) }

    // Auto-drive mode
    var autoMode by remember { mutableStateOf(false) }
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

                // Auto-drive: sinusoidal steering + periodic accel/brake
                if (autoMode) {
                    steerInput = (2f * sin(t * PI / 2.0).toFloat()).coerceIn(-1f, 1f)
                    val pulse = (t % 4f) / 4f
                    accelInput = if (pulse < 0.3f) {
                        (1.5f * sin(pulse * PI / 0.3).toFloat()).coerceIn(-1f, 1f)
                    } else if (pulse in 0.5f..0.7f) {
                        (-1.2f * sin((pulse - 0.5f) * PI / 0.2).toFloat()).coerceIn(-1f, 1f)
                    } else 0f
                }

                // Smooth inputs
                smoothSteer += (steerInput - smoothSteer) * 8f * dt
                smoothAccel += (accelInput - smoothAccel) * 8f * dt

                // Convert to vehicle forces
                val lateral = smoothSteer * 4f
                val longitudinal = smoothAccel * 3f

                val ax = VehicleFrame.smoothDeadzone(lateral, settings.deadzone)
                    .coerceIn(-settings.inputClamp, settings.inputClamp)
                val ay = VehicleFrame.smoothDeadzone(longitudinal, settings.deadzone)
                    .coerceIn(-settings.inputClamp, settings.inputClamp)

                // Integrate
                smoothAx += (ax - smoothAx) * settings.filterAlpha
                smoothAy += (ay - smoothAy) * settings.filterAlpha
                velX += (smoothAx - velX * settings.dampingCoef) * dt
                velY += (smoothAy - velY * settings.dampingCoef) * dt
                posX += velX * dt
                posY += velY * dt
                posX -= posX * settings.returnToCenterCoef * dt
                posY -= posY * settings.returnToCenterCoef * dt

                // Car visual angle follows steer
                carAngle += (smoothSteer * 25f - carAngle) * 5f * dt
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Title
        Text(
            text = "Drive Simulation",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "Drag to steer and accelerate",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        // Main simulation area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Canvas with car + dots
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            steerInput = (steerInput + dragAmount.x / 100f).coerceIn(-1f, 1f)
                            accelInput = (accelInput - dragAmount.y / 150f).coerceIn(-1f, 1f)
                        }
                    }
            ) {
                screenW = size.width
                screenH = size.height

                // Background
                drawRect(Color(0xFF0D1117))

                // Road markings
                drawRoadMarkings()

                // Car
                drawCar(carAngle)

                // Dots around edges
                drawMotionDots(
                    offsetX = -posX * settings.sensitivity * 18f,
                    offsetY = -posY * settings.sensitivity * 18f,
                    settings = settings,
                    density = density.density,
                )
            }

            // Speed indicator
            val speed = hypot(velX.toDouble(), velY.toDouble()).toFloat()
            val speedKmh = (speed * 3.6f).toInt().coerceIn(0, 120)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "${speedKmh} km/h",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            // Steer indicator
            val steerPercent = (smoothSteer * 100).toInt()
            val steerText = when {
                steerPercent > 5 -> "Right ${steerPercent}%"
                steerPercent < -5 -> "Left ${-steerPercent}%"
                else -> "Center"
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = steerText,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Auto/Manual toggle
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = { autoMode = !autoMode },
                    modifier = Modifier.height(40.dp),
                ) {
                    Text(
                        text = if (autoMode) "Auto Drive" else "Manual",
                        fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                FilledTonalButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.height(40.dp),
                ) {
                    Text(text = "Settings", fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Force indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ForceBar("Turn", smoothSteer, IosTheme.colors.blue)
                Spacer(Modifier.width(16.dp))
                ForceBar("Accel", smoothAccel, if (smoothAccel >= 0) IosTheme.colors.green else IosTheme.colors.red)
            }

            Spacer(Modifier.height(8.dp))

            // Reset button
            FilledTonalButton(
                onClick = {
                    steerInput = 0f
                    accelInput = 0f
                    smoothSteer = 0f
                    smoothAccel = 0f
                    posX = 0f; posY = 0f
                    velX = 0f; velY = 0f
                    smoothAx = 0f; smoothAy = 0f
                },
                modifier = Modifier.height(40.dp),
            ) {
                Text(text = "Reset", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ForceBar(label: String, value: Float, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val animatedFraction by animateFloatAsState(
                targetValue = (value + 1f) / 2f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "bar",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction.coerceIn(0f, 1f))
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
            )
        }
        Text(
            text = "${(value * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun DrawScope.drawRoadMarkings() {
    val w = size.width
    val h = size.height
    val roadColor = Color(0xFF1A2332)
    val lineColor = Color(0xFF2A3A4A)

    // Road surface
    drawRect(roadColor)

    // Center dashed line
    val dashLen = 40f
    val gapLen = 30f
    var y = 0f
    while (y < h) {
        drawRect(
            color = lineColor,
            topLeft = Offset(w / 2f - 1.5f, y),
            size = Size(3f, dashLen),
        )
        y += dashLen + gapLen
    }

    // Edge lines
    drawRect(lineColor, Offset(w * 0.08f, 0f), Size(2f, h))
    drawRect(lineColor, Offset(w * 0.92f - 2f, 0f), Size(2f, h))
}

private fun DrawScope.drawCar(angle: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val carW = 40f
    val carH = 70f

    val path = Path().apply {
        moveTo(-carW / 2f, -carH / 2f)
        lineTo(carW / 2f, -carH / 2f)
        lineTo(carW / 2f - 5f, carH / 2f)
        lineTo(-carW / 2f + 5f, carH / 2f)
        close()
    }

    rotate(angle, Offset(cx, cy)) {
        // Shadow
        translate(cx + 3f, cy + 3f) {
            drawPath(path, Color.Black.copy(alpha = 0.3f), style = Fill)
        }

        // Car body
        translate(cx, cy) {
            drawPath(path, Color(0xFF3B82F6), style = Fill)
        }

        // Windshield
        drawRect(
            color = Color(0xFF1E40AF).copy(alpha = 0.6f),
            topLeft = Offset(cx - carW / 2f + 6f, cy - carH / 2f + 10f),
            size = Size(carW - 12f, 18f),
        )

        // Headlights
        drawCircle(Color(0xFFFBBF24), 4f, Offset(cx - 10f, cy - carH / 2f + 2f))
        drawCircle(Color(0xFFFBBF24), 4f, Offset(cx + 10f, cy - carH / 2f + 2f))

        // Taillights
        drawCircle(Color(0xFFEF4444), 3f, Offset(cx - 12f, cy + carH / 2f - 5f))
        drawCircle(Color(0xFFEF4444), 3f, Offset(cx + 12f, cy + carH / 2f - 5f))
    }
}

private fun DrawScope.drawMotionDots(
    offsetX: Float,
    offsetY: Float,
    settings: CueSettings,
    density: Float,
) {
    val w = size.width
    val h = size.height
    val inset = 24f * density
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

    // Ring color (contrast)
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

    // Left dots (respond to lateral)
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

    // Right dots
    val rightX = w - inset
    for (i in 0 until sideCount) {
        val frac = (i + 0.5f) / sideCount
        val y = excludeTop + frac * availH
        val sizeMul = if (i == sideCount / 2) 1.2f else 0.8f + (i % 2) * 0.2f
        drawDot(rightX, y, -offsetX, 0f, sizeMul)
    }

    // Top dots (respond to longitudinal)
    val topY = inset
    val excludeLeft = w * exclusion
    val excludeRight = w * (1f - exclusion)
    val availW = excludeRight - excludeLeft
    for (i in 0 until endCount) {
        val frac = (i + 0.5f) / endCount
        val x = excludeLeft + frac * availW
        drawDot(x, topY, 0f, -offsetY, 0.9f)
    }

    // Bottom dots
    val botY = h - inset
    for (i in 0 until endCount) {
        val frac = (i + 0.5f) / endCount
        val x = excludeLeft + frac * availW
        drawDot(x, botY, 0f, -offsetY, 0.9f)
    }
}
