package com.zai.vmccues.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zai.vmccues.data.CueSettings
import com.zai.vmccues.data.DotPattern
import com.zai.vmccues.ui.theme.IosTheme
import com.zai.vmccues.motion.VehicleFrame
import com.zai.vmccues.ui.components.PreviewUtilities
import kotlin.math.hypot
import kotlin.math.sin

/**
 * An in-app phone-shaped preview that renders animated peripheral dots
 * driven by *simulated* vehicle motion, so users can see how their settings
 * (color, size, count, opacity, pattern, auto-contrast, sensitivity,
 * filterAlpha, dampingCoef, returnToCenterCoef, inputClamp, deadzone) will
 * look without leaving the app.
 *
 * The preview is intentionally self-contained: it doesn't depend on the
 * real overlay, the motion pipeline, or any permission. It synthesises a
 * driving scenario (sine-wave cornering + periodic accel/brake pulses)
 * locally and feeds it through the **dead-reckoning integrator with
 * critical damping** (spec Part B.3) — the same algorithm the real
 * [com.zai.vmccues.motion.MotionPipeline] uses, just replicated inside
 * this composable so the preview stays a single self-contained file.
 *
 * The algorithm (spec Part B.3, mirrored from
 * [com.zai.vmccues.motion.DeadReckoningIntegrator]):
 *   1. low-pass filter the raw acceleration (denoise)
 *   2. integrate accel → velocity with spring-damper damping
 *   3. integrate velocity → position with return-to-center pull
 *   4. negate position so dots appear anchored to the outside world
 *
 * Direction mapping (spec Part A.5/B.6, achieved by the negation in step 4):
 *   - Turn right → dots drift LEFT   (positive lateral force)
 *   - Turn left  → dots drift RIGHT  (negative lateral force)
 *   - Accelerate → dots drift BACKWARD (toward top of screen, +longitudinal)
 *   - Brake      → dots drift FORWARD  (toward bottom of screen, -longitudinal)
 *
 * DOT RENDERING (iOS-faithful, mirrors [com.zai.vmccues.overlay.DotOverlayView]):
 * Each dot is a simple solid circle — NO radial gradients, NO halos. To
 * achieve Apple's "auto-contrast" effect, a thin contrast ring is drawn
 * just behind the solid dot: a circle ~0.5dp larger in radius, filled with
 * the inverse-luminance color of the dot (dark ring for light dots, light
 * ring for dark dots) at ~35% opacity. The solid dot is then drawn on top
 * at the full per-dot opacity. This matches the iOS look exactly: crisp,
 * clean dots that remain visible on any background without any glow.
 *
 * DOT LAYOUT (spec Part A.5/B.7, mirrors DotOverlayView.buildLayout):
 * Dots live PRIMARILY on the left and right edges (6 per side regular,
 * 10 per side with "More Dots") — these respond to lateral force via a
 * horizontal shift. A few dots (2 regular, 4 with "More Dots") sit on
 * the top and bottom edges and respond to longitudinal force via a
 * vertical shift. A center-exclusion band (0.35) keeps the middle of
 * each edge dot-free so dots don't occlude central content.
 *
 * Layout: a 160dp × 338dp (~9:19) rounded-rectangle phone shell containing
 * fake "app content" (a title bar, a paragraph of text-line blocks, an
 * image placeholder, another paragraph) with peripheral dots arranged
 * around the inside edges. A "Live Preview" caption sits below.
 */
