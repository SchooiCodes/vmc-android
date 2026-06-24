package com.zai.vmccues.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zai.vmccues.ui.theme.IosTheme

/**
 * A grouped "section" card as seen in iOS Settings > Accessibility.
 *
 *  - Inset grouped style: 16pt margins on each side, 10pt corner radius
 *  - Card background: secondarySystemGroupedBackground (white / #1C1C1E)
 *  - Optional header text ABOVE the card (small uppercase secondaryLabel)
 *  - Optional footer text BELOW the card (small secondaryLabel)
 *  - Rows are laid out vertically inside the card; separators between rows
 *    are drawn by [SettingsRow] itself
 */
@Composable
fun GroupedSection(
    header: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable GroupedSectionScope.() -> Unit,
) {
    val colors = IosTheme.colors
    val typo = IosTheme.typography
    Column(modifier = modifier.fillMaxWidth()) {
        if (header != null) {
            Text(
                text = header.uppercase(),
                style = typo.caption1,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = 32.dp, end = 16.dp, bottom = 6.dp, top = 18.dp),
                letterSpacing = 0.4.sp,
            )
        }
        // The card.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.secondaryGroupedBackground),
        ) {
            GroupedSectionScope().content()
        }
        if (footer != null) {
            Text(
                text = footer,
                style = typo.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 6.dp),
            )
        }
    }
}

/** Receiver scope for [GroupedSection] content — provides [SettingsRow]. */
class GroupedSectionScope internal constructor() {
    @Composable
    fun SettingsRow(
        title: String,
        subtitle: String? = null,
        icon: @Composable (() -> Unit)? = null,
        trailing: @Composable (() -> Unit)? = null,
        showSeparator: Boolean = true,
        onClick: (() -> Unit)? = null,
    ) {
        com.zai.vmccues.ui.components.SettingsRow(
            title = title,
            subtitle = subtitle,
            icon = icon,
            trailing = trailing,
            showSeparator = showSeparator,
            onClick = onClick,
        )
    }
}

/**
 * A single iOS settings row. Title on the left (with optional icon),
 * control/value on the right, inset separator at the bottom.
 */
@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    icon: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    showSeparator: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colors = IosTheme.colors
    val typo = IosTheme.typography
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickableNoRipple(onClick) else Modifier),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (subtitle != null) 10.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    Modifier
                        .width(28.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    icon()
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = typo.body,
                    color = colors.label,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = typo.subheadline,
                        color = colors.secondaryLabel,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
        if (showSeparator) {
            // Inset separator — starts at the left text boundary, not the
            // edge of the card, matching iOS.
            val insetStart = if (icon != null) 56.dp else 16.dp
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = insetStart)
                    .height(0.5.dp)
                    .background(colors.separator),
            )
        }
    }
}

/** Helper: clickable without the Material ripple (iOS has no ripple). */
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(
        Modifier.clickable(
            interactionSource = MutableInteractionSource(),
            indication = null,
        ) { onClick() }
    )
