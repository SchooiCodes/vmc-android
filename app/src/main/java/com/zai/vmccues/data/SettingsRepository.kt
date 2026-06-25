package com.zai.vmccues.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "vmc_settings"
)

class SettingsRepository(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private object Keys {
        val MODE = stringPreferencesKey("mode")
        val PATTERN = stringPreferencesKey("pattern")
        val DOT_COLOR = intPreferencesKey("dot_color")
        val AUTO_CONTRAST = booleanPreferencesKey("auto_contrast")
        val ADAPTIVE_CONTRAST = booleanPreferencesKey("adaptive_contrast")
        val LARGER_DOTS = booleanPreferencesKey("larger_dots")
        val MORE_DOTS = booleanPreferencesKey("more_dots")
        val SENSITIVITY = floatPreferencesKey("sensitivity")
        val FILTER_ALPHA = floatPreferencesKey("filter_alpha")
        val DAMPING_COEF = floatPreferencesKey("damping_coef")
        val RETURN_TO_CENTER = floatPreferencesKey("return_to_center")
        val INPUT_CLAMP = floatPreferencesKey("input_clamp")
        val DEADZONE = floatPreferencesKey("deadzone")
        val DOT_OPACITY = floatPreferencesKey("dot_opacity")
        val DOT_INSET = floatPreferencesKey("dot_inset_dp")
        val INTENSITY_RESPONSE = floatPreferencesKey("intensity_response")
        val GATE_ENTRY_MS = longPreferencesKey("gate_entry_ms")
        val GATE_EXIT_MS = longPreferencesKey("gate_exit_ms")
        val SAFETY_ACK = booleanPreferencesKey("safety_ack")
    }

    private fun Preferences.toSettings(): CueSettings = CueSettings(
        mode = ActivationMode.fromName(this[Keys.MODE]),
        pattern = DotPattern.fromName(this[Keys.PATTERN]),
        dotColor = this[Keys.DOT_COLOR] ?: 0xFFF5F5F5.toInt(),
        autoContrast = this[Keys.AUTO_CONTRAST] ?: true,
        adaptiveContrast = this[Keys.ADAPTIVE_CONTRAST] ?: true,
        largerDots = this[Keys.LARGER_DOTS] ?: false,
        moreDots = this[Keys.MORE_DOTS] ?: false,
        sensitivity = this[Keys.SENSITIVITY] ?: 2.0f,
        filterAlpha = this[Keys.FILTER_ALPHA] ?: 0.18f,
        dampingCoef = this[Keys.DAMPING_COEF] ?: 5.5f,
        returnToCenterCoef = this[Keys.RETURN_TO_CENTER] ?: 2.5f,
        inputClamp = this[Keys.INPUT_CLAMP] ?: 8.0f,
        deadzone = this[Keys.DEADZONE] ?: 0.25f,
        dotOpacity = this[Keys.DOT_OPACITY] ?: 0.45f,
        dotInsetDp = this[Keys.DOT_INSET] ?: 16f,
        intensityResponse = this[Keys.INTENSITY_RESPONSE] ?: 0.6f,
        gateEntryDelayMs = this[Keys.GATE_ENTRY_MS] ?: 5_000L,
        gateExitGraceMs = this[Keys.GATE_EXIT_MS] ?: 4_000L,
        safetyAcknowledged = this[Keys.SAFETY_ACK] ?: false,
    )

    val settings: StateFlow<CueSettings> = appContext.settingsDataStore.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, CueSettings())

    suspend fun update(transform: (CueSettings) -> CueSettings) {
        appContext.settingsDataStore.edit { p ->
            val next = transform(p.toSettings())
            p[Keys.MODE] = next.mode.name
            p[Keys.PATTERN] = next.pattern.name
            p[Keys.DOT_COLOR] = next.dotColor
            p[Keys.AUTO_CONTRAST] = next.autoContrast
            p[Keys.ADAPTIVE_CONTRAST] = next.adaptiveContrast
            p[Keys.LARGER_DOTS] = next.largerDots
            p[Keys.MORE_DOTS] = next.moreDots
            p[Keys.SENSITIVITY] = next.sensitivity
            p[Keys.FILTER_ALPHA] = next.filterAlpha
            p[Keys.DAMPING_COEF] = next.dampingCoef
            p[Keys.RETURN_TO_CENTER] = next.returnToCenterCoef
            p[Keys.INPUT_CLAMP] = next.inputClamp
            p[Keys.DEADZONE] = next.deadzone
            p[Keys.DOT_OPACITY] = next.dotOpacity
            p[Keys.DOT_INSET] = next.dotInsetDp
            p[Keys.INTENSITY_RESPONSE] = next.intensityResponse
            p[Keys.GATE_ENTRY_MS] = next.gateEntryDelayMs
            p[Keys.GATE_EXIT_MS] = next.gateExitGraceMs
            p[Keys.SAFETY_ACK] = next.safetyAcknowledged
        }
    }

    suspend fun setMode(mode: ActivationMode) = update { it.copy(mode = mode) }
    suspend fun setPattern(pattern: DotPattern) = update { it.copy(pattern = pattern) }
    suspend fun setDotColor(color: Int) = update { it.copy(dotColor = color) }
    suspend fun setAutoContrast(v: Boolean) = update { it.copy(autoContrast = v) }
    suspend fun setAdaptiveContrast(v: Boolean) = update { it.copy(adaptiveContrast = v) }
    suspend fun setLargerDots(v: Boolean) = update { it.copy(largerDots = v) }
    suspend fun setMoreDots(v: Boolean) = update { it.copy(moreDots = v) }
    suspend fun setSensitivity(v: Float) = update { it.copy(sensitivity = v) }
    suspend fun setFilterAlpha(v: Float) = update { it.copy(filterAlpha = v) }
    suspend fun setDampingCoef(v: Float) = update { it.copy(dampingCoef = v) }
    suspend fun setReturnToCenterCoef(v: Float) = update { it.copy(returnToCenterCoef = v) }
    suspend fun setInputClamp(v: Float) = update { it.copy(inputClamp = v) }
    suspend fun setDeadzone(v: Float) = update { it.copy(deadzone = v) }
    suspend fun setDotOpacity(v: Float) = update { it.copy(dotOpacity = v) }
    suspend fun setDotInsetDp(v: Float) = update { it.copy(dotInsetDp = v) }
    suspend fun setIntensityResponse(v: Float) = update { it.copy(intensityResponse = v) }
    suspend fun setGateEntryDelayMs(v: Long) = update { it.copy(gateEntryDelayMs = v) }
    suspend fun setGateExitGraceMs(v: Long) = update { it.copy(gateExitGraceMs = v) }
    suspend fun ackSafety() = update { it.copy(safetyAcknowledged = true) }
    suspend fun reset() { appContext.settingsDataStore.edit { it.clear() } }
}
