package com.zai.vmccues.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.zai.vmccues.MainActivity
import com.zai.vmccues.R
import com.zai.vmccues.VmcApplication
import com.zai.vmccues.data.ActivationMode
import com.zai.vmccues.data.CueSettings
import com.zai.vmccues.gate.ContextGate
import com.zai.vmccues.motion.MotionPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Layer 3 host (brief Section 6 Layer 3 / Section 5.3 / 5.4).
 *
 * A foreground Service that:
 *   - owns the live [MotionPipeline] + [ContextGate],
 *   - adds the [DotOverlayView] to the [WindowManager] as a
 *     TYPE_APPLICATION_OVERLAY window (touch-passthrough so it never
 *     intercepts taps), and
 *   - keeps itself alive with a persistent notification (required by Android
 *     8+ background-service limits; brief Section 5.4).
 *
 * Started from the settings UI or the Quick Settings tile. While running it
 * observes the user's settings: in ON mode the dots are always visible; in
 * AUTOMATIC they're gated by the context gate; in OFF the service stops
 * itself.
 *
 * The notification includes a "Stop" action so the user can kill the overlay
 * without opening the app.
 */
class OverlayService : Service() {

    private lateinit var pipeline: MotionPipeline
    private lateinit var gate: ContextGate
    private lateinit var windowManager: WindowManager
    @Volatile private var overlayView: DotOverlayView? = null
    @Volatile private var overlayAttached = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var settingsJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as VmcApplication
        pipeline = MotionPipeline(this)
        gate = ContextGate(pipeline, app.activityRecognition, scope)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        Log.i(TAG, "OverlayService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle the "Stop" action from the notification.
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "Stop action received")
            // Flip the setting to OFF so the UI stays in sync, then stop.
            val app = applicationContext as VmcApplication
            scope.launch { app.settings.setMode(ActivationMode.OFF) }
            stopSelfClean()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        // API 34+ requires the foregroundServiceType to be passed to
        // startForeground explicitly (and to match the manifest-declared type).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        ensureOverlay()
        observeSettings()
        return START_STICKY
    }

    override fun onDestroy() {
        settingsJob?.cancel()
        gate.stop()
        pipeline.stop()
        pipeline.destroy()
        removeOverlay()
        scope.cancel()
        Log.i(TAG, "OverlayService destroyed")
        super.onDestroy()
    }

    // --- overlay window management ----------------------------------------

    private fun ensureOverlay() {
        if (overlayAttached) return
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW not granted; overlay will not be shown")
            return
        }
        val view = DotOverlayView(this)
        overlayView = view
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = 1f
        }
        try {
            windowManager.addView(view, params)
            overlayAttached = true
            Log.i(TAG, "overlay view attached")
        } catch (e: Exception) {
            Log.e(TAG, "addView failed", e)
        }
    }

    private fun removeOverlay() {
        overlayView?.let { v ->
            if (overlayAttached) {
                try { windowManager.removeView(v) } catch (e: Exception) {
                    Log.w(TAG, "removeView failed", e)
                }
            }
        }
        overlayView = null
        overlayAttached = false
    }

    // --- settings + gate observation --------------------------------------

    private fun observeSettings() {
        val app = applicationContext as VmcApplication
        settingsJob?.cancel()
        settingsJob = scope.launch {
            // React to settings changes.
            launch {
                app.settings.settings.collectLatest { s ->
                    // OFF -> shut everything down.
                    if (s.mode == ActivationMode.OFF) {
                        stopSelfClean()
                        return@collectLatest
                    }
                    overlayView?.setSettings(s)
                    // Push configurable gate delays.
                    gate.entryConfirmMs = s.gateEntryDelayMs
                    gate.exitGraceMs = s.gateExitGraceMs
                    // Restart pipeline with new integrator params if running.
                    if (pipeline.available) {
                        pipeline.start(
                            s.filterAlpha, s.dampingCoef, s.returnToCenterCoef,
                            s.inputClamp, s.deadzone, s.sensitivity,
                        )
                    }
                    // In ON mode, force the gate to "in vehicle"; in AUTOMATIC,
                    // clear the override and let the gate decide.
                    gate.force(if (s.mode == ActivationMode.ON) true else null)
                    // Update the notification text to reflect the current mode.
                    notificationManager()?.notify(NOTIF_ID, buildNotification(s))
                }
            }
            // Drive the pipeline lifecycle from the gate's in-vehicle signal.
            // Only react to the boolean transition (not every confidence tick)
            // so we don't re-register sensor listeners every 500ms.
            launch {
                gate.status
                    .map { it.inVehicle }
                    .distinctUntilChanged()
                    .collectLatest { inVehicle ->
                        if (inVehicle) {
                            val s = app.settings.settings.value
                            pipeline.start(
                                s.filterAlpha, s.dampingCoef, s.returnToCenterCoef,
                                s.inputClamp, s.deadzone, s.sensitivity,
                            )
                            Log.i(TAG, "pipeline started (in-vehicle=true)")
                        } else {
                            pipeline.stop()
                            Log.i(TAG, "pipeline stopped (in-vehicle=false)")
                        }
                    }
            }
            // Pipe dot offset + raw force + visibility into the view.
            launch {
                combine(pipeline.dotOffset, pipeline.rawForce, gate.status) { offset, raw, g -> Triple(offset, raw, g) }
                    .collectLatest { (offset, raw, g) ->
                        overlayView?.setDotOffset(offset)
                        overlayView?.setRawForce(raw)
                        overlayView?.setDotsVisible(g.inVehicle)
                    }
            }
            // Log gate state transitions for debugging.
            launch {
                gate.status
                    .map { it.state }
                    .distinctUntilChanged()
                    .collectLatest { state ->
                        Log.i(TAG, "gate state -> $state")
                    }
            }
        }
        gate.start()
    }

    private fun stopSelfClean() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notificationManager(): NotificationManager? =
        getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    // --- notification ------------------------------------------------------

    private fun buildNotification(settings: CueSettings? = null): Notification {
        val nm = notificationManager()
        if (nm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW).apply {
                    description = getString(R.string.notif_channel_desc)
                    setShowBadge(false)
                }
            )
        }
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // "Stop" action — lets the user kill the overlay from the notification.
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val modeText = when (settings?.mode) {
            ActivationMode.ON -> "Dots always visible"
            ActivationMode.AUTOMATIC -> "Waiting for vehicle context"
            ActivationMode.OFF, null -> getString(R.string.notif_text)
        }
        val detailText = buildString {
            append(modeText)
            if (settings?.adaptiveContrast == true) append(" · Adaptive contrast")
            if (settings?.pattern == com.zai.vmccues.data.DotPattern.DYNAMIC) append(" · Dynamic pattern")
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(detailText)
            .setSmallIcon(R.drawable.ic_tile)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_tile, getString(R.string.toggle_stop), stopIntent)
            .build()
    }

    companion object {
        private const val TAG = "OverlayService"
        private const val CHANNEL_ID = "vmc_active"
        private const val NOTIF_ID = 1
        private const val ACTION_STOP = "com.zai.vmccues.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
