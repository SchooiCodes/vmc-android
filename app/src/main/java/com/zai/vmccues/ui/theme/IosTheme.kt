package com.zai.vmccues.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class IosColorScheme(
    val groupedBackground: Color,
    val secondaryGroupedBackground: Color,
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

private val LightColors = IosColorScheme(
    groupedBackground = Color(0xFFF2F2F7),
    secondaryGroupedBackground = Color.White,
    label = Color(0xFF1C1C1E),
    secondaryLabel = Color(0xFF6C6C70),
    tertiaryLabel = Color(0xFF98989E),
    quaternaryLabel = Color(0xFFC6C6C8),
    separator = Color(0xFFC6C6C8),
    fill = Color(0xFFE8E8ED),
    secondaryFill = Color(0xFFE8E8ED),
    blue = Color(0xFF007AFF),
    green = Color(0xFF34C759),
    red = Color(0xFFFF3B30),
    orange = Color(0xFFFF9500),
    teal = Color(0xFF30B0C7),
    purple = Color(0xFF5856D6),
)

private val DarkColors = IosColorScheme(
    groupedBackground = Color(0xFF000000),
    secondaryGroupedBackground = Color(0xFF1C1C1E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0xFF98989E),
    tertiaryLabel = Color(0xFF6C6C70),
    quaternaryLabel = Color(0xFF48484A),
    separator = Color(0xFF48484A),
    fill = Color(0xFF2C2C2E),
    secondaryFill = Color(0xFF2C2C2E),
    blue = Color(0xFF0A84FF),
    green = Color(0xFF30D158),
    red = Color(0xFFFF453A),
    orange = Color(0xFFFF9F0A),
    teal = Color(0xFF64D2FF),
    purple = Color(0xFFBF5AF2),
)

data class IosTypography(
    val largeTitle: TextStyle,
    val title1: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val subheadline: TextStyle,
    val footnote: TextStyle,
    val caption1: TextStyle,
    val caption2: TextStyle,
)

val DefaultTypography = IosTypography(
    largeTitle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    title1 = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    title2 = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    title3 = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    headline = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    body = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    callout = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    subheadline = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    footnote = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    caption1 = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    caption2 = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal),
)

val LocalIosColorScheme = staticCompositionLocalOf { LightColors }
val LocalIosTypography = staticCompositionLocalOf { DefaultTypography }

// iOS-style Material3 color scheme — light
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EAFF),
    onPrimaryContainer = Color(0xFF001A3D),
    secondary = Color(0xFF5856D6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0DEFF),
    onSecondaryContainer = Color(0xFF150066),
    tertiary = Color(0xFFFF2D55),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9DE),
    onTertiaryContainer = Color(0xFF3E001A),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF6C6C70),
    outline = Color(0xFFC6C6C8),
    outlineVariant = Color(0xFFE5E5EA),
)

// iOS-style Material3 color scheme — dark
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color(0xFF001A3D),
    primaryContainer = Color(0xFF004299),
    onPrimaryContainer = Color(0xFFD6EAFF),
    secondary = Color(0xFFBF5AF2),
    onSecondary = Color(0xFF22005D),
    secondaryContainer = Color(0xFF4A0099),
    onSecondaryContainer = Color(0xFFE0DEFF),
    tertiary = Color(0xFFFF453A),
    onTertiary = Color(0xFF3E001A),
    tertiaryContainer = Color(0xFF8C0030),
    onTertiaryContainer = Color(0xFFFFD9DE),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF98989E),
    outline = Color(0xFF48484A),
    outlineVariant = Color(0xFF38383A),
)

@Composable
fun VmcTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val iosColors = if (darkTheme) DarkColors else LightColors

    CompositionLocalProvider(
        LocalIosColorScheme provides iosColors,
        LocalIosTypography provides DefaultTypography,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

object IosTheme {
    val colors: IosColorScheme
        @Composable get() = LocalIosColorScheme.current
    val typography: IosTypography
        @Composable get() = LocalIosTypography.current
}
