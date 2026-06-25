package com.zai.vmccues.ui.components

import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zai.vmccues.ui.theme.IosTheme

@Composable
fun IosSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = IosTheme.colors.blue,
            activeTrackColor = IosTheme.colors.blue,
            inactiveTrackColor = IosTheme.colors.separator,
        ),
    )
}
