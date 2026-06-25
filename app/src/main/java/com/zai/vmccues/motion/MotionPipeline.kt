package com.zai.vmccues.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.zai.vmccues.ui.components.PreviewUtilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Layer 2 — the motion pipeline (spec Part B.2/B.3).
 *
 * Owns the SensorManager listeners for [Sensor.TYPE_LINEAR_ACCELERATION]
 * and [Sensor.TYPE_GAME_ROTATION_VECTOR]. Transforms device-frame
 * acceleration into the vehicle frame, then feeds it through a
 * [DeadReckoningIntegrator] (spec Part B.3) which integrates acceleration →
 * velocity → position with critical damping, producing the negated position
 * that drives dot displacement.
 *
 * Low-end optimization: uses SENSOR_DELAY_UI (~50ms) on low-end devices
 * instead of SENSOR_DELAY_GAME (~20ms) to reduce CPU usage.
 *
 * Emits the final dot displacement (in pixels) via [dotOffset].
 */
class MotionPipeline(context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "MotionPipeline"
        private const val GRAVITY_ALPHA = 0.8f
        /** In "On" mode, use a lower deadzone so dots respond to walking-level forces. */
        const val ON_MODE_DEADZONE = 0.05f
    }

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val linearAccel: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val rawAccel: Sensor? =
        if (linearAccel == null) sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) else null
    private val rotation: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val gravityEstimate = FloatArray(3)
    private val integrator = DeadReckoningIntegrator()

    // Dedicated HandlerThread for sensor delivery — avoids blocking the
    // calling thread and ensures sensor callbacks don't run on the main Looper.
    private val sensorThread = HandlerThread("MotionPipeline").apply { start() }
    private val sensorHandler = Handler(sensorThread.looper)

    private val isLowEnd: Boolean by lazy { PreviewUtilities.detectLowEnd(context) }
    private val sensorDelay: Int
        get() = if (isLowEnd) SensorManager.SENSOR_DELAY_UI else SensorManager.SENSOR_DELAY_GAME

    /** Latest dot displacement in pixels (negated position from the integrator). */
    private val _dotOffset = MutableStateFlow(ForceVector.ZERO)
    val dotOffset: StateFlow<ForceVector> = _dotOffset.asStateFlow()

    /** Latest raw vehicle-frame force (pre-integration), for diagnostics. */
    private val _rawForce = MutableStateFlow(ForceVector.ZERO)
    val rawForce: StateFlow<ForceVector> = _rawForce.asStateFlow()

    /** Latest filtered vehicle-frame force (post-deadzone, pre-integration). */
    private val _filteredForce = MutableStateFlow(ForceVector.ZERO)
    val filteredForce: StateFlow<ForceVector> = _filteredForce.asStateFlow()

    /** Raw accelerometer values (device frame). */
    private val _rawAccelValues = MutableStateFlow(Triple(0f, 0f, 0f))
    val rawAccelValues: StateFlow<Triple<Float, Float, Float>> = _rawAccelValues.asStateFlow()

    /** Dot offset in pixels (for diagnostics). */
    private val _dotOffsetPx = MutableStateFlow(ForceVector.ZERO)
    val dotOffsetPx: StateFlow<ForceVector> = _dotOffsetPx.asStateFlow()

    @Volatile var lastSampleUptimeMs: Long = 0L
        private set
    @Volatile var available: Boolean = false
        private set
    @Volatile private var running: Boolean = false

    private val latestRotation = FloatArray(5)
    private var lastTickMs: Long = 0L

    @Volatile private var deadzone: Float = 0.25f
    @Volatile private var sensitivity: Float = 1.2f

    @Synchronized
    fun start(
        filterAlpha: Float,
        dampingCoef: Float,
        returnToCenterCoef: Float,
        inputClamp: Float,
        deadzone: Float,
        sensitivity: Float,
    ) {
        // Prevent double registration — unregister first if already running.
        if (running) stop()
        integrator.setParams(filterAlpha, dampingCoef, returnToCenterCoef, inputClamp)
        this.deadzone = deadzone
        this.sensitivity = sensitivity
        val la = linearAccel
        val ra = rawAccel
        val rot = rotation
        if (rot == null || (la == null && ra == null)) {
            available = false
            Log.w(TAG, "sensors not available")
            return
        }
        available = true
        running = true
        if (la != null) sensorManager.registerListener(this, la, sensorDelay, sensorHandler)
        else if (ra != null) {
            sensorManager.registerListener(this, ra, sensorDelay, sensorHandler)
            Log.i(TAG, "using TYPE_ACCELEROMETER fallback")
        }
        sensorManager.registerListener(this, rot, sensorDelay, sensorHandler)
        lastTickMs = SystemClock.elapsedRealtime()
        Log.i(TAG, "pipeline started (sensorDelay=$sensorDelay, lowEnd=$isLowEnd)")
    }

    @Synchronized
    fun stop() {
        running = false
        sensorManager.unregisterListener(this)
        integrator.reset()
        _dotOffset.value = ForceVector.ZERO
        _rawForce.value = ForceVector.ZERO
        _filteredForce.value = ForceVector.ZERO
        Log.i(TAG, "pipeline stopped")
    }

    /**
     * Inject a simulated force (for the Force Test screen).
     * Bypasses sensors and processes the force directly through the integrator.
     */
    @Synchronized
    fun injectSimulatedForce(lateral: Float, longitudinal: Float) {
        if (!running) return
        val now = SystemClock.elapsedRealtime()
        val dt = if (lastTickMs > 0) Math.min(0.1f, (now - lastTickMs) / 1000f) else 1f / 60f
        lastTickMs = now

        val vehicle = ForceVector(lateral, longitudinal)
        val newRaw = vehicle
        val rawEpsilon = 0.001f
        if (kotlin.math.abs(newRaw.lateral - _rawForce.value.lateral) > rawEpsilon ||
            kotlin.math.abs(newRaw.longitudinal - _rawForce.value.longitudinal) > rawEpsilon) {
            _rawForce.value = newRaw
        }

        val dampedLat = VehicleFrame.smoothDeadzone(vehicle.lateral, deadzone)
        val dampedLon = VehicleFrame.smoothDeadzone(vehicle.longitudinal, deadzone)
        val damped = ForceVector(dampedLat, dampedLon)
        _filteredForce.value = damped

        val offset = integrator.update(damped, dt)
        val newOffset = ForceVector(
            lateral = offset.lateral * sensitivity,
            longitudinal = offset.longitudinal * sensitivity,
        )
        val epsilon = 0.01f
        if (kotlin.math.abs(newOffset.lateral - _dotOffset.value.lateral) > epsilon ||
            kotlin.math.abs(newOffset.longitudinal - _dotOffset.value.longitudinal) > epsilon) {
            _dotOffset.value = newOffset
        }
        _dotOffsetPx.value = newOffset
        lastSampleUptimeMs = now
    }

    fun destroy() {
        sensorThread.quitSafely()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR -> {
                val n = event.values.size.coerceAtMost(latestRotation.size)
                System.arraycopy(event.values, 0, latestRotation, 0, n)
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                processAcceleration(event.values[0], event.values[1], event.values[2])
            }
            Sensor.TYPE_ACCELEROMETER -> {
                gravityEstimate[0] = GRAVITY_ALPHA * gravityEstimate[0] + (1 - GRAVITY_ALPHA) * event.values[0]
                gravityEstimate[1] = GRAVITY_ALPHA * gravityEstimate[1] + (1 - GRAVITY_ALPHA) * event.values[1]
                gravityEstimate[2] = GRAVITY_ALPHA * gravityEstimate[2] + (1 - GRAVITY_ALPHA) * event.values[2]
                processAcceleration(
                    event.values[0] - gravityEstimate[0],
                    event.values[1] - gravityEstimate[1],
                    event.values[2] - gravityEstimate[2],
                )
            }
        }
    }

    private fun processAcceleration(ax: Float, ay: Float, az: Float) {
        val now = SystemClock.elapsedRealtime()
        val dt = if (lastTickMs > 0) Math.min(0.1f, (now - lastTickMs) / 1000f) else 1f / 60f
        lastTickMs = now

        _rawAccelValues.value = Triple(ax, ay, az)

        val vehicle = VehicleFrame.transform(latestRotation, ax, ay, az)
        // Emit raw force only if significant change occurs.
        val newRaw = vehicle
        val rawEpsilon = 0.001f
        if (kotlin.math.abs(newRaw.lateral - _rawForce.value.lateral) > rawEpsilon ||
            kotlin.math.abs(newRaw.longitudinal - _rawForce.value.longitudinal) > rawEpsilon) {
            _rawForce.value = newRaw
        }

        val dampedLat = VehicleFrame.smoothDeadzone(vehicle.lateral, deadzone)
        val dampedLon = VehicleFrame.smoothDeadzone(vehicle.longitudinal, deadzone)
        val damped = ForceVector(dampedLat, dampedLon)
        _filteredForce.value = damped

        val offset = integrator.update(damped, dt)
        // Emit only if displacement changed beyond a small epsilon to avoid redundant UI updates.
        val newOffset = ForceVector(
            lateral = offset.lateral * sensitivity,
            longitudinal = offset.longitudinal * sensitivity,
        )
        val epsilon = 0.01f // pixels
        if (kotlin.math.abs(newOffset.lateral - _dotOffset.value.lateral) > epsilon ||
            kotlin.math.abs(newOffset.longitudinal - _dotOffset.value.longitudinal) > epsilon) {
            _dotOffset.value = newOffset
        }
        _dotOffsetPx.value = newOffset
        lastSampleUptimeMs = now
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
