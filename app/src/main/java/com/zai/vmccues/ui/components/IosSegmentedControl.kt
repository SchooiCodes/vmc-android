package com.zai.vmccues.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zai.vmccues.ui.theme.IosTheme

/**
 * iOS-style segmented control — a faithful recreation of UISegmentedControl.
 *
 *  - Light gray capsule container
 *  - Selected segment shows a white "pill" with a subtle shadow
 *  - Spring animation as the pill slides between segments
 *  - Text is label color when selected, secondaryLabel otherwise
 *
 * @param segments list of labels
 * @param selectedIndex currently selected index
 * @param onSelected callback when a segment is tapped
 */
@Composable
fun IosSegmentedControl(
    segments: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IosTheme.colors
    val density = LocalDensity.current
    var containerWidthPx by remember { mutableStateOf(0) }

    val segmentWidthDp = if (segments.isNotEmpty() && containerWidthPx > 0) {
        with(density) { (containerWidthPx / segments.size).toDp() }
    } else 0.dp

    val indicatorOffset by animateDpAsState(
        targetValue = segmentWidthDp * selectedIndex,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "indicatorOffset",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.secondaryFill)
            .onSizeChanged { containerWidthPx = it.width },
    ) {
        // Sliding white indicator pill.
        if (segmentWidthDp > 0.dp) {
            Box(
                Modifier
                    .width(segmentWidthDp)
                    .height(32.dp)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .shadow(1.dp, RoundedCornerShape(6.dp), clip = false)
                    .background(colors.secondaryGroupedBackground)
            )
        }
        // Segment labels on top.
        Row(
            Modifier.fillMaxWidth().height(32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            segments.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .width(segmentWidthDp)
                        .height(32.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelected(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = with(density) { 13.dp.toSp() },
                        fontWeight = if (i == selectedIndex) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (i == selectedIndex) colors.label else colors.secondaryLabel,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
