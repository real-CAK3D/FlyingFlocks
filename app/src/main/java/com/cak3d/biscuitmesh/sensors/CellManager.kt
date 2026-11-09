package com.cak3d.biscuitmesh.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One cell (tower sector) the modem can currently see. */
data class CellTower(
    val tech: String,       // "5G NR", "LTE", "3G", "2G", "CDMA"
    val tier: Int,          // 0 = low band, 1 = mid band, 2 = high band (lane bucket)
    val dbm: Int,           // signal strength
    val registered: Boolean, // serving / camped-on cell
)

data class CellSnapshot(
    val cells: List<CellTower> = emptyList(),
    val supported: Boolean = true,
)

/**
 * Live cell-tower status — which cells the modem sees and how strong they are. Polls
 * TelephonyManager.getAllCellInfo on a background coroutine (ANR-safe) and leans on the
 * ACCESS_FINE_LOCATION the wardrive already holds. If the grant is missing the list just
 * comes back empty and the Scope's cell lane stays quiet.
 */
class CellManager(context: Context) {

    private val appContext = context.applicationContext
    private val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private val _state = MutableStateFlow(CellSnapshot())
    val state: StateFlow<CellSnapshot> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    @SuppressLint("MissingPermission")
    fun start() {
        val mgr = tm ?: run { _state.value = CellSnapshot(supported = false); return }
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                val list = runCatching { mgr.allCellInfo }.getOrNull()
                if (list != null) {
                    val cells = ArrayList<CellTower>(list.size)
                    for (info in list) {
                        val tower = toTower(info) ?: continue
                        if (tower.dbm in -140..-30) cells.add(tower)
                    }
                    _state.value = CellSnapshot(cells.sortedByDescending { it.dbm }, supported = true)
                }
                delay(2500)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun toTower(info: CellInfo): CellTower? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) nrTower(info)?.let { return it }
        return when (info) {
            is CellInfoLte -> CellTower("LTE", 1, info.cellSignalStrength.dbm, info.isRegistered)
            is CellInfoWcdma -> CellTower("3G", 0, info.cellSignalStrength.dbm, info.isRegistered)
            is CellInfoGsm -> CellTower("2G", 0, info.cellSignalStrength.dbm, info.isRegistered)
            is CellInfoCdma -> CellTower("CDMA", 0, info.cellSignalStrength.dbm, info.isRegistered)
            else -> null
        }
    }

    @SuppressLint("NewApi")
    private fun nrTower(info: CellInfo): CellTower? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        if (info !is CellInfoNr) return null
        val ss = info.cellSignalStrength as? CellSignalStrengthNr ?: return null
        return CellTower("5G NR", 2, ss.dbm, info.isRegistered)
    }
}
