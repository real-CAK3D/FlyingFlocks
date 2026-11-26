package com.cak3d.biscuitmesh.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.cak3d.biscuitmesh.BiscuitMeshApp
import com.cak3d.biscuitmesh.wardrive.WardriveMode

/**
 * A Quick Settings tile: one tap in the notification shade starts or stops an ALL-mode wardrive —
 * no need to open the app. Mirrors the home-screen Wardrive widget, for the fastest possible
 * "start catching" from anywhere in the system.
 */
class QuickScanTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        sync()
    }

    override fun onClick() {
        super.onClick()
        (applicationContext as? BiscuitMeshApp)?.widgetToggleScan(WardriveMode.ALL)
        sync()
    }

    private fun sync() {
        val app = applicationContext as? BiscuitMeshApp
        val on = app?.wardriveActive == true && app.wardriveMode == WardriveMode.ALL
        qsTile?.apply {
            state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "Wardrive"
            contentDescription = if (on) "Wardrive scanning — tap to stop" else "Tap to start a wardrive"
            updateTile()
        }
    }
}
