package com.zai.vmccues.data

/**
 * The 3-way activation mode mirrors Apple's setting (brief Section 3.4).
 *  - OFF:        feature disabled entirely
 *  - ON:         dots always shown (manual)
 *  - AUTOMATIC:  only shown when the context gate infers "passenger in moving
 *                vehicle"
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

/** Pattern preset (brief Section 3.3). */
enum class DotPattern {
    REGULAR,
    DYNAMIC;

    companion object {
        fun fromName(name: String?): DotPattern =
            entries.firstOrNull { it.name == name } ?: DEFAULT
        val DEFAULT = REGULAR
    }
}
