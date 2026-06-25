package com.zai.vmccues.motion

import android.hardware.SensorManager

/**
 * Projects a device-frame linear-acceleration sample onto the vehicle's
 * reference frame. This is the "hard part" called out in brief Section 4
 * step 3: because the phone can be held at any angle, we must transform raw
 * sensor readings (device frame) into an estimate of the vehicle's frame
 * (forward/back, left/right).
 *
 * Approach (the clean Android equivalent of iOS's CMDeviceMotion
 * userAcceleration + attitude fusion):
 *
 *  1. Build the device→world rotation matrix from the (game) rotation vector
 *     using [SensorManager.getRotationMatrixFromVector].
 *  2. Rotate the device-frame linear acceleration into the world frame.
 *  3. In the world frame, "up" is the +Z axis. Remove the (already tiny, but
 *     not exactly zero) vertical component so we're left with horizontal
 *     acceleration only.
 *  4. Recover the phone's "forward" direction (the top of the phone, which is
 *     -Y in device space) and "right" direction (+X in device space) in the
 *     world frame, project both onto the horizontal plane, and normalize.
 *     These two horizontal vectors ARE the vehicle's forward/right when the
 *     user is seated facing forward and holding the phone upright to read —
 *     the documented best-case orientation (brief Section 3.6).
 *  5. Project the horizontal world-frame acceleration onto vehicle-forward
 *     (→ longitudinal) and vehicle-right (→ lateral).
 *
 * Sign convention:
 *   +lateral      = body pushed right (vehicle turning left)
 *   +longitudinal = accelerating forward (body pressed back into seat)
 *
 * Output is in m/s^2. This is approximate by design — Apple presumably uses
 * a full earth-frame + magnetometer-heading transform; for a passenger
 * holding a phone the approximation feels direction-correct, which is what
 * matters for the cue.
 */
object VehicleFrame {

    /** Scratch buffers reused across calls to avoid per-frame allocation. */
    private val r = FloatArray(9)
    private val accDevice = FloatArray(3)
    private val accWorld = FloatArray(3)
    private val forwardDevice = floatArrayOf(0f, -1f, 0f)
    private val rightDevice = floatArrayOf(1f, 0f, 0f)

    // Device-space basis vectors we care about, rotated into world frame.
    private val forwardWorld = FloatArray(3)
    private val rightWorld = FloatArray(3)
    private val forwardHoriz = FloatArray(3)
    private val rightHoriz = FloatArray(3)

    /**
     * @param rotationValues the `SensorEvent.values` array from
     *   TYPE_GAME_ROTATION_VECTOR (or TYPE_ROTATION_VECTOR).
     * @param ax,ay,az device-frame linear acceleration (gravity already
     *   removed) — i.e. TYPE_LINEAR_ACCELERATION values.
     * @return the force expressed in the vehicle frame (m/s^2).
     */
    fun transform(rotationValues: FloatArray, ax: Float, ay: Float, az: Float): ForceVector {
        // 1. device -> world rotation matrix.
        SensorManager.getRotationMatrixFromVector(r, rotationValues)

        // 2. Rotate device-frame acceleration into world frame: a_world = R * a_device.
        accDevice[0] = ax; accDevice[1] = ay; accDevice[2] = az
        multiplyMV3(r, accDevice, accWorld)

        // 3. Drop the world-vertical (Z) component — vertical acceleration
        //    (e.g. bumps) carries no useful cornering/braking signal.
        accWorld[2] = 0f

        // 4. Recover phone "forward" (-Y device) and "right" (+X device) in
        //    the world frame.
        multiplyMV3(r, forwardDevice, forwardWorld)
        multiplyMV3(r, rightDevice, rightWorld)

        // Project both onto the horizontal plane (zero out world-Z) + normalize.
        forwardHoriz[0] = forwardWorld[0]; forwardHoriz[1] = forwardWorld[1]; forwardHoriz[2] = 0f
        rightHoriz[0] = rightWorld[0];   rightHoriz[1] = rightWorld[1];   rightHoriz[2] = 0f
        val fLen = kotlin.math.hypot(forwardHoriz[0].toDouble(), forwardHoriz[1].toDouble()).toFloat()
        val rLen = kotlin.math.hypot(rightHoriz[0].toDouble(), rightHoriz[1].toDouble()).toFloat()
        // Guard against divide-by-zero if the phone is held flat (face up).
        // In that degenerate orientation forward is undefined; we return zero
        // rather than garbage.
        if (fLen < 1e-3f || rLen < 1e-3f) return ForceVector.ZERO
        forwardHoriz[0] /= fLen; forwardHoriz[1] /= fLen
        rightHoriz[0] /= rLen;   rightHoriz[1] /= rLen

        // 5. Project horizontal world acceleration onto vehicle forward/right.
        val longitudinal = dot3(accWorld, forwardHoriz)
        val lateral = dot3(accWorld, rightHoriz)
        return ForceVector(lateral = lateral, longitudinal = longitudinal)
    }

