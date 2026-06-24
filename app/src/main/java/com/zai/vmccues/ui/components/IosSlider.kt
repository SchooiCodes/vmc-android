package com.zai.vmccues.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.zai.vmccues.ui.theme.IosTheme

/**
 * iOS-style slider — thin 2pt track with a small 28pt white thumb and a
 * shadow. The filled portion uses the accent color (iOS blue by default).
 *
 * Faithful to UISlider proportions.
 *
 * @param value current value
 * @param onValueChange callback during drag
 * @param valueRange min..max
 * @param modifier
 */
@Composable
fun IosSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    val colors = IosTheme.colors
    val density = LocalDensity.current
    val trackHeightPx = with(density) { 3.dp.toPx() }
    val thumbRadiusPx = with(density) { 14.dp.toPx() }
    var canvasWidthPx by remember { mutableStateOf(1) }
    var isDragging by remember { mutableStateOf(false) }

    val min = valueRange.start
    val max = valueRange.endInclusive
    val fraction = ((value - min) / (max - min)).coerceIn(0f, 1f)

    fun fractionFromX(x: Float): Float =
        ((x - thumbRadiusPx) / (canvasWidthPx - 2 * thumbRadiusPx)).coerceIn(0f, 1f)

    fun valueFromX(x: Float): Float = min + fractionFromX(x) * (max - min)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp) // hit area
            .pointerInput(valueRange) {
                detectTapGestures { offset ->
                    onValueChange(valueFromX(offset.x))
                }
            }
            .pointerInput(valueRange) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        onValueChange(valueFromX(offset.x))
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                ) { change, _ ->
                    onValueChange(valueFromX(change.position.x))
                }
            },
    ) {
        canvasWidthPx = size.width.toInt().coerceAtLeast(1)
        val centerY = size.height / 2
        val trackStart = thumbRadiusPx
        val trackEnd = size.width - thumbRadiusPx
        val thumbX = trackStart + fraction * (trackEnd - trackStart)

        // Track background (unfilled portion).
        drawLine(
            color = colors.secondaryFill,
            start = Offset(trackStart, centerY),
            end = Offset(trackEnd, centerY),
            strokeWidth = trackHeightPx,
        )
        // Filled portion.
        drawLine(
            color = colors.blue,
            start = Offset(trackStart, centerY),
            end = Offset(thumbX, centerY),
            strokeWidth = trackHeightPx,
        )
        // Thumb shadow (a faint ring).
        if (isDragging) {
            drawCircle(
                color = colors.blue.copy(alpha = 0.2f),
                radius = thumbRadiusPx * 1.3f,
                center = Offset(thumbX, centerY),
            )
        }
        // Thumb (white circle with subtle shadow).
        drawCircle(
            color = Color(0x22000000),
            radius = thumbRadiusPx + 1,
            center = Offset(thumbX, centerY + 0.5f),
        )
        drawCircle(
            color = Color.White,
            radius = thumbRadiusPx,
            center = Offset(thumbX, centerY),
        )
    }
}
