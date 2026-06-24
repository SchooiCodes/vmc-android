package com.zai.vmccues.overlay

import android.app.ActivityManager
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

/**
 * Layer 3 renderer — the dot overlay (spec Part B.7).
 *
 * Dots are simple solid circles with a thin contrast ring (matching Apple's
 * actual rendering). Dots live along the screen edges, PRIMARILY the left and
 * right edges (spec Part A.5: "left and right edges primarily").
 *
 * Adaptive contrast: when enabled, dots sample the wallpaper behind them and
 * choose light/dark colors to remain visible on any background — matching
 * iOS's compositor-level adaptive rendering as closely as possible on Android.
 *
 * Low-end device optimization: automatically detects device capability and
 * reduces frame rate and dot count on lower-end hardware.
 */
class DotOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var settings: CueSettings = CueSettings()
    private var dotOffset: ForceVector = ForceVector.ZERO
    private var dotsVisible: Boolean = false

    // Paint objects allocated once, reused every frame (zero alloc in onDraw).
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private var layout: List<PreviewUtilities.DotSpec> = emptyList()
    private val cur: MutableList<PreviewUtilities.Offset> = mutableListOf()
    private var widthPx: Int = 0
    private var heightPx: Int = 0

    // Screen color sampler for adaptive contrast.
    private val colorSampler = ScreenColorSampler(context)

    // Device-tier detection for low-end optimization.
    private val isLowEnd: Boolean by lazy { detectLowEnd() }

    // Adaptive frame rate: low-end gets 30fps, high-end gets 60fps.
    private var targetFrameIntervalMs: Long = if (isLowEnd) 33L else 16L
    private var lastFrameTimeMs: Long = 0L

    // Choreographer for frame-synced rendering.
    private val choreographer = Choreographer.getInstance()
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - lastFrameTimeMs
            if (elapsed >= targetFrameIntervalMs) {
                lastFrameTimeMs = now
                invalidate()
            }
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
        lastFrameTimeMs = SystemClock.elapsedRealtime()
        choreographer.postFrameCallback(frameCallback)
    }
    override fun onDetachedFromWindow() {
        choreographer.removeFrameCallback(frameCallback)
        colorSampler.destroy()
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
        // On low-end devices, reduce dot count by ~30%.
        val sideMul = if (isLowEnd) 0.7f else 1f
        val endMul = if (isLowEnd) 0.6f else 1f
        val rawSide = if (settings.moreDots) 10 else 6
        val rawEnd = if (settings.moreDots) 4 else 2
        val sideCount = (rawSide * sideMul).toInt().coerceAtLeast(4)
        val endCount = (rawEnd * endMul).toInt().coerceAtLeast(1)
        val newLayout = PreviewUtilities.buildDotLayout(
            width = w.toFloat(),
            height = h.toFloat(),
            sideCount = sideCount,
            endCount = endCount,
            insetPx = settings.dotInsetDp * density,
            centerExclusion = CENTER_EXCLUSION,
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
        val scrW = widthPx
        val scrH = heightPx

        val dxTarget = dotOffset.lateral
        val dyTarget = dotOffset.longitudinal

        val intensity = VehicleFrame.forceToIntensity(_rawForceForIntensity)
        val baseOpacity = (s.dotOpacity + intensity * s.intensityResponse).coerceIn(0.05f, 1f)
        val targetOpacity = if (dotsVisible) baseOpacity else 0f

        val now = SystemClock.elapsedRealtime() / 1000f
        val dotColor = s.dotColor

        for (i in dots.indices) {
            val d = dots[i]
            val c = cur[i]
            var tx = 0f; var ty = 0f
            if (d.axis == PreviewUtilities.Axis.HORIZONTAL) tx = dxTarget else ty = dyTarget
            if (s.pattern == DotPattern.DYNAMIC) {
                val wob = WOBBLE_AMPLITUDE * kotlin.math.sin((now * WOBBLE_FREQ + d.phase).toDouble()).toFloat()
                if (d.axis == PreviewUtilities.Axis.HORIZONTAL) tx += wob else ty += wob
            }
            c.x += (tx - c.x) * VISUAL_LERP
            c.y += (ty - c.y) * VISUAL_LERP
            c.opacity += (targetOpacity - c.opacity) * OPACITY_LERP

            val cx = d.restX + c.x
            val cy = d.restY + c.y
            val r = baseRadius * d.sizeMul
            val alpha = c.opacity.coerceIn(0f, 1f)
            if (alpha < 0.01f) continue

            val alpha255 = (alpha * 255).toInt()

            if (s.adaptiveContrast) {
                // Sample wallpaper behind this dot and pick the best contrast.
                val bgLight = colorSampler.isBackgroundLight(cx, cy, scrW, scrH)
                if (bgLight) {
                    // Light background → dark dot with dark ring.
                    val darkColor = COLOR_DARK
                    val ringAlpha = (alpha * RING_OPACITY * 255).toInt()
                    ringPaint.color = (ringAlpha shl 24) or (darkColor and 0x00FFFFFF)
                    canvas.drawCircle(cx, cy, r + ringWidth, ringPaint)
                    dotPaint.color = (alpha255 shl 24) or (darkColor and 0x00FFFFFF)
                } else {
                    // Dark background → light dot with light ring.
                    val lightColor = COLOR_LIGHT
                    val ringAlpha = (alpha * RING_OPACITY * 255).toInt()
                    ringPaint.color = (ringAlpha shl 24) or (lightColor and 0x00FFFFFF)
                    canvas.drawCircle(cx, cy, r + ringWidth, ringPaint)
                    dotPaint.color = (alpha255 shl 24) or (lightColor and 0x00FFFFFF)
                }
                canvas.drawCircle(cx, cy, r, dotPaint)
            } else if (s.autoContrast) {
                // Static auto-contrast: light ring on dark dots, dark ring on light dots.
                val ringColor = if (isLightColor(dotColor)) COLOR_DARK else COLOR_LIGHT
                val ringAlpha = (alpha * RING_OPACITY * 255).toInt()
                ringPaint.color = (ringAlpha shl 24) or (ringColor and 0x00FFFFFF)
                canvas.drawCircle(cx, cy, r + ringWidth, ringPaint)
                dotPaint.color = (alpha255 shl 24) or (dotColor and 0x00FFFFFF)
                canvas.drawCircle(cx, cy, r, dotPaint)
            } else {
                // No contrast ring.
                dotPaint.color = (alpha255 shl 24) or (dotColor and 0x00FFFFFF)
                canvas.drawCircle(cx, cy, r, dotPaint)
            }
        }
    }

    @Volatile private var _rawForceForIntensity: ForceVector = ForceVector.ZERO
    fun setRawForce(f: ForceVector) { _rawForceForIntensity = f }

    private fun isLightColor(color: Int): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0 > 0.5
    }

    private fun detectLowEnd(): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalMemGB = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        val cores = Runtime.getRuntime().availableProcessors()
        val lowEnd = totalMemGB <= 3.0 || cores <= 4
        if (lowEnd) Log.i(TAG, "low-end device detected (${totalMemGB.toFixed(1)}GB, ${cores} cores)")
        return lowEnd
    }

    private fun Double.toFixed(n: Int): String = "%.${n}f".format(this)

    companion object {
        private const val TAG = "DotOverlayView"
        private const val BASE_RADIUS = 6.5f
        private const val LARGER_RADIUS = 11f
        private const val CENTER_EXCLUSION = 0.35f
        private const val VISUAL_LERP = 0.14f
        private const val OPACITY_LERP = 0.12f
        private const val RING_OPACITY = 0.40f
        private const val WOBBLE_AMPLITUDE = 1.5f
        private const val WOBBLE_FREQ = 1.3f
        private val COLOR_LIGHT = Color.WHITE
        private val COLOR_DARK = Color.BLACK
    }
}
