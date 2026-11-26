package com.cak3d.biscuitmesh.geo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cbrt

/**
 * Finds the "Starlink train" — the freshest batch of just-launched Starlinks, still flying low and
 * bunched together in one orbital plane before they raise to their operational 550 km shells. For a
 * few days after each launch they cross the sky as a bright "string of pearls," one of the most
 * asked-about things in the night sky. We spot the freshest cluster from live TLEs (lowest orbit =
 * most recently launched) and predict when that cluster next passes over you, sunlit against a dark
 * sky, using the same validated SGP4 + Sun-shadow math as Visible Passes.
 */
object StarlinkTrains {

    /** Circular-orbit altitude (km) from mean motion (revolutions per day). */
    fun altitudeKm(meanMotion: Double): Double {
        if (meanMotion <= 0.0) return 0.0
        val mu = 398_600.4418                       // km^3/s^2
        val n = meanMotion * 2.0 * PI / 86_400.0    // rad/s
        val a = cbrt(mu / (n * n))                  // semi-major axis, km
        return a - 6378.137
    }

    private fun safeMeanMotion(t: Tle): Double = runCatching { t.meanMotion }.getOrDefault(0.0)

    data class Train(
        val sats: Int,
        val altKm: Int,
        val passes: List<VisiblePass>,
        /** True when the full Starlink catalogue came back and a genuinely low cluster was found. */
        val fresh: Boolean,
    )

    /**
     * The freshest low-orbit Starlink cluster and its next visible passes over [lat]/[lon].
     * Returns null only when the catalogue couldn't be fetched.
     */
    suspend fun freshestTrain(
        tle: TleClient,
        lat: Double,
        lon: Double,
        fromMs: Long = System.currentTimeMillis(),
    ): Train? = withContext(Dispatchers.Default) {
        val all = tle.group(TleClient.STARLINK)
        if (all.isEmpty()) return@withContext null
        // Lowest orbit = most recently launched and still raising. 15.5 rev/day ≈ below ~400 km.
        val low = all.filter { safeMeanMotion(it) >= 15.5 }.sortedByDescending { safeMeanMotion(it) }
        val fresh = low.isNotEmpty()
        val cluster = (if (fresh) low else all.sortedByDescending { safeMeanMotion(it) }).take(30)
        val avgAlt = cluster.map { altitudeKm(safeMeanMotion(it)) }.filter { it > 0 }.average()
            .let { if (it.isNaN()) 0 else it.toInt() }
        val passes = visiblePasses(cluster, lat, lon, fromMs, windowHrs = 48, minPeakElDeg = 18.0)
            .sortedBy { it.aosMs }
            .take(20)
        Train(cluster.size, avgAlt, passes, fresh)
    }
}
