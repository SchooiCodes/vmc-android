package com.zai.vmccues.gate

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wraps the Google Play Services Activity Recognition API (brief Section 5.1).
 *
 * We subscribe for periodic IN_VEHICLE detections and expose the latest
 * result as a StateFlow. The [ContextGate] consumes it.
 *
 * IMPORTANT (brief caveat): Google's ActivityRecognition accuracy on
 * motorized-vehicle detection is mediocre out of the box — we treat this as
 * a COARSE GATE ("probably in a vehicle, turn on the higher-power IMU
 * pipeline") rather than ground truth. The gate also corroborates with the
 * motion-signal statistics before fully engaging.
 */
class ActivityRecognitionProvider(private val appContext: Context) {

    private val client: ActivityRecognitionClient =
        ActivityRecognition.getClient(appContext)

    private val _latest = MutableStateFlow(ActivityResult(false, 0, System.currentTimeMillis()))
    val latest: StateFlow<ActivityResult> = _latest.asStateFlow()

    @Volatile private var subscribed = false

    /** True only if the runtime permission is granted. */
    fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Pre-API-29: permission is implicit, granted at install time.
            true
        }
    }

    /**
     * Begin receiving activity updates. Safe to call repeatedly; subsequent
     * calls are no-ops while already subscribed.
     */
    @SuppressLint("MissingPermission")
    fun start() {
        if (subscribed) return
        if (!hasPermission()) {
            Log.w(TAG, "start() called without ACTIVITY_RECOGNITION permission; no-op")
            return
        }
        val pi = makePendingIntent()
        // ~20s detection interval — battery-friendly coarse gate.
        val task = try {
            client.requestActivityUpdates(DETECTION_INTERVAL_MS, pi)
        } catch (e: SecurityException) {
            Log.w(TAG, "ACTIVITY_RECOGNITION permission denied; no-op", e)
            return
        }
        task.addOnSuccessListener {
            subscribed = true
            Log.i(TAG, "subscribed to activity updates")
        }
        task.addOnFailureListener { e ->
            Log.e(TAG, "failed to subscribe to activity updates", e)
        }
    }

    fun stop() {
        if (!subscribed) return
        val pi = makePendingIntent()
        try {
            client.removeActivityUpdates(pi)
                .addOnSuccessListener {
                    subscribed = false
                    Log.i(TAG, "unsubscribed from activity updates")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "failed to unsubscribe from activity updates", e)
                }
        } catch (e: SecurityException) {
            Log.w(TAG, "ACTIVITY_RECOGNITION permission denied during unsubscribe; ignoring", e)
        }
    }

    private fun makePendingIntent(): PendingIntent {
        val intent = Intent(appContext, ActivityRecognitionReceiver::class.java).apply {
            action = ACTION_ACTIVITY_UPDATE
        }
        // FLAG_IMMUTABLE is required on API 31+; FLAG_UPDATE_CURRENT keeps the
        // singleton PendingIntent across re-subscribes.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(appContext, REQUEST_CODE, intent, flags)
    }

    /** Called by [ActivityRecognitionReceiver] when a new batch arrives. */
    internal fun onBroadcast(intent: Intent) {
        if (!ActivityRecognitionResult.hasResult(intent)) return
        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        // The Play Services API is getProbableActivities() → Kotlin property
        // `probableActivities`. Returns a List<DetectedActivity> sorted by
        // descending confidence.
        val activities = result.probableActivities
        val inVehicle = activities.any {
            it.type == DetectedActivity.IN_VEHICLE && it.confidence >= CONFIDENCE_THRESHOLD
        }
        val confidence = activities
            .firstOrNull { it.type == DetectedActivity.IN_VEHICLE }
            ?.confidence ?: 0
        _latest.value = ActivityResult(inVehicle, confidence, System.currentTimeMillis())
    }

    /**
     * Immutable snapshot of the latest activity-recognition result.
     * @param inVehicle whether IN_VEHICLE was detected above the confidence threshold
     * @param confidence the raw confidence score (0..100) for IN_VEHICLE, or 0 if absent
     * @param timestampMs wall-clock millis when this result was received
     */
    data class ActivityResult(
        val inVehicle: Boolean,
        val confidence: Int,
        val timestampMs: Long,
    )

    companion object {
        private const val TAG = "ActivityRecog"
        private const val ACTION_ACTIVITY_UPDATE = "com.zai.vmccues.ACTIVITY_UPDATE"
        private const val REQUEST_CODE = 4242
        private const val DETECTION_INTERVAL_MS = 20_000L
        // Brief suggests treating IN_VEHICLE with confidence ≥ ~70 as a
        // candidate; we go a touch lower (60) because we corroborate with the
        // motion-signal statistics before fully engaging.
        private const val CONFIDENCE_THRESHOLD = 60
    }
}

/**
 * Receives the ActivityRecognition broadcast and forwards to the provider
 * stashed on the [com.zai.vmccues.VmcApplication].
 */
class ActivityRecognitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? com.zai.vmccues.VmcApplication ?: return
        app.activityRecognition.onBroadcast(intent)
    }
}
