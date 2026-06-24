package com.zai.vmccues.gate

import android.os.SystemClock
import com.zai.vmccues.motion.MotionPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The resolved state of the Layer-1 context gate (brief Section 6 Layer 1 /
 * Section 4). The gate transitions through these states so the "Automatic"
 * mode doesn't snap on/off instantly — mirroring Apple's documented behavior
 * for the equivalent iOS feature.
 *
 *  - UNKNOWN:   no recent signal that the user is in a vehicle.
 *  - CANDIDATE: ActivityRecognition said "probably in a vehicle", waiting for
 *               the entry confirmation grace period to elapse.
 *  - CONFIRMED: confirmation signals agreed and the grace period elapsed;
 *               the overlay is allowed to show.
 *  - LOST:      was CONFIRMED, motion signal has gone quiet, waiting through
 *               the exit grace period before fully disengaging.
 */
enum class VehicleContextState { UNKNOWN, CANDIDATE, CONFIRMED, LOST }

/**
 * Snapshot of the gate. [inVehicle] is the boolean the renderer actually
 * uses ("should I treat this as a vehicle context right now?") — it's true
 * for CONFIRMED and LOST so dots don't disappear the instant motion pauses.
 */
data class ContextGateStatus(
    val state: VehicleContextState,
    val confidence: Int,        // 0..100, smoothed
    val inVehicle: Boolean,
    val lastChangeUptimeMs: Long,
)

/**
 * Layer 1 of the architecture (brief Section 6 Layer 1 / Section 4).
 *
 * Fuses two signals into a single [ContextGateStatus]:
 *   1. The coarse ActivityRecognition "IN_VEHICLE" gate (when permission is
 *      granted) — battery-friendly, runs even while the app is backgrounded.
 *   2. The motion-signal statistics from the [MotionPipeline] — a sliding
 *      window of |force| magnitudes. Sustained motion above walking pace
 *      corroborates "in a vehicle"; quiet below threshold for the exit grace
 *      disengages.
 *
 * The state machine transitions with explicit grace periods so the "Automatic"
 * mode doesn't snap on/off instantly — mirroring Apple's documented behavior
 * (brief Section 3.4 / Section 4).
 *
 *  UNKNOWN  --(AR in-vehicle OR sustained motion)-->  CANDIDATE
 *  CANDIDATE --(held ENTRY_CONFIRM_MS)-->             CONFIRMED
 *  CONFIRMED --(quiet for EXIT_GRACE_MS)-->           LOST
 *  LOST      --(quiet for EXIT_GRACE_MS more)-->      UNKNOWN
 *  Any      --(manual override via [force])-->        forced
 */
class ContextGate(
    private val pipeline: MotionPipeline,
    private val activity: ActivityRecognitionProvider,
    private val scope: CoroutineScope,
) {
    private val _status = MutableStateFlow(
        ContextGateStatus(VehicleContextState.UNKNOWN, 0, false, SystemClock.elapsedRealtime())
    )
    val status: StateFlow<ContextGateStatus> = _status

    @Volatile private var manualInVehicle: Boolean? = null
    @Volatile var entryConfirmMs: Long = 4_000L
    @Volatile var exitGraceMs: Long = 30_000L

    private val magWindow = ArrayDeque<Float>()  // last ~3s of |force|
    private var lastDriftMs: Long = SystemClock.elapsedRealtime()
    private var loopJob: Job? = null

    fun start() {
        if (loopJob?.isActive == true) return
        activity.start()
        loopJob = scope.launch { gateLoop() }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        activity.stop()
        synchronized(magWindow) { magWindow.clear() }
        _status.value = ContextGateStatus(
            VehicleContextState.UNKNOWN, 0, false, SystemClock.elapsedRealtime()
        )
    }

    /**
     * Manual override (used when the user picks mode = ON, not Automatic).
     * Pass `true` to force "in vehicle", `false` to force "not in vehicle",
     * or `null` to clear the override and let the gate decide (Automatic mode).
     */
    fun force(inVehicle: Boolean?) {
        manualInVehicle = inVehicle
    }

    private suspend fun gateLoop() {
        while (scope.isActive) {
            delay(GATE_TICK_MS)
            tick()
        }
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val filtered = pipeline.filteredForce.value
        val ar = activity.latest.value

        // Maintain the sliding window of force magnitudes.
        synchronized(magWindow) {
            magWindow.addLast(filtered.magnitude())
            while (magWindow.isNotEmpty() && magWindow.size > WINDOW_SAMPLES) {
                magWindow.removeFirst()
            }
        }
        val avgMag = synchronized(magWindow) {
            if (magWindow.isEmpty()) 0f else magWindow.toFloatArray().average().toFloat()
        }

        // Manual override short-circuits everything.
        manualInVehicle?.let { forced ->
            val s = if (forced) VehicleContextState.CONFIRMED else VehicleContextState.UNKNOWN
            val conf = if (forced) 100 else 0
            update(s, conf, now)
            return
        }

        // Fused "is there vehicle-like motion right now?" signal.
        val arSays = ar.inVehicle && (now - ar.timestampMs < AR_FRESH_MS)
        val motionSays = avgMag > WALKING_THRESHOLD

        val cur = _status.value
        val nextState: VehicleContextState = when (cur.state) {
            VehicleContextState.UNKNOWN, VehicleContextState.LOST -> {
                if (arSays || motionSays) VehicleContextState.CANDIDATE else cur.state
            }
            VehicleContextState.CANDIDATE -> {
                val held = now - cur.lastChangeUptimeMs >= entryConfirmMs
                when {
                    !arSays && !motionSays -> VehicleContextState.UNKNOWN
                    held -> VehicleContextState.CONFIRMED
                    else -> VehicleContextState.CANDIDATE
                }
            }
            VehicleContextState.CONFIRMED -> {
                val quiet = !arSays && !motionSays
                if (quiet) VehicleContextState.LOST else VehicleContextState.CONFIRMED
            }
        }

        // Handle the LOST -> UNKNOWN decay separately (a second grace period).
        val finalState: VehicleContextState = if (cur.state == VehicleContextState.LOST && nextState == VehicleContextState.LOST) {
            if (now - cur.lastChangeUptimeMs >= exitGraceMs * 2) VehicleContextState.UNKNOWN
            else VehicleContextState.LOST
        } else nextState

        // Drift the smoothed confidence toward a target based on state.
        val target = when (finalState) {
            VehicleContextState.CONFIRMED -> 92
            VehicleContextState.CANDIDATE -> 60
            VehicleContextState.LOST -> 35
            VehicleContextState.UNKNOWN -> 5
        }
        val dt = (now - lastDriftMs) / 1000f
        lastDriftMs = now
        val conf = _status.value.confidence + ((target - _status.value.confidence) * (dt * 2f)).toInt()
        val clampedConf = conf.coerceIn(0, 100)

        update(finalState, clampedConf, now)
    }

    private fun update(state: VehicleContextState, confidence: Int, now: Long) {
        val prev = _status.value
        val lastChange = if (state != prev.state) now else prev.lastChangeUptimeMs
        val inVehicle = state == VehicleContextState.CONFIRMED || state == VehicleContextState.LOST
        _status.value = ContextGateStatus(state, confidence, inVehicle, lastChange)
    }

    companion object {
        private const val GATE_TICK_MS = 500L
        private const val WINDOW_SAMPLES = 12            // ~3s at 250ms cadence
        private const val WALKING_THRESHOLD = 0.6f       // m/s^2
        private const val AR_FRESH_MS = 60_000L          // AR result usable for 60s
    }
}
