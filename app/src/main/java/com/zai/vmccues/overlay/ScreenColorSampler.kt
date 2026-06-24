package com.zai.vmccues.overlay

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.Log
import kotlin.math.min

/**
 * Samples the device wallpaper to determine the average brightness behind
 * each dot's position. Used by [DotOverlayView] for adaptive contrast —
 * dots choose light or dark colors based on what's actually behind them,
 * matching iOS's compositor-level adaptive rendering.
 *
 * The wallpaper bitmap is cached and re-sampled once per second to avoid
 * hammering WallpaperManager on every frame.
 */
class ScreenColorSampler(context: Context) {

    companion object {
        private const val TAG = "ScreenColorSampler"
        private const val SAMPLE_INTERVAL_MS = 1000L
        private const val SAMPLE_SIZE = 64
    }

    private val wallpaperManager = WallpaperManager.getInstance(context)

    @Volatile private var wallpaperBitmap: Bitmap? = null
    @Volatile private var lastSampleTime: Long = 0L

    /**
     * Returns the average brightness (0.0 = dark, 1.0 = light) of the
     * wallpaper region behind the given screen coordinates [screenX], [screenY].
     *
     * [screenWidth], [screenHeight] are the full screen dimensions in pixels.
     * The result is cached for [SAMPLE_INTERVAL_MS] to avoid per-frame work.
     */
    fun getBackgroundBrightness(
        screenX: Float,
        screenY: Float,
        screenWidth: Int,
        screenHeight: Int,
    ): Float {
        val bitmap = getWallpaperBitmap() ?: return 0.5f
        val bmpW = bitmap.width
        val bmpH = bitmap.height
        if (bmpW == 0 || bmpH == 0) return 0.5f

        // Map screen coordinates to wallpaper coordinates.
        val wpX = ((screenX / screenWidth) * bmpW).toInt().coerceIn(0, bmpW - 1)
        val wpY = ((screenY / screenHeight) * bmpH).toInt().coerceIn(0, bmpH - 1)

        // Sample a small region around the point.
        val half = SAMPLE_SIZE / 2
        val x0 = (wpX - half).coerceIn(0, bmpW - 1)
        val y0 = (wpY - half).coerceIn(0, bmpH - 1)
        val x1 = (wpX + half).coerceIn(0, bmpW - 1)
        val y1 = (wpY + half).coerceIn(0, bmpH - 1)

        if (x1 <= x0 || y1 <= y0) return 0.5f

        var totalLuminance = 0L
        var count = 0
        val pixels = IntArray((x1 - x0) * (y1 - y0))
        bitmap.getPixels(pixels, 0, x1 - x0, x0, y0, x1 - x0, y1 - y0)

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            // ITU-R BT.601 luma.
            totalLuminance += (0.299 * r + 0.587 * g + 0.114 * b).toLong()
            count++
        }

        return if (count > 0) (totalLuminance.toFloat() / count / 255f).coerceIn(0f, 1f) else 0.5f
    }

    /**
     * Returns true if the wallpaper background behind [screenX], [screenY]
     * is considered "light" (brightness > 0.45), meaning the dot should be
     * dark with a dark contrast ring.
     */
    fun isBackgroundLight(
        screenX: Float,
        screenY: Float,
        screenWidth: Int,
        screenHeight: Int,
    ): Boolean = getBackgroundBrightness(screenX, screenY, screenWidth, screenHeight) > 0.45f

    private fun getWallpaperBitmap(): Bitmap? {
        val now = SystemClock.elapsedRealtime()
        if (wallpaperBitmap != null && now - lastSampleTime < SAMPLE_INTERVAL_MS) {
            return wallpaperBitmap
        }
        lastSampleTime = now
        return try {
            val drawable: Drawable? = wallpaperManager.drawable
            if (drawable == null) {
                Log.w(TAG, "wallpaper drawable is null")
                return wallpaperBitmap
            }
            val w = min(drawable.intrinsicWidth, 512)
            val h = min(drawable.intrinsicHeight, 512)
            if (w <= 0 || h <= 0) return wallpaperBitmap

            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, w, h)
                drawable.draw(canvas)
                wallpaperBitmap?.recycle()
                wallpaperBitmap = bmp
                bmp
            } catch (e: Exception) {
                bmp.recycle()
                Log.w(TAG, "failed to draw wallpaper", e)
                wallpaperBitmap
            }
        } catch (e: Exception) {
            Log.w(TAG, "failed to sample wallpaper", e)
            wallpaperBitmap
        }
    }

    fun destroy() {
        wallpaperBitmap?.recycle()
        wallpaperBitmap = null
    }
}
