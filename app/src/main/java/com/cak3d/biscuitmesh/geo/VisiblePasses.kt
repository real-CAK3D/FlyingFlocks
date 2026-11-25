package com.cak3d.biscuitmesh.geo

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Visible-pass prediction — which satellites will actually be *seen* overhead, not just pass. A pass
 * is visible only when three things line up: the pass climbs high enough, your sky is dark (the Sun
 * is below the horizon), and the satellite itself is still catching sunlight (not in Earth's
 * shadow). Builds on the validated [Sgp4] core.
 */

/** Low-precision Sun position, good to ~0.01° — enough for twilight and the shadow test. */
object SunPos {
    /** Sun's unit direction in ECI (equatorial) coordinates at [ms]. */
    fun eci(ms: Long): DoubleArray {
        val d = Sgp4.julianDate(ms) - 2451545.0
        val l = Math.toRadians((280.460 + 0.9856474 * d) % 360.0)
        val g = Math.toRadians((357.528 + 0.9856003 * d) % 360.0)
        val lambda = l + Math.toRadians(1.915) * sin(g) + Math.toRadians(0.020) * sin(2 * g)
        val eps = Math.toRadians(23.439 - 0.0000004 * d)
        return doubleArrayOf(cos(lambda), cos(eps) * sin(lambda), sin(eps) * sin(lambda))
    }

    /** The Sun's elevation (deg) above an observer's horizon at [ms] — negative means night/twilight. */
    fun altDeg(latDeg: Double, lonDeg: Double, ms: Long): Double {
        val e = eci(ms)
        val ra = atan2(e[1], e[0]); val dec = asin(e[2].coerceIn(-1.0, 1.0))
        val lst = Sgp4.gmst(Sgp4.julianDate(ms)) + Math.toRadians(lonDeg)
        val ha = lst - ra
        val lat = Math.toRadians(latDeg)
        val sinAlt = sin(dec) * sin(lat) + cos(dec) * cos(lat) * cos(ha)
        return Math.toDegrees(asin(sinAlt.coerceIn(-1.0, 1.0)))
    }
}

/** True if a satellite at TEME [x,y,z] km is in sunlight (not inside Earth's cylindrical shadow). */
fun isSunlit(satKm: DoubleArray, ms: Long): Boolean {
    val sun = SunPos.eci(ms)
    val proj = satKm[0] * sun[0] + satKm[1] * sun[1] + satKm[2] * sun[2]
    if (proj > 0) return true   // on the sunward side of Earth
    val mag2 = satKm[0] * satKm[0] + satKm[1] * satKm[1] + satKm[2] * satKm[2]
    val perp = sqrt((mag2 - proj * proj).coerceAtLeast(0.0))
    return perp > 6378.137      // misses the shadow cylinder
}

/** A pass that will actually be visible: bright satellite, dark sky, sunlit bird. */
data class VisiblePass(
    val name: String,
    val aosMs: Long, val peakMs: Long, val losMs: Long,
    val peakElDeg: Double, val aosAzDeg: Double, val losAzDeg: Double,
)

/**
 * Find the visible passes of [tles] over an observer across the next [windowHrs]. For each satellite
 * we walk its upcoming passes; one counts only if it peaks above [minPeakElDeg], the Sun is at least
 * 6° below the observer's horizon at culmination, and the satellite is sunlit then.
 */
fun visiblePasses(
    tles: List<Tle>, latDeg: Double, lonDeg: Double, fromMs: Long,
    windowHrs: Int = 36, minPeakElDeg: Double = 15.0,
): List<VisiblePass> {
    val out = ArrayList<VisiblePass>()
    val end = fromMs + windowHrs * 3_600_000L
    for (t in tles) {
        if (t.isDeepSpace) continue
        var from = fromMs
        var guard = 0
        while (from < end && guard < 8) {
            guard++
            val p = Sgp4.nextPass(t, latDeg, lonDeg, 0.0, from, horizonDeg = 0.0, windowHrs = 12) ?: break
            if (p.aosMs > end) break
            from = p.losMs + 60_000L
            if (p.peakElDeg < minPeakElDeg) continue
            if (SunPos.altDeg(latDeg, lonDeg, p.peakMs) > -6.0) continue   // sky not dark enough
            val tsince = (Sgp4.julianDate(p.peakMs) - Sgp4.epochJd(t)) * 1440.0
            val r = runCatching { Sgp4.propagate(t, tsince) }.getOrNull() ?: continue
            if (!isSunlit(r, p.peakMs)) continue
            out.add(VisiblePass(t.name, p.aosMs, p.peakMs, p.losMs, p.peakElDeg, p.aosAzDeg, p.losAzDeg))
        }
    }
    return out.sortedBy { it.aosMs }
}
