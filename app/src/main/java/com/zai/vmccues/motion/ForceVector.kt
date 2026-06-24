package com.zai.vmccues.motion

/**
 * The two scalar force components we derive from the device's motion sensors,
 * expressed in the vehicle's reference frame (m/s^2).
 *
 *  - [lateral]:      left/right force. Positive = body pushed right
 *                     (vehicle turning left).
 *  - [longitudinal]: forward/back force. Positive = accelerating forward
 *                     (body pressed back into the seat).
 *
 * These are the same two scalars the iOS feature uses to drive the dot
 * displacement (brief Section 6, Layer 2).
 */
data class ForceVector(val lateral: Float, val longitudinal: Float) {
    fun magnitude(): Float = Math.hypot(lateral.toDouble(), longitudinal.toDouble()).toFloat()
    companion object { val ZERO = ForceVector(0f, 0f) }
}
