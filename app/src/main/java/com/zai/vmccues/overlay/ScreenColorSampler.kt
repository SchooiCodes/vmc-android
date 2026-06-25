package com.zai.vmccues.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.ImageFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager

class ScreenColorSampler(context: Context) {

    companion object {
        private const val TAG = "ScreenColorSampler"
        private const val SAMPLE_INTERVAL_MS = 200L
        private const val CAPTURE_WIDTH = 64
        private const val CAPTURE_HEIGHT = 64
    }

    private val appContext = context.applicationContext
    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    @Volatile private var cachedBrightness: Float = 0.5f
    @Volatile private var lastBrightnessTime: Long = 0L

    private val captureThread = HandlerThread("ScreenColorSampler").apply { start() }
    private val captureHandler = Handler(captureThread.looper)

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val metrics = DisplayMetrics().also { @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(it) }

    private var darkModeChecked = false
    private var isDarkMode = false

    @Volatile private var bgLightCache = false
    @Volatile private var bgLightCacheTime = 0L

    fun setProjection(proj: MediaProjection?) {
        projection = proj
        setupCapture()
    }

    private fun setupCapture() {
        val proj = projection ?: return
        imageReader?.close()
        imageReader = ImageReader.newInstance(
            CAPTURE_WIDTH, CAPTURE_HEIGHT, ImageFormat.RGB_565, 2
        )
        virtualDisplay?.release()
        virtualDisplay = proj.createVirtualDisplay(
            "VMCColorSample",
            CAPTURE_WIDTH, CAPTURE_HEIGHT,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            captureHandler,
        )
        Log.i(TAG, "virtual display created: ${CAPTURE_WIDTH}x${CAPTURE_HEIGHT}")
    }

    fun isBackgroundLight(
        screenX: Float, screenY: Float,
        screenWidth: Int, screenHeight: Int,
    ): Boolean {
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - bgLightCacheTime < 200) return bgLightCache
        val bright = sampleBrightness(screenX, screenY, screenWidth, screenHeight)
        bgLightCache = bright > 0.45f
        bgLightCacheTime = nowMs
        return bgLightCache
    }

    private fun sampleBrightness(
        screenX: Float, screenY: Float,
        screenWidth: Int, screenHeight: Int,
    ): Float {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBrightnessTime < SAMPLE_INTERVAL_MS) return cachedBrightness
        lastBrightnessTime = now

        val reader = imageReader ?: return fallbackBrightness()
        val image = reader.acquireLatestImage() ?: return cachedBrightness
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride

            val capX = ((screenX / screenWidth) * CAPTURE_WIDTH).toInt().coerceIn(0, CAPTURE_WIDTH - 1)
            val capY = ((screenY / screenHeight) * CAPTURE_HEIGHT).toInt().coerceIn(0, CAPTURE_HEIGHT - 1)

            val half = 2
            var totalLuminance = 0L
            var count = 0
            for (dy in -half..half) {
                for (dx in -half..half) {
                    val px = (capX + dx).coerceIn(0, CAPTURE_WIDTH - 1)
                    val py = (capY + dy).coerceIn(0, CAPTURE_HEIGHT - 1)
                    val offset = py * rowStride + px * pixelStride
                    if (offset + 1 < buffer.capacity()) {
                        val rgb565 = buffer.getShort(offset).toInt() and 0xFFFF
                        val r = (rgb565 shr 11) and 0x1F
                        val g = (rgb565 shr 5) and 0x3F
                        val b = rgb565 and 0x1F
                        val r8 = (r * 255) / 31
                        val g8 = (g * 255) / 63
                        val b8 = (b * 255) / 31
                        totalLuminance += (0.299 * r8 + 0.587 * g8 + 0.114 * b8).toLong()
                        count++
                    }
                }
            }
            if (count > 0) {
                cachedBrightness = (totalLuminance.toFloat() / count / 255f).coerceIn(0f, 1f)
            }
            return cachedBrightness
        } catch (e: Exception) {
            Log.w(TAG, "sample error", e)
            return cachedBrightness
        } finally {
            image.close()
        }
    }

    private fun fallbackBrightness(): Float {
        if (!darkModeChecked) {
            val mode = appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            isDarkMode = mode == Configuration.UI_MODE_NIGHT_YES
            darkModeChecked = true
        }
        return if (isDarkMode) 0.1f else 0.9f
    }

    fun destroy() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        projection?.stop()
        projection = null
        captureThread.quitSafely()
    }
}
