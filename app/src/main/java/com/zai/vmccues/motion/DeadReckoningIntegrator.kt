package com.zai.vmccues.motion

/**
 * Dead-reckoning integrator with critical damping — the confirmed-working
 * approach from the EasyQueasy clone (spec Part B.3).
 *
 * Instead of directly mapping force → pixel displacement, we:
 *   1. Low-pass filter the acceleration to denoise.
 *   2. Integrate acceleration → velocity (with spring-damper damping).
 *   3. Integrate velocity → position (with return-to-center pull).
 *   4. Negate the position so dots appear anchored to the outside world
 *      rather than the device.
 *
 * This creates a transient, self-correcting "nudge" that conveys direction
 * and rough magnitude of a turn/brake/accelerate event, then relaxes back
 * to center — exactly the behavior Apple's dots exhibit.
 *
 * The spring-damper formulation (rather than naive exponential decay)
 * gives a more natural "settle" feel:
 *   velocity += (acceleration_input - velocity * DAMPING_COEF) * dt
 *   position += velocity * dt
 *   position -= position * RETURN_TO_CENTER_COEF * dt
 *
 * All inputs/outputs are in m/s² (acceleration) and pixels (position, after
 * a scale factor is applied by the caller).
 */
class DeadReckoningIntegrator {

    // Current integrated state (2D: lateral, longitudinal).
    private var velocityX: Float = 0f
    private var velocityY: Float = 0f
    private var positionX: Float = 0f
    private var positionY: Float = 0f

    // Smoothed (low-pass filtered) acceleration.
    private var smoothAccelX: Float = 0f
    private var smoothAccelY: Float = 0f

    // Tunable parameters (see spec Part B.3/B.4).
    @Volatile var filterAlpha: Float = 0.18f         // low-pass coefficient (0.15-0.25)
    @Volatile var dampingCoef: Float = 2.0f          // spring-damper (4.0-8.0 1/s)
    @Volatile var returnToCenterCoef: Float = 0.5f   // position pull (1.5-3.0 1/s)
    @Volatile var inputClamp: Float = 12.0f          // max input acceleration (m/s²)
    @Volatile var pxPerMs2: Float = 90f              // scale: m/s² integrated → px

    fun setParams(
        filterAlpha: Float,
        dampingCoef: Float,
        returnToCenterCoef: Float,
        inputClamp: Float,
    ) {
        this.filterAlpha = filterAlpha
        this.dampingCoef = dampingCoef
        this.returnToCenterCoef = returnToCenterCoef
        this.inputClamp = inputClamp
    }

    @Synchronized
    fun reset() {
        velocityX = 0f; velocityY = 0f
        positionX = 0f; positionY = 0f
        smoothAccelX = 0f; smoothAccelY = 0f
    }

    /**
     * Advance the integrator by [dtSec] seconds given a new vehicle-frame
     * acceleration [input] (m/s²).
     *
     * Returns the NEGATED position (so dots appear anchored to the earth)
     * scaled to pixels, ready for dot displacement.
     */
    @Synchronized
    fun update(input: ForceVector, dtSec: Float): ForceVector {
        var ax = input.lateral
        var ay = input.longitudinal

        // Clamp input acceleration to avoid extreme excursions on hard
        // braking/potholes (spec B.4: clamp to ~0.6-0.8 g).
        ax = ax.coerceIn(-inputClamp, inputClamp)
        ay = ay.coerceIn(-inputClamp, inputClamp)

        // 1. Low-pass filter to denoise.
        smoothAccelX += (ax - smoothAccelX) * filterAlpha
        smoothAccelY += (ay - smoothAccelY) * filterAlpha

        // 2. Integrate acceleration → velocity with spring-damper damping.
        //    velocity += (acceleration - velocity * DAMPING_COEF) * dt
        velocityX += (smoothAccelX - velocityX * dampingCoef) * dtSec
        velocityY += (smoothAccelY - velocityY * dampingCoef) * dtSec

        // 3. Integrate velocity → position.
        positionX += velocityX * dtSec
        positionY += velocityY * dtSec

        // 4. Return-to-center pull on position (prevents slow residual drift).
        positionX -= positionX * returnToCenterCoef * dtSec
        positionY -= positionY * returnToCenterCoef * dtSec

        // 5. Negate position so dots appear anchored to the outside world
        //    (spec B.3: "Negating the calculated position vector achieves
        //    this effect"). Scale to pixels.
        return ForceVector(
            lateral = -positionX * pxPerMs2,
            longitudinal = -positionY * pxPerMs2,
        )
    }

    /** Current negated+scaled position (for rendering without advancing state). */
    fun currentPosition(): ForceVector = ForceVector(
        lateral = -positionX * pxPerMs2,
        longitudinal = -positionY * pxPerMs2,
    )
}
