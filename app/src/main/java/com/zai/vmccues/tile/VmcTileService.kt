package com.zai.vmccues.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.zai.vmccues.VmcApplication
import com.zai.vmccues.data.ActivationMode
import com.zai.vmccues.overlay.OverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Quick Settings tile (brief Section 6 Layer 4) — Android's equivalent of
 * iOS Control Center. One-tap toggle between ON and OFF. AUTOMATIC mode is
 * only reachable from the settings screen; the tile cycles ON ↔ OFF.
 */
class VmcTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var collectorJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        collectorJob?.cancel()
        collectorJob = scope.launch {
            VmcApplication.get().settings.settings.collectLatest { s ->
                updateTile(s.mode != ActivationMode.OFF)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        collectorJob?.cancel()
        collectorJob = null
    }

    override fun onClick() {
        super.onClick()
        val app = VmcApplication.get()
        // Run the toggle off the collector job so it works even if the tile
        // is invoked edge-case-ish; reuse the main scope.
        scope.launch {
            val current = app.settings.settings.value
            val next = if (current.mode == ActivationMode.OFF) ActivationMode.ON
                       else ActivationMode.OFF
            app.settings.setMode(next)
            if (next == ActivationMode.OFF) {
                OverlayService.stop(this@VmcTileService)
            } else {
                OverlayService.start(this@VmcTileService)
            }
            updateTile(next != ActivationMode.OFF)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun updateTile(active: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (active) "On" else "Off"
        }
        tile.contentDescription = "Vehicle Motion Cues"
        tile.updateTile()
    }
}
