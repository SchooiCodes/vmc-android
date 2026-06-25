package com.zai.vmccues.ui.components

import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import com.zai.vmccues.data.DotPattern
import com.zai.vmccues.motion.ForceVector

/**
 * Shared utility functions used by both LivePreview.kt and DotOverlayView.kt
 * to ensure consistent rendering and physics behavior.
 */
object PreviewUtilities {

    /**
     * Returns true if the ARGB [color] is perceived as "light" (luminance > 0.5),
     * meaning it should get a dark contrast ring.
     */
    fun isLightColor(color: Int): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0 > 0.5
    }

    /**
     * Adjust the user-chosen dot color's brightness and saturation to maintain
     * visible contrast against the current background, without replacing the hue.
     * Mirrors Apple's "saturation auto-adjusts to maintain contrast" behavior.
     *
     * - Light background: darken the dot significantly so it stands out
     * - Dark background: brighten the dot so it's visible
     */
    fun adjustSaturationForContrast(color: Int, bgLight: Boolean): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val hue = hsv[0]
        var sat = hsv[1]
        var value = hsv[2]
        val alpha = Color.alpha(color)

        if (bgLight) {
            // Background is light — darken the dot, boost saturation for visibility.
            value = (value * 0.45f).coerceIn(0f, 1f)
            sat = (sat * 1.2f).coerceIn(0f, 1f)
        } else {
            // Background is dark — brighten the dot significantly.
            value = (value * 1.4f + 0.3f).coerceIn(0f, 1f)
            sat = (sat * 0.9f).coerceIn(0f, 1f)
        }

        hsv[1] = sat
        hsv[2] = value
        val adjusted = Color.HSVToColor(hsv)
        return (adjusted and 0x00FFFFFF) or (alpha shl 24)
    }

    /**
     * Detect low-end devices (≤2 GB RAM or ≤2 cores) so we can reduce
     * dot count and use slower sensor sampling.
     */
    fun detectLowEnd(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalMemGB = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        val cores = Runtime.getRuntime().availableProcessors()
        return totalMemGB <= 2.0 || cores <= 2
    }

    /**
     * Build the dot positions around the four edges of the preview rectangle.
     * Mirrors [com.zai.vmccues.overlay.DotOverlayView.buildLayout]:
     *  - Left/right edges hold HORIZONTAL-axis dots (respond to lateral force).
     *    [sideCount] per edge (6-10 per side).
     *  - Top/bottom edges hold VERTICAL-axis dots (respond to longitudinal force).
     *    [endCount] per edge (2-4 per edge).
     *  - The center of each edge is left dot-free per [centerExclusion].
     *  - For DYNAMIC pattern, each dot gets a deterministic phase + sizeMul so
     *    the wobble looks alive but doesn't flicker between frames.
     */
    fun buildDotLayout(
        width: Float,
        height: Float,
        sideCount: Int,
        endCount: Int,
        insetPx: Float,
        centerExclusion: Float,
        dynamic: Boolean,
        density: Float = 1f,
    ): List<DotSpec> {
        val safeInset = insetPx.coerceAtLeast(MIN_DOT_INSET_DP * density)
        val out = ArrayList<DotSpec>(sideCount * 2 + endCount * 2)
        var i = 0

        // Left + right edges (respond to lateral force → horizontal shift).
        val ys = positionsAlong(height, sideCount, centerExclusion)
        for (yPos in ys) {
            val (phase, sizeMul) = dotJitter(i++, dynamic)
            out.add(DotSpec(safeInset, yPos, Axis.HORIZONTAL, phase, sizeMul))
            out.add(DotSpec(width - safeInset, yPos, Axis.HORIZONTAL, phase, sizeMul))
        }

        // Top + bottom edges (respond to longitudinal force → vertical shift).
        val xs = positionsAlong(width, endCount, centerExclusion)
        for (xPos in xs) {
            val (phase, sizeMul) = dotJitter(i++, dynamic)
            out.add(DotSpec(xPos, safeInset, Axis.VERTICAL, phase, sizeMul))
            out.add(DotSpec(xPos, height - safeInset, Axis.VERTICAL, phase, sizeMul))
        }

        return out
    }

    /**
     * Distribute [n] positions along [len] in two outer segments with a gap in
     * the middle (per [centerExclusion]). Mirrors DotOverlayView.positionsAlong
     * so the preview matches the real overlay's layout exactly (just smaller).
     */
    fun positionsAlong(len: Float, n: Int, centerExclusion: Float): List<Float> {
        val excludeStart = len * (0.5f - centerExclusion / 2f)
        val excludeEnd = len * (0.5f + centerExclusion / 2f)
        val half = (n + 1) / 2
        val out = ArrayList<Float>(n)

        for (i in 0 until half) {
            val frac = if (half == 1) 0.5f else i.toFloat() / (half - 1)
            out.add(excludeStart * frac)
        }

        for (i in 0 until n - half) {
            val frac = if (n - half == 1) 0.5f else i.toFloat() / (n - half - 1)
            out.add(excludeEnd + (len - excludeEnd) * frac)
        }

        return out.sorted()
    }

    /** Deterministic per-index phase/sizeMul — no per-frame randomness. */
    private fun dotJitter(index: Int, dynamic: Boolean): Pair<Float, Float> {
        if (!dynamic) return 0f to 1f
        // ~π/4 per dot, wraps at 2π; sizeMul alternates 0.85 / 1.00 / 1.15.
        val phase = (index * 0.7853f) % 6.2832f
        val sizeMul = 0.85f + (index % 3) * 0.15f
        return phase to sizeMul
    }

    private const val MIN_DOT_INSET_DP = 16f

    /** Which edge axis a dot lives on — determines which force moves it. */
    enum class Axis { HORIZONTAL, VERTICAL }

    /** A single dot's rest position + per-dot jitter for the DYNAMIC pattern. */
    data class DotSpec(
        val restX: Float,
        val restY: Float,
        val axis: Axis,
        val phase: Float,
        val sizeMul: Float,
    )

    /** Runtime state for each dot: current offset + opacity for animation. */
    data class Offset(var x: Float, var y: Float, var opacity: Float)
}