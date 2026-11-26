package com.cak3d.biscuitmesh.car

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cak3d.biscuitmesh.BiscuitMeshApp
import com.cak3d.biscuitmesh.wardrive.WardriveMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Starts a wardrive automatically when your car's Bluetooth connects — so getting in the car and
 * driving off begins catching with no taps. Matches the device name or MAC the operator set in
 * Settings; does nothing unless car auto-start is on and the name/MAC matches.
 */
class CarConnectReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        val app = context.applicationContext as? BiscuitMeshApp ?: return
        @Suppress("DEPRECATION")
        val dev = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        val addr = dev?.address ?: ""
        val name = runCatching { dev?.name }.getOrNull() ?: ""

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!app.settingsStore.carAutoStart.first()) return@launch
                val want = app.settingsStore.carBtName.first().trim()
                if (want.isEmpty()) return@launch
                val match = name.contains(want, ignoreCase = true) ||
                    addr.equals(want, ignoreCase = true) || addr.contains(want, ignoreCase = true)
                if (match && !app.wardriveActive) {
                    withContext(Dispatchers.Main) { app.widgetToggleScan(WardriveMode.ALL) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
