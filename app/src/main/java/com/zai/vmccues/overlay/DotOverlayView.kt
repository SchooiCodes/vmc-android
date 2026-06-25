package com.zai.vmccues.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import android.view.View
import com.zai.vmccues.data.CueSettings
import com.zai.vmccues.data.DotPattern
import com.zai.vmccues.motion.ForceVector
import com.zai.vmccues.motion.VehicleFrame
import com.zai.vmccues.ui.components.PreviewUtilities

class DotOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    // Cache background‑light result for a short interval to avoid costly sampling each frame.
    @Volatile private var bgLightCache: Boolean = false
    @Volatile private var bgLightCacheTime: Long = 0L // ms

    @Volatile private var settings: CueSettings = CueSettings()
    @Volatile private var dotsVisible: Boolean = false
    @Volatile private var dotOffset: ForceVector = ForceVector.ZERO
    @Volatile private var rawForce: ForceVector = ForceVector.ZERO

    private val isLowEnd: Boolean by lazy { PreviewUtilities.detectLowEnd(context) }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val choreographer = Choreographer.getInstance()

    private val colorSampler = ScreenColorSampler(context)
    @Volatile private var dotSnapshot: DotSnapshot = DotSnapshot.EMPTY

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            invalidate()
            // Continue loop only if still requested
            if (frameRequested) {
                choreographer.postFrameCallback(this)
            }
        }
    }
    @Volatile private var frameRequested = false
    private fun ensureFrameCallback() {
        if (!frameRequested) {
            frameRequested = true
            choreographer.postFrameCallback(frameCallback)
        }
    }
    private fun cancelFrameCallback() {
        if (frameRequested) {
            frameRequested = false
            choreographer.removeFrameCallback(frameCallback)
        }
    }

    private class DotSnapshot(
        val dots: List<PreviewUtilities.DotSpec>,
        val offsets: MutableList<PreviewUtilities.Offset>,
    ) {
        companion object {
            val EMPTY = DotSnapshot(emptyList(), mutableListOf())
        }
    }

    fun setSettings(s: CueSettings) {
        if (s == settings) return
        settings = s
        rebuildLayout()
    }
    fun setDotOffset(offset: ForceVector) { dotOffset = offset }
    fun setDotsVisible(visible: Boolean) {
        if (visible == dotsVisible) return
        dotsVisible = visible
        if (visible) {
            ensureFrameCallback()
        } else {
            cancelFrameCallback()
        }
    }
    fun setRawForce(f: ForceVector) { rawForce = f }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Start frame callbacks only when view is attached and visible
        if (dotsVisible) ensureFrameCallback()
    }
    override fun onDetachedFromWindow() {
        cancelFrameCallback()
        colorSampler.destroy()
        super.onDetachedFromWindow()
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildLayout()
    }

    private fun rebuildLayout() {
        val w = widthPx; val h = heightPx
        if (w <= 0 || h <= 0) return
        val density = resources.displayMetrics.density
        val sideMul = if (isLowEnd) 0.7f else 1f
        val endMul = if (isLowEnd) 0.6f else 1f
        val rawSide = if (settings.moreDots) 10 else 6
        val rawEnd = if (settings.moreDots) 4 else 2
        val sideCount = (rawSide * sideMul).toInt().coerceAtLeast(4)
        val endCount = (rawEnd * endMul).toInt().coerceAtLeast(1)
        val newDots = PreviewUtilities.buildDotLayout(
            width = w.toFloat(),
            height = h.toFloat(),
            sideCount = sideCount,
            endCount = endCount,
            insetPx = settings.dotInsetDp * density,
            centerExclusion = CENTER_EXCLUSION,
            dynamic = settings.pattern == DotPattern.DYNAMIC,
            density = density,
        )
        val newOffsets = mutableListOf<PreviewUtilities.Offset>()
        for (i in newDots.indices) {
            newOffsets.add(PreviewUtilities.Offset(0f, 0f, 0f))
        }
        dotSnapshot = DotSnapshot(newDots, newOffsets)
    }

    private val widthPx: Int get() = width
    private val heightPx: Int get() = height

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val snapshot = dotSnapshot
        val dots = snapshot.dots
        val offsets = snapshot.offsets
        if (dots.isEmpty()) return

        val s = settings
        val density = resources.displayMetrics.density
        val baseRadius = (if (settings.largerDots) LARGER_RADIUS else BASE_RADIUS) * density / 2f
        val ringWidth = 1f * density
        val scrW = widthPx
        val scrH = heightPx
        val maxDotR = baseRadius + ringWidth

        val dxTarget = dotOffset.lateral
        val dyTarget = dotOffset.longitudinal

        val intensity = (VehicleFrame.forceToIntensity(rawForce) * s.intensityResponse)
            .coerceIn(0f, 1f)
        val baseOpacity = (s.dotOpacity + intensity).coerceIn(0.05f, 1f)
        val targetOpacity = if (dotsVisible) baseOpacity else 0f

        val now = SystemClock.elapsedRealtime() / 1000f
        val dotColor = s.dotColor
        val useAdaptive = s.adaptiveContrast

        // Determine background lightness, caching for 200 ms to reduce sampler calls.
        var bgLight = false
        if (useAdaptive && scrW > 0 && scrH > 0) {
            val nowMs = SystemClock.elapsedRealtime()
            if (nowMs - bgLightCacheTime > 200) {
                bgLight = colorSampler.isBackgroundLight(scrW / 2f, scrH / 2f, scrW, scrH)
                bgLightCache = bgLight
                bgLightCacheTime = nowMs
            } else {
                bgLight = bgLightCache
            }
        }

        val ringColor: Int
        val fillColor: Int
        if (useAdaptive) {
            // Apple behavior: adjust saturation of user's color to maintain contrast.
            fillColor = PreviewUtilities.adjustSaturationForContrast(dotColor, bgLight)
            ringColor = if (s.autoContrast) {
                if (PreviewUtilities.isLightColor(fillColor)) COLOR_DARK else COLOR_LIGHT
            } else 0
        } else if (s.autoContrast) {
            ringColor = if (PreviewUtilities.isLightColor(dotColor)) COLOR_DARK else COLOR_LIGHT
            fillColor = dotColor
        } else {
            ringColor = 0
            fillColor = dotColor
        }

        val ringAlphaInt = (targetOpacity * RING_OPACITY * 255).toInt()
        val fillAlphaInt = (targetOpacity * 255).toInt()
        val ringArgb = (ringAlphaInt shl 24) or (ringColor and 0x00FFFFFF)
        val fillArgb = (fillAlphaInt shl 24) or (fillColor and 0x00FFFFFF)

        ringPaint.color = ringArgb
        dotPaint.color = fillArgb

        for (i in dots.indices) {
            val d = dots[i]
            val c = offsets[i]
            var tx = 0f; var ty = 0f
            if (d.axis == PreviewUtilities.Axis.HORIZONTAL) tx = dxTarget else ty = dyTarget
            if (s.pattern == DotPattern.DYNAMIC) {
                val wob = WOBBLE_AMPLITUDE * kotlin.math.sin((now * WOBBLE_FREQ + d.phase).toDouble()).toFloat()
                if (d.axis == PreviewUtilities.Axis.HORIZONTAL) tx += wob else ty += wob
            }
            c.x += (tx - c.x) * VISUAL_LERP
            c.y += (ty - c.y) * VISUAL_LERP
            c.opacity += (targetOpacity - c.opacity) * OPACITY_LERP

            var cx = d.restX + c.x
            var cy = d.restY + c.y
            val r = baseRadius * d.sizeMul
            val alpha = c.opacity.coerceIn(0f, 1f)
            if (alpha < 0.01f) continue

            cx = cx.coerceIn(maxDotR, scrW - maxDotR)
            cy = cy.coerceIn(maxDotR, scrH - maxDotR)

            if (ringColor != 0) {
                val a = (alpha * 255).toInt()
                ringPaint.color = (ringAlphaInt * a / 255 shl 24) or (ringColor and 0x00FFFFFF)
                canvas.drawCircle(cx, cy, r + ringWidth, ringPaint)
                dotPaint.color = (a shl 24) or (fillColor and 0x00FFFFFF)
            } else {
                val a = (alpha * 255).toInt()
                dotPaint.color = (a shl 24) or (fillColor and 0x00FFFFFF)
            }
            canvas.drawCircle(cx, cy, r, dotPaint)
        }
    }

    companion object {
        private const val BASE_RADIUS = 6.5f
        private const val LARGER_RADIUS = 11f
        private const val CENTER_EXCLUSION = 0.35f
        private const val VISUAL_LERP = 0.35f
        private const val OPACITY_LERP = 0.25f
        private const val RING_OPACITY = 0.40f
        private const val WOBBLE_AMPLITUDE = 1.5f
        private const val WOBBLE_FREQ = 1.3f
        private val COLOR_LIGHT = Color.WHITE
        private val COLOR_DARK = Color.BLACK
    }
}
