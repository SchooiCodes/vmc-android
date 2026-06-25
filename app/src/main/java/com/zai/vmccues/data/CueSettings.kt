package com.zai.vmccues.data

/**
 * All user-facing settings for the feature. Persisted via [SettingsRepository].
 *
 * Defaults follow the spec's [ESTIMATE] starter constants (Part B.3/B.4).
 */
data class CueSettings(
    val mode: ActivationMode = ActivationMode.DEFAULT,
    val pattern: DotPattern = DotPattern.DEFAULT,
    val dotColor: Int = 0xFFF5F5F5.toInt(), // ARGB
    val autoContrast: Boolean = true,
    val adaptiveContrast: Boolean = true,
    val largerDots: Boolean = false,
    val moreDots: Boolean = false,

    /** 0.5f..3.0f — overall sensitivity multiplier on dot displacement. */
    val sensitivity: Float = 2.0f,

    // --- Dead-reckoning integrator (spec Part B.3) ---
    /** 0.10f..0.30f — low-pass filter coefficient (lower = smoother, more lag). */
    val filterAlpha: Float = 0.18f,
    /** 2.0f..10.0f (1/s) — spring-damper coefficient (higher = dots settle faster). */
    val dampingCoef: Float = 5.5f,
    /** 0.5f..5.0f (1/s) — return-to-center pull (higher = dots recenter faster). */
    val returnToCenterCoef: Float = 2.5f,
    /** 3.0f..12.0f m/s² — max input acceleration clamp (~0.3-1.2g). */
    val inputClamp: Float = 8.0f,

    // --- Signal conditioning (spec Part B.4) ---
    /** 0.05f..0.5f m/s² — dead-band: forces below this are ignored (kills jitter). */
    val deadzone: Float = 0.25f,

    // --- Advanced appearance ---
    /** 0.1f..1.0f — base dot opacity at rest (before intensity modulation). */
    val dotOpacity: Float = 0.45f,
    /** 8f..40f dp — distance of dots from the physical screen edge. */
    val dotInsetDp: Float = 16f,
    /** 0.3f..1.0f — how much dot opacity scales with force intensity. */
    val intensityResponse: Float = 0.6f,

    // --- Context gate tuning (Automatic mode, spec Part B.5) ---
    /** 1000f..10000f ms — sustained motion before confirming "in vehicle". */
    val gateEntryDelayMs: Long = 5_000L,
    /** 3000f..15000f ms — quiet before disengaging (spec: fast exit 3-5s). */
    val gateExitGraceMs: Long = 4_000L,

    /** One-shot flag: the Layer-5 safety disclaimer has been acknowledged. */
    val safetyAcknowledged: Boolean = false,
)
