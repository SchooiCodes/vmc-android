package com.zai.vmccues.motion

/**
 * Exponential moving average low-pass filter (brief Section 6, Layer 2 —
 * "a simple exponential moving average is a reasonable starting point").
 *
 * [alpha] is the SMOOTHING amount: 0 = frozen, 1 = no smoothing. Higher alpha
 * = smoother but laggier. Causal and O(1) per sample, so it's cheap to call
 * at sensor rate (~60 Hz).
 */
class LowPassFilter(initialAlpha: Float = 0.8f) {
    @Volatile var alpha: Float = initialAlpha.coerceIn(0f, 1f)
        private set

    private var lastLateral: Float = 0f
    private var lastLongitudinal: Float = 0f
    private var initialized: Boolean = false

    fun setAlpha(value: Float) {
        alpha = value.coerceIn(0f, 1f)
    }

    fun reset() {
        initialized = false
        lastLateral = 0f
        lastLongitudinal = 0f
    }

    fun update(input: ForceVector): ForceVector {
        if (!initialized) {
            lastLateral = input.lateral
            lastLongitudinal = input.longitudinal
            initialized = true
            return ForceVector(input.lateral, input.longitudinal)
        }
        val a = alpha
        val out = ForceVector(
            lateral = lastLateral * a + input.lateral * (1f - a),
            longitudinal = lastLongitudinal * a + input.longitudinal * (1f - a),
        )
        lastLateral = out.lateral
        lastLongitudinal = out.longitudinal
        return out
    }
}