@Composable
fun LivePreview(settings: CueSettings, modifier: Modifier = Modifier) {
    val colors = IosTheme.colors
    val typography = IosTheme.typography
    val density = LocalDensity.current

    // Preview dimensions: ~9:19 phone aspect, ~160dp wide.
    val previewWidthDp = 160.dp
    val previewHeightDp = 338.dp

    // Pre-compute density-dependent pixel sizes used by the canvas drawing.
    val previewWidthPx = with(density) { previewWidthDp.toPx() }
    val previewHeightPx = with(density) { previewHeightDp.toPx() }
    val cornerRadiusPx = with(density) { 30.dp.toPx() }
    val screenInsetPx = with(density) { 6.dp.toPx() }
    val screenRadiusPx = with(density) { 24.dp.toPx() }
    val outlineWidthPx = with(density) { 1.dp.toPx() }
    val contentPadPx = with(density) { 10.dp.toPx() }
    val lineHPx = with(density) { 6.dp.toPx() }
    val lineGapPx = with(density) { 12.dp.toPx() }
    val titleHPx = with(density) { 10.dp.toPx() }
    val titleRadiusPx = with(density) { 5.dp.toPx() }
    val titleGapPx = with(density) { 22.dp.toPx() }
    val imageHPx = with(density) { 56.dp.toPx() }
    val imageRadiusPx = with(density) { 8.dp.toPx() }
    val imageGapPx = with(density) { 66.dp.toPx() }
    // Contrast ring width: ~0.5dp (scaled down from the overlay's 1dp ring).
    val ringWidthPx = with(density) { 0.5.dp.toPx() }

    // rememberUpdatedState so the long-running LaunchedEffect below always
    // reads the latest settings without needing to restart when they change.
    val currentSettings by rememberUpdatedState(settings)

    // Animation clock in seconds, plus the integrated dot offset (already
    // negated + scaled). These are observable so the Canvas redraws each
    // frame as the values change.
    var t by remember { mutableFloatStateOf(0f) }
    var dotOffsetX by remember { mutableFloatStateOf(0f) }
    var dotOffsetY by remember { mutableFloatStateOf(0f) }

    // Dead-reckoning integrator state — held across frames in a remembered
    // holder (not directly observable; only the resulting dotOffset is).
    val integrator = remember { DeadReckoningState() }

    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        while (true) {
            withFrameNanos { frameTimeNanos ->
                // dt from frame time, clamped to a safe range (kills jitter
                // on the first frame + after pauses, and avoids huge steps
                // if the app is backgrounded).
                val dt = if (lastFrameNanos == 0L) {
                    1f / 60f
                } else {
                    ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f)
                        .coerceIn(MIN_DT_SEC, MAX_DT_SEC)
                }
                lastFrameNanos = frameTimeNanos
                t = frameTimeNanos / 1_000_000_000f

                val s = currentSettings

                // ---- Simulated vehicle-frame forces (spec B.6) ----
                // Sine wave for lateral (cornering) + sine + periodic pulse
                // for longitudinal (accel/brake). Same shape as a real
                // driving trace.
                val (lateralRaw, longitudinalRaw) = simulateForces(t)

                // ---- Deadzone (spec B.4) ----
                // Smooth ramp so subtle forces still produce visible movement.
                val lateral = VehicleFrame.smoothDeadzone(lateralRaw, s.deadzone)
                val longitudinal = VehicleFrame.smoothDeadzone(longitudinalRaw, s.deadzone)

                // ---- Clamp (spec B.4) ----
                // Symmetric clamp so a pothole / hard-brake spike doesn't
                // throw the integrator into a huge excursion.
                val ax = lateral.coerceIn(-s.inputClamp, s.inputClamp)
                val ay = longitudinal.coerceIn(-s.inputClamp, s.inputClamp)

                // ---- Dead-reckoning integration (spec B.3) ----
                // Self-contained replication of DeadReckoningIntegrator.update:
                //   1. low-pass filter the input acceleration
                //   2. integrate accel → velocity (spring-damper)
                //   3. integrate velocity → position (return-to-center pull)
                integrator.update(
                    ax = ax,
                    ay = ay,
                    dt = dt,
                    filterAlpha = s.filterAlpha,
                    dampingCoef = s.dampingCoef,
                    returnToCenterCoef = s.returnToCenterCoef,
                    inputClamp = s.inputClamp,
                )

                // ---- Negate position so dots appear anchored to earth ----
                // (spec B.3: "Negating the calculated position vector
                // achieves this effect"). Scale to preview pixels:
                //   - PX_PER_MS2 = 50 (same as the real overlay)
                //   - PREVIEW_DISPLACEMENT_SCALE = 0.30 (smaller preview)
                //   - user sensitivity (applied last, like MotionPipeline)
                val scaleX = PX_PER_MS2 * PREVIEW_DISPLACEMENT_SCALE * s.sensitivity
                val scaleY = PX_PER_MS2 * PREVIEW_DISPLACEMENT_SCALE * s.sensitivity
                dotOffsetX = -integrator.positionX * scaleX
                dotOffsetY = -integrator.positionY * scaleY
            }
        }
    }

    // Dot layout only depends on geometry + a handful of settings, so we
    // remember it across frames to avoid per-frame allocation + flicker.
    val dotLayout = remember(
        previewWidthPx, previewHeightPx,
        settings.moreDots, settings.pattern,
        settings.dotInsetDp,
        density.density,
    ) {
        PreviewUtilities.buildDotLayout(
            width = previewWidthPx,
            height = previewHeightPx,
            sideCount = if (settings.moreDots) SIDE_COUNT_MORE else SIDE_COUNT_BASE,
            endCount = if (settings.moreDots) END_COUNT_MORE else END_COUNT_BASE,
            insetPx = settings.dotInsetDp * PREVIEW_INSET_SCALE * density.density,
            centerExclusion = CENTER_EXCLUSION,
            dynamic = settings.pattern == DotPattern.DYNAMIC,
            density = density.density,
        )
    }

    Column(
        modifier = modifier.wrapContentSize(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            modifier = Modifier.size(previewWidthDp, previewHeightDp),
        ) {
            val w = size.width
            val h = size.height
            val cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)

            // ---- 1. Phone shell (rounded-rect fill + thin separator outline) ----
            drawRoundRect(
                color = colors.secondaryGroupedBackground,
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = cornerRadius,
            )
            drawRoundRect(
                color = colors.separator,
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = cornerRadius,
                style = Stroke(width = outlineWidthPx),
            )

            // ---- 2. Screen (inset inside the phone shell) ----
            val screenLeft = screenInsetPx
            val screenTop = screenInsetPx
            val screenW = w - 2f * screenInsetPx
            val screenH = h - 2f * screenInsetPx
            val screenRadius = CornerRadius(screenRadiusPx, screenRadiusPx)
            drawRoundRect(
                color = colors.groupedBackground,
                topLeft = Offset(screenLeft, screenTop),
                size = Size(screenW, screenH),
                cornerRadius = screenRadius,
            )

            // ---- 3. Fake "app content" inside the screen ----
            // Title bar, paragraph of gray text-line blocks, image
            // placeholder, then another short paragraph.
            val lineColor = colors.tertiaryLabel.copy(alpha = 0.30f)
            val titleColor = colors.label.copy(alpha = 0.65f)
            val imageColor = colors.fill
            val lineRadius = CornerRadius(lineHPx / 2f, lineHPx / 2f)
            val titleRadius = CornerRadius(titleRadiusPx, titleRadiusPx)
            val imageRadius = CornerRadius(imageRadiusPx, imageRadiusPx)

            var y = screenTop + contentPadPx
            // Title bar
            drawRoundRect(
                color = titleColor,
                topLeft = Offset(screenLeft + contentPadPx, y),
                size = Size(screenW * 0.55f, titleHPx),
                cornerRadius = titleRadius,
            )
            y += titleGapPx
            // Paragraph 1
            for (frac in PARAGRAPH_1_FRACTIONS) {
                drawRoundRect(
                    color = lineColor,
                    topLeft = Offset(screenLeft + contentPadPx, y),
                    size = Size((screenW - 2f * contentPadPx) * frac, lineHPx),
                    cornerRadius = lineRadius,
                )
                y += lineGapPx
            }
            // Image placeholder
            y += lineGapPx
            drawRoundRect(
                color = imageColor,
                topLeft = Offset(screenLeft + contentPadPx, y),
                size = Size(screenW - 2f * contentPadPx, imageHPx),
                cornerRadius = imageRadius,
            )
            y += imageGapPx
            // Paragraph 2
            for (frac in PARAGRAPH_2_FRACTIONS) {
                drawRoundRect(
                    color = lineColor,
                    topLeft = Offset(screenLeft + contentPadPx, y),
                    size = Size((screenW - 2f * contentPadPx) * frac, lineHPx),
                    cornerRadius = lineRadius,
                )
                y += lineGapPx
            }

            // ---- 4. Intensity (from the latest simulated forces) ----
            val (lateralNow, longitudinalNow) = simulateForces(t)
            val intensity = (hypot(lateralNow.toDouble(), longitudinalNow.toDouble()) / 2.5f).toFloat().coerceIn(0f, 1f)
            val baseOpacity = (settings.dotOpacity + intensity * settings.intensityResponse)
                .coerceIn(0.05f, 1f)
            val alpha = baseOpacity.coerceIn(0f, 1f)
            if (alpha < 0.01f) return@Canvas

            // ---- 5. Peripheral dots around the phone edges ----
            // iOS-faithful rendering: solid circles + thin contrast ring,
            // exactly matching DotOverlayView (no radial gradients, no halos).
            val baseRadiusPx = (if (settings.largerDots) LARGER_RADIUS_DP else BASE_RADIUS_DP) *
                PREVIEW_RADIUS_SCALE * density.density
            val dotColor = Color(settings.dotColor)
            // Contrast ring color: inverse luminance of the dot color.
            val ringColorArgb = if (isLightColor(settings.dotColor)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            val dyn = settings.pattern == DotPattern.DYNAMIC

            // The dot offset is already negated + scaled (set by the
            // LaunchedEffect above). Lateral → horizontal, longitudinal →
            // vertical. Each dot only moves along its own edge axis.
            val dxTarget = dotOffsetX
            val dyTarget = dotOffsetY

            for (dot in dotLayout) {
                var dx = 0f
                var dy = 0f
                if (dot.axis == PreviewUtilities.Axis.HORIZONTAL) dx = dxTarget else dy = dyTarget
                if (dyn) {
                    // Subtle sine wobble (1.5f amplitude) — matches DotOverlayView.
                    val wob = sin((t * 1.3f + dot.phase).toDouble()).toFloat() * 1.5f
                    if (dot.axis == PreviewUtilities.Axis.HORIZONTAL) dx += wob else dy += wob
                }
                val cx = dot.restX + dx
                val cy = dot.restY + dy
                val r = baseRadiusPx * dot.sizeMul
                if (r < 0.5f) continue

                // Contrast ring: a slightly larger circle in the inverse-
                // luminance color at ~35% opacity. Drawn first, behind the
                // solid dot. Gives a thin dark (or light) border that makes
                // the dot visible on any background — Apple's auto-contrast.
                 if (settings.autoContrast) {
                    drawCircle(
                        color = Color(ringColorArgb.toInt() and 0x00FFFFFF or ((alpha * 0.40f * 255).toInt() shl 24)),
                        radius = r + ringWidthPx,
                        center = Offset(cx, cy),
                    )
                }

                // The dot itself: a simple solid circle, full opacity.
                drawCircle(
                    color = dotColor.copy(alpha = alpha),
                    radius = r,
                    center = Offset(cx, cy),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Live Preview",
            style = typography.caption1.copy(fontWeight = FontWeight.Normal),
            color = colors.secondaryLabel,
        )
    }
}

// --- Constants -------------------------------------------------------------

// Dot layout counts (mirror DotOverlayView exactly).
private const val SIDE_COUNT_BASE = 6
private const val SIDE_COUNT_MORE = 10
private const val END_COUNT_BASE = 2
private const val END_COUNT_MORE = 4
private const val CENTER_EXCLUSION = 0.35f

// Preview scaling — the preview is a ~0.45× miniature of a real phone.
private const val PREVIEW_INSET_SCALE = 0.45f
private const val PREVIEW_RADIUS_SCALE = 0.45f
private const val PREVIEW_DISPLACEMENT_SCALE = 0.30f

// Dead-reckoning scale: m/s² integrated → px (matches the real overlay's
// pxPerMs2 = 50). Multiplied by PREVIEW_DISPLACEMENT_SCALE + sensitivity in
// the LaunchedEffect above.
private const val PX_PER_MS2 = 50f

// Frame-time clamps so the integrator can't blow up on a stalled frame.
private const val MIN_DT_SEC = 1e-5f
private const val MAX_DT_SEC = 0.1f

// Dot base radii in dp — mirrors DotOverlayView.BASE_RADIUS (7f) /
// LARGER_RADIUS (12f) — scaled down by PREVIEW_RADIUS_SCALE for the preview.
private const val BASE_RADIUS_DP = 6.5f
private const val LARGER_RADIUS_DP = 11f

// Longitudinal pulse: a short 2.5 m/s² "brake" burst every 4 seconds.
private const val PULSE_PERIOD_SEC = 4f
private const val PULSE_DURATION_SEC = 0.4f
private const val PULSE_AMPLITUDE = 3.5f

// Fake-content paragraph line-width fractions (sum of widths, not positions).
private val PARAGRAPH_1_FRACTIONS = floatArrayOf(0.92f, 0.88f, 0.95f, 0.60f, 0.90f, 0.85f, 0.70f)
private val PARAGRAPH_2_FRACTIONS = floatArrayOf(0.85f, 0.95f, 0.60f)

// --- Helpers ---------------------------------------------------------------

/** Helper method to convert color int to isLightColor using shared utilities */
private fun isLightColor(color: Int): Boolean = PreviewUtilities.isLightColor(color)

/**
 * Simulates vehicle-frame forces for the preview (spec B.6).
 * Returns a pair of (lateral, longitudinal) acceleration in m/s².
 * - Lateral: sine wave for cornering (period 4s, amplitude 1.2 m/s²)
 * - Longitudinal: sine wave + periodic pulse for accel/brake
 */
private fun simulateForces(t: Float): Pair<Float, Float> {
    val lateral = (2.5f * kotlin.math.sin(t * kotlin.math.PI / 1.8f)).toFloat()
    val sine = (1.5f * kotlin.math.sin(t * kotlin.math.PI / 1.2f)).toFloat()
    val pulsePhase = (t % PULSE_PERIOD_SEC) / PULSE_PERIOD_SEC
    val pulse = if (pulsePhase < PULSE_DURATION_SEC / PULSE_PERIOD_SEC) {
        (-PULSE_AMPLITUDE * kotlin.math.sin(pulsePhase * kotlin.math.PI / (PULSE_DURATION_SEC / PULSE_PERIOD_SEC))).toFloat()
    } else 0f
    val longitudinal = sine + pulse
    return Pair(lateral, longitudinal)
}

/**
 * Dead-reckoning state holder for the preview.
 * Mirrors the logic in DeadReckoningIntegrator but as a simple data class.
 */
private class DeadReckoningState {
    var velocityX: Float = 0f
    var velocityY: Float = 0f
    var positionX: Float = 0f
    var positionY: Float = 0f
    var smoothAccelX: Float = 0f
    var smoothAccelY: Float = 0f

    fun update(
        ax: Float,
        ay: Float,
        dt: Float,
        filterAlpha: Float,
        dampingCoef: Float,
        returnToCenterCoef: Float,
        inputClamp: Float,
    ) {
        var clampedAx = ax.coerceIn(-inputClamp, inputClamp)
        var clampedAy = ay.coerceIn(-inputClamp, inputClamp)

        // 1. Low-pass filter to denoise
        smoothAccelX += (clampedAx - smoothAccelX) * filterAlpha
        smoothAccelY += (clampedAy - smoothAccelY) * filterAlpha

        // 2. Integrate acceleration → velocity with spring-damper damping
        velocityX += (smoothAccelX - velocityX * dampingCoef) * dt
        velocityY += (smoothAccelY - velocityY * dampingCoef) * dt

        // 3. Integrate velocity → position
        positionX += velocityX * dt
        positionY += velocityY * dt

        // 4. Return-to-center pull on position
        positionX -= positionX * returnToCenterCoef * dt
        positionY -= positionY * returnToCenterCoef * dt
    }
}
