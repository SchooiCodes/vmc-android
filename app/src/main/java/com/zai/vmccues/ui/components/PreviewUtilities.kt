package com.zai.vmccues.ui.components

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
     * meaning it should get a dark contrast ring. Mirrors the helper in
     * [com.zai.vmccues.overlay.DotOverlayView] exactly.
     */
    fun isLightColor(color: Int): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0 > 0.5
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