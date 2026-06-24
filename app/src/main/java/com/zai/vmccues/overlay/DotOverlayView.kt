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
 * Thread-safe: settings/offset/visibility are written from background
 * coroutines and read on the UI thread via onDraw. All shared mutable
 * state is either @Volatile or swapped atomically.
 */
class DotOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    // Thread-safe: written from coroutines, read from UI thread.
    @Volatile private var settings: CueSettings = CueSettings()
    @Volatile private var dotOffset: ForceVector = ForceVector.ZERO
    @Volatile private var dotsVisible: Boolean = false
    @Volatile private var _rawForceForIntensity: ForceVector = ForceVector.ZERO

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // Layout + offsets are swapped atomically — never mutated mid-iteration.
    @Volatile private var dotSnapshot: DotSnapshot = DotSnapshot.EMPTY

    private val colorSampler = ScreenColorSampler(context)
    private val isLowEnd: Boolean by lazy { detectLowEnd() }
    private var targetFrameIntervalMs: Long = if (isLowEnd) 33L else 16L
    private var lastFrameTimeMs: Long = 0L

    private val choreographer = Choreographer.getInstance()
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastFrameTimeMs >= targetFrameIntervalMs) {
                lastFrameTimeMs = now
                invalidate()
            }
            choreographer.postFrameCallback(this)
        }
    }

    /** Thread-safe snapshot of layout + offsets — swapped atomically in onDraw. */
    private class DotSnapshot(
        val dots: List<PreviewUtilities.DotSpec>,
        val offsets: List<PreviewUtilities.Offset>,
    ) {
        companion object {
            val EMPTY = DotSnapshot(emptyList(), emptyList())
        }
    }

    fun setSettings(s: CueSettings) {
        if (s == settings) return
        settings = s
        rebuildLayout()
    }
    fun setDotOffset(offset: ForceVector) { dotOffset = offset }
    fun setDotsVisible(visible: Boolean) { dotsVisible = visible }
    fun setRawForce(f: ForceVector) { _rawForceForIntensity = f }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        choreographer.removeFrameCallback(frameCallback)
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
        rebuildLayout()
    }

    /**
     * Rebuilds the dot layout on the UI thread (via post if called from
     * a worker). Swaps atomically into [dotSnapshot] so onDraw never
     * sees a half-built list.
     */
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
        )
        val newOffsets = List(newDots.size) { PreviewUtilities.Offset(0f, 0f, 0f) }
        dotSnapshot = DotSnapshot(newDots, newOffsets)
    }

    private val widthPx: Int get() = width
    private val heightPx: Int get() = height

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // Atomic snapshot — safe to iterate even if settings change mid-frame.
        val snapshot = dotSnapshot
        val dots = snapshot.dots
        val offsets = snapshot.offsets
        if (dots.isEmpty()) return

        val s = settings
        val density = resources.displayMetrics.density
        val baseRadius = (if (s.largerDots) LARGER_RADIUS else BASE_RADIUS) * density / 2f
        val ringWidth = 1f * density
        val scrW = widthPx
        val scrH = heightPx

        val dxTarget = dotOffset.lateral
        val dyTarget = dotOffset.longitudinal

        val intensity = (VehicleFrame.forceToIntensity(_rawForceForIntensity) * s.intensityResponse)
            .coerceIn(0f, 1f)
        val baseOpacity = (s.dotOpacity + intensity).coerceIn(0.05f, 1f)
        val targetOpacity = if (dotsVisible) baseOpacity else 0f

        val now = SystemClock.elapsedRealtime() / 1000f
        val dotColor = s.dotColor

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

            val cx = d.restX + c.x
            val cy = d.restY + c.y
            val r = baseRadius * d.sizeMul
            val alpha = c.opacity.coerceIn(0f, 1f)
            if (alpha < 0.01f) continue

            val alpha255 = (alpha * 255).toInt()

            if (s.adaptiveContrast) {
                val bgLight = colorSampler.isBackgroundLight(cx, cy, scrW, scrH)
                if (bgLight) {
                    val ringAlpha = (alpha * RING_OPACITY * 255).toInt()
                    ringPaint.color = (ringAlpha shl 24) or (COLOR_DARK and 0x00FFFFFF)
                    canvas.drawCircle(cx, cy, r + ringWidth, ringPaint)
                    dotPaint.color = (alpha255 shl 24) or (COLOR_DARK and 0x00FFFFFF)
                } else {
                    val ringAlpha = (alpha * RING_OPACITY * 255).toInt()
                    ringPaint.color = (ringAlpha shl 24) or (COLOR_LIGHT and 0x00FFFFFF)
                    canvas.drawCircle(cx, cy, r + ringWidth, ringPaint)
                    dotPaint.color = (alpha255 shl 24) or (COLOR_LIGHT and 0x00FFFFFF)
                }
                canvas.drawCircle(cx, cy, r, dotPaint)
            } else if (s.autoContrast) {
                val ringColor = if (isLightColor(dotColor)) COLOR_DARK else COLOR_LIGHT
                val ringAlpha = (alpha * RING_OPACITY * 255).toInt()
                ringPaint.color = (ringAlpha shl 24) or (ringColor and 0x00FFFFFF)
                canvas.drawCircle(cx, cy, r + ringWidth, ringPaint)
                dotPaint.color = (alpha255 shl 24) or (dotColor and 0x00FFFFFF)
                canvas.drawCircle(cx, cy, r, dotPaint)
            } else {
                dotPaint.color = (alpha255 shl 24) or (dotColor and 0x00FFFFFF)
                canvas.drawCircle(cx, cy, r, dotPaint)
            }
        }
    }

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
