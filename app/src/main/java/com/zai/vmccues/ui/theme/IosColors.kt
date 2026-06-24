package com.zai.vmccues.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * iOS system color palette, reproduced faithfully for the Android port.
 * Values sourced from Apple's Human Interface Guidelines color specifications.
 * Each color has a light and dark variant.
 */

// --- Accents ---
val IosBlue = Color(0xFF007AFF)         // systemBlue (light)
val IosBlueDark = Color(0xFF0A84FF)     // systemBlue (dark)
val IosGreen = Color(0xFF34C759)        // systemGreen (light) — switch ON color
val IosGreenDark = Color(0xFF30D158)    // systemGreen (dark)
val IosRed = Color(0xFFFF3B30)          // systemRed (light)
val IosRedDark = Color(0xFFFF453A)      // systemRed (dark)
val IosOrange = Color(0xFFFF9500)       // systemOrange
val IosTeal = Color(0xFF30B0C7)         // systemTeal
val IosPurple = Color(0xFF5856D6)       // systemPurple
val IosPink = Color(0xFFFF2D55)         // systemPink
val IosYellow = Color(0xFFFFCC00)       // systemYellow

// --- Backgrounds (grouped style) ---
val IosGroupedBackground = Color(0xFFF2F2F7)       // systemGroupedBackground (light)
val IosGroupedBackgroundDark = Color(0xFF000000)   // systemGroupedBackground (dark)
val IosSecondaryGroupedBackground = Color(0xFFFFFFFF)      // secondarySystemGroupedBackground (light)
val IosSecondaryGroupedBackgroundDark = Color(0xFF1C1C1E)  // secondarySystemGroupedBackground (dark)
val IosTertiaryGroupedBackground = Color(0xFFF2F2F7)       // tertiarySystemGroupedBackground (light)
val IosTertiaryGroupedBackgroundDark = Color(0xFF2C2C2E)   // tertiarySystemGroupedBackground (dark)

// --- Labels (text) ---
val IosLabel = Color(0xFF000000)                    // label (light)
val IosLabelDark = Color(0xFFFFFFFF)                // label (dark)
val IosSecondaryLabel = Color(0x993C3C43)           // secondaryLabel (light) — 60% opacity
val IosSecondaryLabelDark = Color(0x99EBEBF5)       // secondaryLabel (dark)
val IosTertiaryLabel = Color(0x4D3C3C43)            // tertiaryLabel (light) — 30%
val IosTertiaryLabelDark = Color(0x4DEBEBF5)        // tertiaryLabel (dark)
val IosQuaternaryLabel = Color(0x2D3C3C43)          // quaternaryLabel (light) — 18%
val IosQuaternaryLabelDark = Color(0x2DEBEBF5)      // quaternaryLabel (dark)

// --- Separators ---
val IosSeparator = Color(0x293C3C43)                // separator (light) — 16%
val IosSeparatorDark = Color(0x54545899.toInt())    // separator (dark) — 65% of #545458

// --- Fills ---
val IosFill = Color(0xFF787880).copy(alpha = 0.16f)             // systemFill (light)
val IosFillDark = Color(0xFF8E8E93).copy(alpha = 0.24f)         // systemFill (dark)
val IosSecondaryFill = Color(0xFF787880).copy(alpha = 0.12f)    // secondarySystemFill (light)
val IosSecondaryFillDark = Color(0xFF8E8E93).copy(alpha = 0.18f)
