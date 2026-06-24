package com.zai.vmccues.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import com.zai.vmccues.data.CueSettings
import com.zai.vmccues.data.DotPattern
import com.zai.vmccues.motion.ForceVector
import com.zai.vmccues.motion.VehicleFrame
import com.zai.vmccues.ui.components.PreviewUtilities
import kotlin.math.sin

/**
 * Layer 3 renderer — the dot overlay (spec Part B.7).
 *
 * Dots are simple solid circles with a thin contrast ring (matching Apple's
 * actual rendering). Dots live along the screen edges, PRIMARILY the left and
 * right edges (spec Part A.5: "left and right edges primarily").
 *
 * The dot displacement comes from the [MotionPipeline]'s dead-reckoning
 * integrator (already negated so dots appear anchored to the outside world).
 * Lateral force → horizontal dot shift; longitudinal → vertical shift.
 *
 * Direction mapping (spec Part A.5/B.6, already handled by the integrator's
 * negation):
 *   - Turn right → dots drift LEFT
 *   - Turn left → dots drift RIGHT
 *   - Accelerate → dots drift BACKWARD (toward top of screen)
 *   - Brake → dots drift FORWARD (toward bottom of screen)
 */
class DotOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var settings: CueSettings = CueSettings()
    private var dotOffset: ForceVector = ForceVector.ZERO
    private var dotsVisible: Boolean = false

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private var layout: List<PreviewUtilities.DotSpec> = emptyList()
    private val cur: MutableList<PreviewUtilities.Offset> = mutableListOf()
    private var widthPx: Int = 0
    private var heightPx: Int = 0

    private val choreographer = Choreographer.getInstance()
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            invalidate()
            choreographer.postFrameCallback(this)
        }
    }

    fun setSettings(s: CueSettings) {
        if (s == settings) return
        settings = s
        rebuildLayout()
    }
    fun setDotOffset(offset: ForceVector) { dotOffset = offset }
    fun setDotsVisible(visible: Boolean) { dotsVisible = visible }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        choreographer.postFrameCallback(frameCallback)
    }
    override fun onDetachedFromWindow() {
        choreographer.removeFrameCallback(frameCallback)
        super.onDetachedFromWindow()
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        widthPx = w; heightPx = h
        rebuildLayout()
    }

    private fun rebuildLayout() {
        val w = widthPx.takeIf { it > 0 } ?: return
        val h = heightPx.takeIf { it > 0 } ?: return
        val density = resources.displayMetrics.density
        // Build dot layout using shared PreviewUtilities - must match DotSpec structure
        val newLayout = PreviewUtilities.buildDotLayout(
            width = w.toFloat(),
            height = h.toFloat(),
            sideCount = if (settings.moreDots) 10 else 6,
            endCount = if (settings.moreDots) 4 else 2,
            insetPx = settings.dotInsetDp * density,
            centerExclusion = 0.35f,
            dynamic = settings.pattern == DotPattern.DYNAMIC,
        )
        layout = newLayout
        cur.clear()
        repeat(newLayout.size) { cur.add(PreviewUtilities.Offset(0f, 0f, 0f)) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val dots = layout
        if (dots.isEmpty()) return

        val s = settings
        val density = resources.displayMetrics.density
        val baseRadius = (if (s.largerDots) LARGER_RADIUS else BASE_RADIUS) * density / 2f
        val ringWidth = 1f * density

        // The dot offset already comes negated from the integrator (so dots
        // appear anchored to the earth). Lateral → horizontal, longitudinal → vertical.
        val dxTarget = dotOffset.lateral
        val dyTarget = dotOffset.longitudinal

        // Intensity from the raw force magnitude (for opacity modulation).
        val intensity = VehicleFrame.forceToIntensity(_rawForceForIntensity)
        val baseOpacity = (s.dotOpacity + intensity * s.intensityResponse).coerceIn(0.05f, 1f)
        val targetOpacity = if (dotsVisible) baseOpacity else 0f

        val now = SystemClock.elapsedRealtime() / 1000f
        val color = s.dotColor
        val ringColor = if (isLightColor(color)) Color.BLACK else Color.WHITE

        for (i in dots.indices) {
            val d = dots[i]
            val c = cur[i]
            var tx = 0f; var ty = 0f
            if (d.axis == PreviewUtilities.Axis.HORIZONTAL) tx = dxTarget else ty = dyTarget
            // Dynamic pattern: very subtle per-dot wobble.
            if (s.pattern == DotPattern.DYNAMIC) {
                val wob = sin((now * 1.3f + d.phase).toDouble()).toFloat() * 1.5f
                if (d.axis == PreviewUtilities.Axis.HORIZONTAL) tx += wob else ty += wob
            }
            c.x += (tx - c.x) * VISUAL_LERP
            c.y += (ty - c.y) * VISUAL_LERP
            c.opacity += (targetOpacity - c.opacity) * 0.12f

            val cx = d.restX + c.x
            val cy = d.restY + c.y
            val r = baseRadius * d.sizeMul
            val alpha = c.opacity.coerceIn(0f, 1f)
            if (alpha < 0.01f) continue

            val alpha255 = (alpha * 255).toInt()
            if (s.autoContrast) {
                val ringAlpha = (alpha * 0.35f * 255).toInt()
                ringPaint.color = (ringAlpha shl 24) or (ringColor and 0x00FFFFFF)
                canvas.drawCircle(cx, cy, r + ringWidth, ringPaint)
            }
            dotPaint.color = (alpha255 shl 24) or (color and 0x00FFFFFF)
            canvas.drawCircle(cx, cy, r, dotPaint)
        }
    }

    // Latest raw force for intensity calculation (set by the service).
    @Volatile private var _rawForceForIntensity: ForceVector = ForceVector.ZERO
    fun setRawForce(f: ForceVector) { _rawForceForIntensity = f }

    private fun isLightColor(color: Int): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0 > 0.5
    }

    companion object {
        private const val BASE_RADIUS = 7f       // dp (~6-10pt, spec B.7)
        private const val LARGER_RADIUS = 12f    // dp (~12-18pt, spec B.7)
        private const val VISUAL_LERP = 0.18f
    }
}
