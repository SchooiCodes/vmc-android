package com.zai.vmccues.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zai.vmccues.ui.theme.IosTheme

/**
 * The 8 preset colors shown on the settings screen. Keeping them in sync
 * with [com.zai.vmccues.ui.SettingsScreen]'s preset list.
 */
private val PRESET_COLORS = listOf(
    0xFFF5F5F5.toInt(), 0xFF0A0A0A.toInt(), 0xFF34D399.toInt(),
    0xFFF59E0B.toInt(), 0xFFF43F5E.toInt(), 0xFFA78BFA.toInt(),
    0xFF22D3EE.toInt(), 0xFFFBBF24.toInt(),
)

/**
 * Reconstruct a packed ARGB Int from four 0..255 components.
 */
private fun argbToInt(a: Int, r: Int, g: Int, b: Int): Int =
    ((a and 0xFF) shl 24) or
        ((r and 0xFF) shl 16) or
        ((g and 0xFF) shl 8) or
        (b and 0xFF)

/**
 * An iOS-style color picker dialog. Lets the user choose a color by:
 *  1. Tapping one of 8 preset swatches (same presets as the settings screen).
 *  2. Dragging Red / Green / Blue sliders.
 *  3. Typing a 6-digit hex code.
 *
 * The alpha channel is preserved from [currentColor]; preset swatches and
 * 6-digit hex entry always use alpha = 0xFF.
 *
 * @param currentColor     the starting color (ARGB packed Int)
 * @param onColorSelected  invoked with the chosen color when the user taps Done
 * @param onDismiss        invoked when the user taps Cancel or dismisses the dialog
 */
@Composable
fun ColorPickerDialog(
    currentColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = IosTheme.colors
    val typo = IosTheme.typography

    // ARGB state, broken into four 0..255 channels so each slider can write
    // independently. Using `by remember { mutableStateOf }` means lambdas
    // captured by IosSlider's pointerInput always read the latest values.
    var a by remember { mutableStateOf((currentColor shr 24) and 0xFF) }
    var r by remember { mutableStateOf((currentColor shr 16) and 0xFF) }
    var g by remember { mutableStateOf((currentColor shr 8) and 0xFF) }
    var b by remember { mutableStateOf(currentColor and 0xFF) }

    // Hex input is its own state because the user may type partial/invalid
    // strings (e.g. "F3") that don't yet parse to a full color.
    var hexInput by remember {
        mutableStateOf(String.format("%06X", currentColor and 0xFFFFFF))
    }
    var hexError by remember { mutableStateOf(false) }

    val currentInt = argbToInt(a, r, g, b)
    val previewColor = Color(currentInt)

    /** Re-sync the hex field from the current RGB state. */
    fun syncHexFromRgb() {
        hexInput = String.format("%06X", (r shl 16) or (g shl 8) or b)
        hexError = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Choose a Color",
                style = typo.title3,
                color = colors.label,
                fontWeight = FontWeight.Normal,
            )
        },
        text = {
            Column {
                // --- Large color preview ---
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(previewColor)
                        .border(0.5.dp, colors.separator, RoundedCornerShape(12.dp)),
                )

                Spacer(Modifier.height(16.dp))

                // --- Preset swatches (8 in a row) ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    PRESET_COLORS.forEach { c ->
                        PresetSwatch(
                            color = c,
                            selected = currentInt == c,
                            onClick = {
                                a = (c shr 24) and 0xFF
                                r = (c shr 16) and 0xFF
                                g = (c shr 8) and 0xFF
                                b = c and 0xFF
                                syncHexFromRgb()
                            },
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // --- RGB sliders ---
                ColorSliderRow(
                    label = "Red",
                    value = r.toFloat(),
                    onValueChange = { v ->
                        r = v.toInt().coerceIn(0, 255)
                        syncHexFromRgb()
                    },
                )
                Spacer(Modifier.height(12.dp))
                ColorSliderRow(
                    label = "Green",
                    value = g.toFloat(),
                    onValueChange = { v ->
                        g = v.toInt().coerceIn(0, 255)
                        syncHexFromRgb()
                    },
                )
                Spacer(Modifier.height(12.dp))
                ColorSliderRow(
                    label = "Blue",
                    value = b.toFloat(),
                    onValueChange = { v ->
                        b = v.toInt().coerceIn(0, 255)
                        syncHexFromRgb()
                    },
                )

                Spacer(Modifier.height(20.dp))

                // --- Hex code text field ---
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { text ->
                        val cleaned = text.removePrefix("#").uppercase()
                        hexInput = cleaned
                        val parsed = cleaned.toIntOrNull(16)
                        when {
                            cleaned.isEmpty() -> {
                                hexError = false
                            }
                            cleaned.length == 6 && parsed != null -> {
                                r = (parsed shr 16) and 0xFF
                                g = (parsed shr 8) and 0xFF
                                b = parsed and 0xFF
                                hexError = false
                            }
                            else -> {
                                hexError = true
                            }
                        }
                    },
                    label = { Text("Hex", style = typo.subheadline) },
                    prefix = { Text("#", color = colors.secondaryLabel) },
                    singleLine = true,
                    isError = hexError,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onColorSelected(argbToInt(a, r, g, b))
                    onDismiss()
                },
            ) {
                Text(
                    text = "Done",
                    color = colors.blue,
                    fontWeight = FontWeight.Normal,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = colors.blue,
                    fontWeight = FontWeight.Normal,
                )
            }
        },
    )
}

/**
 * A labeled RGB slider row: shows the channel name on the left, the current
 * 0..255 value on the right, and an [IosSlider] below.
 */
@Composable
private fun ColorSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    val colors = IosTheme.colors
    val typo = IosTheme.typography
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = typo.subheadline,
                color = colors.label,
            )
            Text(
                text = "${value.toInt()}",
                style = typo.subheadline,
                color = colors.secondaryLabel,
            )
        }
        Spacer(Modifier.height(6.dp))
        IosSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..255f,
        )
    }
}

/**
 * A single circular preset swatch. Tapping it calls [onClick]. When selected,
 * a 3dp blue ring is drawn around the swatch (matching the settings screen).
 */
@Composable
private fun PresetSwatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = IosTheme.colors
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(color))
            .then(
                if (selected) {
                    Modifier.border(3.dp, colors.blue, CircleShape)
                } else {
                    Modifier.border(0.5.dp, colors.separator, CircleShape)
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
    )
}
