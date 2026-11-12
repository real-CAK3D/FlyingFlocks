package com.cak3d.biscuitmesh.ui

import android.bluetooth.BluetoothManager
import android.content.Context
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SB_PANEL = Color(0xFF101217)
private val SB_INK = Color(0xFFE8E6DC)
private val SB_DIM = Color(0xFF6B7078)
private val SB_GREEN = Color(0xFF53D38A)
private val SB_AMBER = Color(0xFFF2AC2A)
private val SB_MONO = FontFamily.Monospace

private val SB_TIME = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
private val SB_DATE = SimpleDateFormat("EEE d MMM", Locale.getDefault())

/** GPS readiness: a recent fix, provider on but no fix, or off. */
private enum class Gps { FIX, ON, OFF }

/**
 * A compact strip of the phone's live state that the app depends on: date/time,
 * phone battery, the BLE link to the board ("app connection"), and the Wi-Fi,
 * Bluetooth and GPS radios. Polls every 2s; anything it can't read shows dim.
 */
@Composable
fun PhoneStatusBar(boardConnected: Boolean, boardLink: String?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var batt by remember { mutableStateOf<Int?>(null) }
    var wifi by remember { mutableStateOf(false) }
    var bt by remember { mutableStateOf(false) }
    var gps by remember { mutableStateOf(Gps.OFF) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            batt = readBattery(ctx)
            wifi = readWifi(ctx)
            bt = readBt(ctx)
            gps = readGps(ctx)
            delay(2000)
        }
    }

    Row(
        modifier.fillMaxWidth()
            .background(SB_PANEL, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.padding(end = 2.dp)) {
            Text(SB_TIME.format(Date(now)), color = SB_INK, fontFamily = SB_MONO,
                fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(SB_DATE.format(Date(now)), color = SB_DIM, fontFamily = SB_MONO, fontSize = 9.sp)
        }
        // phone battery
        Text(
            batt?.let { "$it%" } ?: "—%",
            color = when { batt == null -> SB_DIM; batt!! <= 15 -> SB_AMBER; else -> SB_INK },
            fontFamily = SB_MONO, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // "app connection" = BLE link to the board (green when linked, amber when just connected)
            StatusIcon(Icons.Default.Link, "APP",
                when { boardLink == "ok" -> SB_GREEN; boardConnected -> SB_AMBER; else -> SB_DIM })
            StatusIcon(Icons.Default.Wifi, "WIFI", if (wifi) SB_GREEN else SB_DIM)
            StatusIcon(Icons.Default.Bluetooth, "BT", if (bt) SB_GREEN else SB_DIM)
            StatusIcon(Icons.Default.LocationOn, "GPS",
                when (gps) { Gps.FIX -> SB_GREEN; Gps.ON -> SB_AMBER; Gps.OFF -> SB_DIM })
        }
    }
}

@Composable
private fun StatusIcon(icon: ImageVector, label: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.padding(bottom = 1.dp))
        Text(label, color = tint, fontFamily = SB_MONO, fontSize = 8.sp)
    }
}

private fun readBattery(ctx: Context): Int? = runCatching {
    val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
}.getOrNull()

private fun readWifi(ctx: Context): Boolean = runCatching {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
    caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
}.getOrDefault(false)

private fun readBt(ctx: Context): Boolean = runCatching {
    val mgr = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    mgr.adapter?.isEnabled == true
}.getOrDefault(false)

private fun readGps(ctx: Context): Gps = runCatching {
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return Gps.OFF
    // A fix within the last 2 minutes counts as "locked". Needs location permission; if
    // it isn't granted this throws and we fall back to "provider on, no fix".
    val fix = runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
    if (fix != null && System.currentTimeMillis() - fix.time < 120_000) Gps.FIX else Gps.ON
}.getOrDefault(Gps.OFF)
