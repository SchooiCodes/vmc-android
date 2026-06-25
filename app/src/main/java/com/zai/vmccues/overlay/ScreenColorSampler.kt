package com.zai.vmccues.overlay

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlin.math.min

class ScreenColorSampler(context: Context) {

    companion object {
        private const val TAG = "ScreenColorSampler"
        private const val SAMPLE_SIZE = 8
        private const val SAMPLE_INTERVAL_MS = 1000L
    }

    private val wallpaperManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        try {
            context.getSystemService(Context.WALLPAPER_SERVICE) as? WallpaperManager
                ?: WallpaperManager.getInstance(context)
        } catch (_: Exception) { null }
    } else {
        try { WallpaperManager.getInstance(context) } catch (_: Exception) { null }
    }

    private val appContext = context.applicationContext
    private var reusePixels = IntArray(0)

    @Volatile private var wallpaperBitmap: Bitmap? = null
    @Volatile private var lastSampleTime: Long = 0L
    @Volatile private var cachedBrightness: Float = 0.5f
    @Volatile private var lastBrightnessTime: Long = 0L

    fun getBackgroundBrightness(
        screenX: Float,
        screenY: Float,
        screenWidth: Int,
        screenHeight: Int,
    ): Float {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBrightnessTime < SAMPLE_INTERVAL_MS) return cachedBrightness
        lastBrightnessTime = now
        if (wallpaperManager == null) {
            cachedBrightness = fallbackBrightness()
            return cachedBrightness
        }
        val bitmap = getWallpaperBitmap()
        if (bitmap == null) {
            cachedBrightness = fallbackBrightness()
            return cachedBrightness
        }
        val bmpW = bitmap.width
        val bmpH = bitmap.height
        if (bmpW == 0 || bmpH == 0) {
            cachedBrightness = fallbackBrightness()
            return cachedBrightness
        }

        val wpX = ((screenX / screenWidth) * bmpW).toInt().coerceIn(0, bmpW - 1)
        val wpY = ((screenY / screenHeight) * bmpH).toInt().coerceIn(0, bmpH - 1)
        val half = SAMPLE_SIZE / 2
        val x0 = (wpX - half).coerceIn(0, bmpW - 1)
        val y0 = (wpY - half).coerceIn(0, bmpH - 1)
        val x1 = (wpX + half).coerceIn(0, bmpW - 1)
        val y1 = (wpY + half).coerceIn(0, bmpH - 1)
        if (x1 <= x0 || y1 <= y0) {
            cachedBrightness = fallbackBrightness()
            return cachedBrightness
        }

        val stride = x1 - x0
        val height = y1 - y0
        val total = stride * height
        if (reusePixels.size < total) reusePixels = IntArray(total)
        try {
            bitmap.getPixels(reusePixels, 0, stride, x0, y0, stride, height)
        } catch (_: Exception) {
            cachedBrightness = fallbackBrightness()
            return cachedBrightness
        }

        var totalLuminance = 0L
        for (i in 0 until total) {
            val pixel = reusePixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            totalLuminance += (0.299 * r + 0.587 * g + 0.114 * b).toLong()
        }
        cachedBrightness = (totalLuminance.toFloat() / total / 255f).coerceIn(0f, 1f)
        return cachedBrightness
    }

    fun isBackgroundLight(
        screenX: Float, screenY: Float,
        screenWidth: Int, screenHeight: Int,
    ): Boolean = getBackgroundBrightness(screenX, screenY, screenWidth, screenHeight) > 0.45f

    private var darkModeChecked = false
    private var isDarkMode = false

    private fun fallbackBrightness(): Float {
        if (!darkModeChecked) {
            val mode = appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            isDarkMode = mode == Configuration.UI_MODE_NIGHT_YES
            darkModeChecked = true
        }
        return if (isDarkMode) 0.1f else 0.9f
    }

    @SuppressLint("MissingPermission")
    private fun getWallpaperBitmap(): Bitmap? {
        if (wallpaperManager == null) return null
        val now = SystemClock.elapsedRealtime()
        if (wallpaperBitmap != null && now - lastSampleTime < SAMPLE_INTERVAL_MS) {
            return wallpaperBitmap
        }
        lastSampleTime = now
        return try {
            val drawable: Drawable? = wallpaperManager.peekDrawable()
            if (drawable == null) {
                Log.w(TAG, "wallpaper drawable is null")
                return wallpaperBitmap
            }
            val w = min(drawable.intrinsicWidth, 512).coerceAtLeast(1)
            val h = min(drawable.intrinsicHeight, 512).coerceAtLeast(1)
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
