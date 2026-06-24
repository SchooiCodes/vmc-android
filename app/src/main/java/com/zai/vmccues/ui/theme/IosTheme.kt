package com.zai.vmccues.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * iOS-style color tokens exposed to composables via a CompositionLocal.
 * This is the cleanest way to access iOS-specific colors (like
 * `groupedBackground`, `separator`) that don't map 1:1 to Material 3 roles.
 */
data class IosColorScheme(
    val groupedBackground: Color,
    val secondaryGroupedBackground: Color,
    val tertiaryGroupedBackground: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val quaternaryLabel: Color,
    val separator: Color,
    val fill: Color,
    val secondaryFill: Color,
    val blue: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
    val teal: Color,
    val purple: Color,
)

val LightIosColorScheme = IosColorScheme(
    groupedBackground = IosGroupedBackground,
    secondaryGroupedBackground = IosSecondaryGroupedBackground,
    tertiaryGroupedBackground = IosTertiaryGroupedBackground,
    label = IosLabel,
    secondaryLabel = IosSecondaryLabel,
    tertiaryLabel = IosTertiaryLabel,
    quaternaryLabel = IosQuaternaryLabel,
    separator = IosSeparator,
    fill = IosFill,
    secondaryFill = IosSecondaryFill,
    blue = IosBlue,
    green = IosGreen,
    red = IosRed,
    orange = IosOrange,
    teal = IosTeal,
    purple = IosPurple,
)

val DarkIosColorScheme = IosColorScheme(
    groupedBackground = IosGroupedBackgroundDark,
    secondaryGroupedBackground = IosSecondaryGroupedBackgroundDark,
    tertiaryGroupedBackground = IosTertiaryGroupedBackgroundDark,
    label = IosLabelDark,
    secondaryLabel = IosSecondaryLabelDark,
    tertiaryLabel = IosTertiaryLabelDark,
    quaternaryLabel = IosQuaternaryLabelDark,
    separator = IosSeparatorDark,
    fill = IosFillDark,
    secondaryFill = IosSecondaryFillDark,
    blue = IosBlueDark,
    green = IosGreenDark,
    red = IosRedDark,
    orange = IosOrange,
    teal = IosTeal,
    purple = IosPurple,
)

val LocalIosColorScheme = staticCompositionLocalOf { LightIosColorScheme }

/**
 * iOS-style typography, sized to match SF Pro / system text styles.
 * On Android these render in Roboto by default, which is visually close
 * enough at these sizes.
 */
data class IosTypography(
    val largeTitle: TextStyle,    // 34pt bold — nav bar large title
    val title1: TextStyle,        // 28pt bold
    val title2: TextStyle,        // 22pt bold
    val title3: TextStyle,        // 20pt semibold
    val headline: TextStyle,      // 17pt semibold — row titles
    val body: TextStyle,          // 17pt regular — row titles
    val callout: TextStyle,       // 16pt regular
    val subheadline: TextStyle,   // 15pt regular — row subtitles
    val footnote: TextStyle,      // 13pt regular
    val caption1: TextStyle,      // 12pt regular
    val caption2: TextStyle,      // 11pt regular
)

val DefaultIosTypography = IosTypography(
    largeTitle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold),
    title1 = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    title2 = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    title3 = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    body = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal),
    callout = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    subheadline = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    footnote = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
    caption1 = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    caption2 = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal),
)

val LocalIosTypography = staticCompositionLocalOf { DefaultIosTypography }

/**
 * The app theme. Wraps Material 3 (for interop with libraries that expect
 * a MaterialTheme) but also provides iOS color + typography tokens via
 * CompositionLocals for our custom iOS-style components.
 */
@Composable
fun VmcTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val iosColors = if (darkTheme) DarkIosColorScheme else LightIosColorScheme
    // Material 3 color scheme derived from the iOS palette so any Material
    // components we do use still look reasonable.
    val materialColors = if (darkTheme) {
        darkColorScheme(
            primary = iosColors.blue,
            onPrimary = Color.White,
            background = iosColors.groupedBackground,
            onBackground = iosColors.label,
            surface = iosColors.secondaryGroupedBackground,
            onSurface = iosColors.label,
            error = iosColors.red,
        )
    } else {
        lightColorScheme(
            primary = iosColors.blue,
            onPrimary = Color.White,
            background = iosColors.groupedBackground,
            onBackground = iosColors.label,
            surface = iosColors.secondaryGroupedBackground,
            onSurface = iosColors.label,
            error = iosColors.red,
        )
    }
    CompositionLocalProvider(
        LocalIosColorScheme provides iosColors,
        LocalIosTypography provides DefaultIosTypography,
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            content = content,
        )
    }
}

/** Convenience accessor for the current iOS color scheme. */
object IosTheme {
    val colors: IosColorScheme
        @Composable get() = LocalIosColorScheme.current
    val typography: IosTypography
        @Composable get() = LocalIosTypography.current
}