    /** out = R (3x3, row-major) * v (length 3). Result written into `out`. */
    private fun multiplyMV3(m: FloatArray, v: FloatArray, out: FloatArray) {
        val x = v[0]; val y = v[1]; val z = v[2]
        out[0] = m[0] * x + m[1] * y + m[2] * z
        out[1] = m[3] * x + m[4] * y + m[5] * z
        out[2] = m[6] * x + m[7] * y + m[8] * z
    }

    private fun dot3(a: FloatArray, b: FloatArray): Float =
        a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    /**
     * Map a filtered force (m/s^2) to a pixel displacement for the dots.
     * Clamped linear with a small deadzone (brief Section 7 — exact curve is
     * an engineering judgment call; we picked values that feel right on a
     * typical phone screen and exposed `sensitivity` so users can tune).
     *
     *   - deadzone: |f| < [DEADZONE] m/s^2 -> 0px (kills hand jitter at rest)
     *   - scale:    [SCALE_PX_PER_G] px per m/s^2, multiplied by user sensitivity
     *   - clamp:    ±[MAX_PX] px so dots never leave the periphery band
     */
    fun forceToDisplacement(
        force: Float,
        sensitivity: Float,
        deadzone: Float = DEFAULT_DEADZONE,
        maxPx: Float = DEFAULT_MAX_PX,
    ): Float {
        val absF = Math.abs(force)
        if (absF < deadzone) return 0f
        val signed = Math.signum(force) * (absF - deadzone)
        val px = signed * SCALE_PX_PER_G * sensitivity
        return px.coerceIn(-maxPx, maxPx)
    }

    /** 0..1 intensity used to pulse dot opacity/scale with force magnitude. */
    fun forceToIntensity(force: ForceVector): Float =
        (force.magnitude() / 2.5f).coerceIn(0f, 1f)

    /**
     * Smooth deadzone: instead of a hard cutoff (abruptly zeroing forces
     * below the threshold), use a cubic smoothstep that ramps force from 0
     * at 0 up to full strength at the deadzone boundary.
     *
     * This prevents the "dots don't move at all" feeling from subtle motion
     * while still killing sensor noise (very small forces produce negligible
     * displacement).
     */
    fun smoothDeadzone(value: Float, deadzone: Float): Float {
        if (deadzone <= 0f) return value
        val abs = Math.abs(value)
        if (abs <= 0f) return 0f
        if (abs >= deadzone) return value
        val t = abs / deadzone
        val smooth = t * t * (3f - 2f * t)
        return Math.signum(value) * smooth * abs
    }

    private const val DEFAULT_DEADZONE = 0.15f        // m/s^2
    private const val SCALE_PX_PER_G = 30f    // px per m/s^2
    private const val DEFAULT_MAX_PX = 150f
}
