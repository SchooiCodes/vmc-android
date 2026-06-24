package com.zai.vmccues.ui.components

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zai.vmccues.gate.ActivityRecognitionProvider
import com.zai.vmccues.gate.ContextGateStatus
import com.zai.vmccues.gate.VehicleContextState
import com.zai.vmccues.motion.ForceVector
import com.zai.vmccues.ui.theme.IosTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * Diagnostics / telemetry panel for the Vehicle Motion Cues motion pipeline.
 *
 * Shows the live state of the three upstream signals (Layer 2 force, Layer 1
 * context gate, and the ActivityRecognition coarse gate) so we can verify
 * the pipeline is producing sane numbers without staring at logcat.
 *
 * Because the real [MotionPipeline] / [ContextGate] instances live inside the
 * OverlayService and aren't directly reachable from Compose, the sheet accepts
 * their backing [StateFlow]s directly — the caller pulls them off whatever
 * scope owns the pipeline and hands them in.
 *
 * All four flows are collected lifecycle-aware-ly via
 * [collectAsStateWithLifecycle], so the sheet stops recomposing when the host
 * activity is backgrounded (the underlying sensors keep running inside the
 * Service regardless).
 *
 * The force magnitude bar and the oscilloscope both clip to a 0–5 m/s² range
 * — enough headroom for aggressive city driving while keeping normal cruising
 * motion readable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsSheet(
    filteredFlow: StateFlow<ForceVector>,
    rawFlow: StateFlow<ForceVector>,
    gateFlow: StateFlow<ContextGateStatus>,
    arFlow: StateFlow<ActivityRecognitionProvider.ActivityResult>,
    onDismiss: () -> Unit,
) {
    val colors = IosTheme.colors
    val typo = IosTheme.typography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Collect each upstream flow lifecycle-aware-ly. StateFlow overload uses
    // the current .value as the initial value, so no initialValue arg needed.
    val filtered by filteredFlow.collectAsStateWithLifecycle()
    val raw by rawFlow.collectAsStateWithLifecycle()
    val gate by gateFlow.collectAsStateWithLifecycle()
    val ar by arFlow.collectAsStateWithLifecycle()

    // Rolling buffer of recent |force| magnitudes for the oscilloscope. We
    // sample the filtered flow's .value on a fixed 50ms tick rather than
    // recomposing on every sensor sample (~50Hz) — keeps the panel smooth
    // without drowning the recomposer.
    val scopeSamples = remember { mutableStateListOf<Float>() }
    LaunchedEffect(Unit) {
        while (true) {
            scopeSamples.add(filteredFlow.value.magnitude())
            while (scopeSamples.size > SCOPE_SAMPLES) scopeSamples.removeAt(0)
            delay(SCOPE_TICK_MS)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.groupedBackground,
        dragHandle = null,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            // --- Title bar ---
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 4.dp, top = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Diagnostics",
                    style = typo.headline,
                    color = colors.label,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Done",
                        style = typo.body.copy(fontWeight = FontWeight.Normal),
                        color = colors.blue,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // --- Force section ---
            SectionCard(header = "Force (m/s\u00B2)") {
                ForceRow(label = "Lateral (raw)", value = raw.lateral, swatch = colors.teal)
                ForceRow(label = "Lateral (filtered)", value = filtered.lateral, swatch = colors.teal)
                ForceRow(label = "Longitudinal (raw)", value = raw.longitudinal, swatch = colors.orange)
                ForceRow(
                    label = "Longitudinal (filtered)",
                    value = filtered.longitudinal,
                    swatch = colors.orange,
                )
                Spacer(Modifier.height(4.dp))
                MagnitudeBar(magnitude = filtered.magnitude())
            }

            Spacer(Modifier.height(12.dp))

            // --- Oscilloscope ---
            SectionCard(header = "Oscilloscope  \u00B7  |F|  \u00B7  last 6s") {
                val teal = colors.teal
                val separator = colors.separator
                val tertiaryLabel = colors.tertiaryLabel
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                    ) {
                        drawScopeTrace(
                            samples = scopeSamples,
                            strokeColor = teal,
                            baselineColor = separator,
                            tickColor = tertiaryLabel,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // --- Context gate section ---
            SectionCard(header = "Context Gate") {
                KVRow(key = "State", value = formatState(gate.state), valueColor = stateColor(gate.state))
                KVRow(key = "Confidence", value = "${gate.confidence}%")
                KVRow(key = "In Vehicle", value = if (gate.inVehicle) "Yes" else "No")
                KVRow(
                    key = "Last Change",
                    value = formatAgo(SystemClock.elapsedRealtime() - gate.lastChangeUptimeMs),
                )
            }

            Spacer(Modifier.height(12.dp))

            // --- Activity recognition section ---
            SectionCard(header = "Activity Recognition") {
                KVRow(key = "In Vehicle", value = if (ar.inVehicle) "Yes" else "No")
                KVRow(key = "Confidence", value = "${ar.confidence}%")
                // AR timestampMs is wall-clock (System.currentTimeMillis()),
                // so age it against wall-clock — not elapsedRealtime.
                KVRow(
                    key = "Last Update",
                    value = formatAgo(System.currentTimeMillis() - ar.timestampMs),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Section card
// ---------------------------------------------------------------------------

/**
 * A grouped inset card with an uppercase caption1 header, matching the iOS
 * Settings grouped-list style used elsewhere in the app.
 */
@Composable
private fun SectionCard(
    header: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = IosTheme.colors
    val typo = IosTheme.typography
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = header.uppercase(),
            style = typo.caption1,
            color = colors.secondaryLabel,
            modifier = Modifier.padding(start = 32.dp, end = 16.dp, bottom = 6.dp),
            letterSpacing = 0.4.sp,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.secondaryGroupedBackground)
                .padding(vertical = 8.dp),
        ) {
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// Force rows + magnitude bar
// ---------------------------------------------------------------------------

@Composable
private fun ForceRow(label: String, value: Float, swatch: Color) {
    val colors = IosTheme.colors
    val typo = IosTheme.typography
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(swatch),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = typo.subheadline,
            color = colors.label,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = formatForce(value),
            style = typo.body.copy(fontWeight = FontWeight.Normal),
            color = colors.secondaryLabel,
        )
    }
}

/**
 * Horizontal bar showing the current filtered |force| on a 0–5 m/s² scale.
 * Color shifts green → orange → red so the magnitude is readable at a glance.
 */
@Composable
private fun MagnitudeBar(magnitude: Float) {
    val colors = IosTheme.colors
    val typo = IosTheme.typography
    val frac = (magnitude / MAG_MAX).coerceIn(0f, 1f)
    val barColor = when {
        magnitude > 3f -> colors.red
        magnitude > 1.5f -> colors.orange
        else -> colors.green
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "|F|",
                style = typo.caption1.copy(fontWeight = FontWeight.Normal),
                color = colors.secondaryLabel,
            )
            Text(
                text = "${"%.2f".format(magnitude)} / ${MAG_MAX.toInt()} m/s\u00B2",
                style = typo.caption1.copy(fontWeight = FontWeight.Normal),
                color = colors.secondaryLabel,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.secondaryFill),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(frac)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Oscilloscope
// ---------------------------------------------------------------------------

/**
 * Draws the rolling magnitude trace into the [Canvas]. Baseline sits at the
 * bottom; values rise upward, clipped to MAG_MAX. A faint baseline and a
 * couple of horizontal grid ticks give the eye a reference.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawScopeTrace(
    samples: List<Float>,
    strokeColor: Color,
    baselineColor: Color,
    tickColor: Color,
) {
    val w = size.width
    val h = size.height
    val baselineY = h - 2f
    val topY = 4f
    val usable = (baselineY - topY).coerceAtLeast(1f)

    // Grid ticks at 1, 2, 3, 4 m/s^2.
    for (tick in 1..4) {
        val y = baselineY - (tick / MAG_MAX) * usable
        drawLine(
            color = tickColor,
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = 0.5f,
        )
    }

    // Baseline.
    drawLine(
        color = baselineColor,
        start = Offset(0f, baselineY),
        end = Offset(w, baselineY),
        strokeWidth = 1f,
    )

    if (samples.size < 2) return

    val step = w / (SCOPE_SAMPLES - 1).toFloat()
    for (i in 1 until samples.size) {
        val x0 = (i - 1) * step
        val x1 = i * step
        val frac0 = (samples[i - 1] / MAG_MAX).coerceIn(0f, 1f)
        val frac1 = (samples[i] / MAG_MAX).coerceIn(0f, 1f)
        val y0 = baselineY - frac0 * usable
        val y1 = baselineY - frac1 * usable
        drawLine(
            color = strokeColor,
            start = Offset(x0, y0),
            end = Offset(x1, y1),
            strokeWidth = 2f,
            cap = StrokeCap.Round,
        )
    }
}

// ---------------------------------------------------------------------------
// Key/value rows (gate + AR sections)
// ---------------------------------------------------------------------------

@Composable
private fun KVRow(key: String, value: String, valueColor: Color? = null) {
    val colors = IosTheme.colors
    val typo = IosTheme.typography
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = key,
            style = typo.subheadline,
            color = colors.label,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = typo.body.copy(fontWeight = FontWeight.Normal),
            color = valueColor ?: colors.secondaryLabel,
        )
    }
}

// ---------------------------------------------------------------------------
// Formatting + state colors
// ---------------------------------------------------------------------------

private fun formatForce(v: Float): String {
    val sign = if (v >= 0f) "+" else "\u2212"
    return "$sign${"%.2f".format(kotlin.math.abs(v))}"
}

private fun formatAgo(deltaMs: Long): String {
    if (deltaMs < 0) return "just now"
    val s = deltaMs / 1000.0
    return when {
        s < 1.0 -> "just now"
        s < 60.0 -> "${"%.1f".format(s)}s ago"
        s < 3600.0 -> "${"%.1f".format(s / 60.0)}m ago"
        else -> "${"%.1f".format(s / 3600.0)}h ago"
    }
}

private fun formatState(s: VehicleContextState): String =
    s.name.lowercase().replaceFirstChar { it.uppercase() }

@Composable
private fun stateColor(s: VehicleContextState): Color {
    val colors = IosTheme.colors
    return when (s) {
        VehicleContextState.UNKNOWN -> colors.tertiaryLabel
        VehicleContextState.CANDIDATE -> colors.orange
        VehicleContextState.CONFIRMED -> colors.green
        VehicleContextState.LOST -> colors.red
    }
}

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

private const val MAG_MAX = 5f              // m/s^2 — top of the magnitude bar / scope
private const val SCOPE_SAMPLES = 120       // ~6s of history at 50ms cadence
private const val SCOPE_TICK_MS = 50L
