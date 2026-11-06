package com.cak3d.biscuitmesh.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One satellite the GNSS receiver can currently see. */
data class Sat(
    val constellation: String,
    val svid: Int,
    val cn0: Float,          // carrier-to-noise density, dB-Hz (signal quality)
    val usedInFix: Boolean,  // true = contributing to the current position fix
    val elevationDeg: Float,
    val azimuthDeg: Float,
)

data class GnssSnapshot(
    val sats: List<Sat> = emptyList(),
    val usedCount: Int = 0,
    val visibleCount: Int = 0,
    val supported: Boolean = true,
)

/**
 * Live GNSS satellite status — which satellites the phone sees and which ones it's
 * actually using to compute the GPS fix right now. Reads Android's GnssStatus.
 */
class GnssManager(context: Context) {

    private val appContext = context.applicationContext
    private val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _state = MutableStateFlow(GnssSnapshot())
    val state: StateFlow<GnssSnapshot> = _state.asStateFlow()

    private var callback: GnssStatus.Callback? = null

    @SuppressLint("MissingPermission")
    fun start() {
        val mgr = lm ?: run { _state.value = GnssSnapshot(supported = false); return }
        if (callback != null) return
        val cb = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                val list = ArrayList<Sat>(status.satelliteCount)
                for (i in 0 until status.satelliteCount) {
                    list.add(
                        Sat(
                            constellation = constellationName(status.getConstellationType(i)),
                            svid = status.getSvid(i),
                            cn0 = status.getCn0DbHz(i),
                            usedInFix = status.usedInFix(i),
                            elevationDeg = status.getElevationDegrees(i),
                            azimuthDeg = status.getAzimuthDegrees(i),
                        ),
                    )
                }
                _state.value = GnssSnapshot(
                    sats = list.sortedByDescending { it.cn0 },
                    usedCount = list.count { it.usedInFix },
                    visibleCount = list.size,
                    supported = true,
                )
            }
        }
        callback = cb
        runCatching { mgr.registerGnssStatusCallback(cb, Handler(Looper.getMainLooper())) }
            .onFailure { _state.value = GnssSnapshot(supported = false) }
    }

    fun stop() {
        callback?.let { cb -> runCatching { lm?.unregisterGnssStatusCallback(cb) } }
        callback = null
    }

    private fun constellationName(type: Int): String = when (type) {
        GnssStatus.CONSTELLATION_GPS -> "GPS"
        GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
        GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
        GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
        GnssStatus.CONSTELLATION_QZSS -> "QZSS"
        GnssStatus.CONSTELLATION_SBAS -> "SBAS"
        GnssStatus.CONSTELLATION_IRNSS -> "NavIC"
        else -> "Other"
    }
}
