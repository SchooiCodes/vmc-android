package com.zai.vmccues.data

/**
 * The 3-way activation mode mirrors Apple's setting.
 *  - OFF:        feature disabled entirely
 *  - ON:         dots always shown (manual), with a lower deadzone so dots
 *                respond to walking-level forces
 *  - AUTOMATIC:  only shown when the context gate infers "passenger in moving
 *                vehicle" (requires actual vehicle motion)
 */
enum class ActivationMode {
    OFF,
    ON,
    AUTOMATIC;

    companion object {
        fun fromName(name: String?): ActivationMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
        val DEFAULT = ON
    }
}

/** Pattern preset. */
enum class DotPattern {
    REGULAR,
    DYNAMIC;

    companion object {
        fun fromName(name: String?): DotPattern =
            entries.firstOrNull { it.name == name } ?: DEFAULT
        val DEFAULT = REGULAR
    }
}

/**
 * Visibility option (Apple: "Visibility: Larger Dots or More Dots").
 * Mutually exclusive — only one can be active at a time.
 */
enum class DotVisibility {
    STANDARD,
    LARGER_DOTS,
    MORE_DOTS;

    companion object {
        fun fromName(name: String?): DotVisibility =
            entries.firstOrNull { it.name == name } ?: DEFAULT
        val DEFAULT = STANDARD
    }
}
