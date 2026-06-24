package com.zai.vmccues.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
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
 * Emits the final dot displacement (in pixels) via [dotOffset].
 */
class MotionPipeline(context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "MotionPipeline"
        private const val GRAVITY_ALPHA = 0.8f
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

    /** Latest dot displacement in pixels (negated position from the integrator). */
    private val _dotOffset = MutableStateFlow(ForceVector.ZERO)
    val dotOffset: StateFlow<ForceVector> = _dotOffset.asStateFlow()

    /** Latest raw vehicle-frame force (pre-integration), for diagnostics. */
    private val _rawForce = MutableStateFlow(ForceVector.ZERO)
    val rawForce: StateFlow<ForceVector> = _rawForce.asStateFlow()

    /** Latest filtered vehicle-frame force (post-deadzone, pre-integration). */
    private val _filteredForce = MutableStateFlow(ForceVector.ZERO)
    val filteredForce: StateFlow<ForceVector> = _filteredForce.asStateFlow()

    @Volatile var lastSampleUptimeMs: Long = 0L
        private set
    @Volatile var available: Boolean = false
        private set

    private val latestRotation = FloatArray(5)
    private var lastTickMs: Long = 0L

    // Configurable parameters (pushed from settings).
    @Volatile private var deadzone: Float = 0.3f
    @Volatile private var sensitivity: Float = 1.0f

    fun start(
        filterAlpha: Float,
        dampingCoef: Float,
        returnToCenterCoef: Float,
        inputClamp: Float,
        deadzone: Float,
        sensitivity: Float,
    ) {
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
        if (la != null) sensorManager.registerListener(this, la, SensorManager.SENSOR_DELAY_GAME)
        else if (ra != null) {
            sensorManager.registerListener(this, ra, SensorManager.SENSOR_DELAY_GAME)
            Log.i(TAG, "using TYPE_ACCELEROMETER fallback")
        }
        sensorManager.registerListener(this, rot, SensorManager.SENSOR_DELAY_GAME)
        lastTickMs = SystemClock.elapsedRealtime()
        Log.i(TAG, "pipeline started")
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        integrator.reset()
        _dotOffset.value = ForceVector.ZERO
        _rawForce.value = ForceVector.ZERO
        _filteredForce.value = ForceVector.ZERO
        Log.i(TAG, "pipeline stopped")
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

        // Transform device-frame → vehicle-frame.
        val vehicle = VehicleFrame.transform(latestRotation, ax, ay, az)
        _rawForce.value = vehicle

        // Apply dead-band (spec B.4): zero out forces below the noise floor.
        val dampedLat = if (Math.abs(vehicle.lateral) < deadzone) 0f else vehicle.lateral
        val dampedLon = if (Math.abs(vehicle.longitudinal) < deadzone) 0f else vehicle.longitudinal
        val damped = ForceVector(dampedLat, dampedLon)
        _filteredForce.value = damped

        // Dead-reckoning integration (spec B.3).
        val offset = integrator.update(damped, dt)
        // Apply user sensitivity.
        _dotOffset.value = ForceVector(
            lateral = offset.lateral * sensitivity,
            longitudinal = offset.longitudinal * sensitivity,
        )
        lastSampleUptimeMs = now
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
