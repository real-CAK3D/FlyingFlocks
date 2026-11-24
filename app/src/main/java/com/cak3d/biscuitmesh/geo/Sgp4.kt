package com.cak3d.biscuitmesh.geo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Near-Earth SGP4 orbit propagator — turns a TLE into a satellite's real position now.
 *
 * This is a direct transliteration of a compact SGP4 (Spacetrack Report #3, WGS-72) that was
 * validated in Python against the reference `sgp4` library (0.0000 km error at 0/45/90/360 min)
 * and against wheretheiss.at's live ISS position (~8 km, i.e. the ~1 s sampling gap at 7.6 km/s).
 * It covers near-Earth orbits (period < ~225 min): LEO stations, Starlink, most "visual" sats.
 * Deep-space (SDP4, for GEO/HEO) is not implemented — [Tle.isDeepSpace] flags those to skip.
 */

/** A two-line element set. */
data class Tle(val name: String, val line1: String, val line2: String) {
    val noradId: String get() = line2.substring(2, 7).trim()
    /** Mean motion, revolutions per day. */
    val meanMotion: Double get() = line2.substring(52, 63).trim().toDouble()
    val eccentricity: Double get() = ("0." + line2.substring(26, 33).trim()).toDouble()
    val inclinationDeg: Double get() = line2.substring(8, 16).trim().toDouble()
    /** Deep-space regime (period >= 225 min) — SGP4 near-Earth is not valid there. */
    val isDeepSpace: Boolean get() = 1440.0 / meanMotion >= 225.0
}

enum class OrbitClass { LEO, MEO, GEO, HEO }

/** Observer look-angles to a satellite: elevation & azimuth (deg) and slant range (km). */
data class LookAngle(val elDeg: Double, val azDeg: Double, val rangeKm: Double)

/**
 * A satellite's next pass over an observer: acquisition-of-signal (rise), culmination (peak) and
 * loss-of-signal (set) times in epoch millis, the peak elevation, and the rise/set azimuths.
 * [inProgress] is true when the pass is already overhead at the time the prediction was made.
 */
data class PassPrediction(
    val aosMs: Long,
    val peakMs: Long,
    val losMs: Long,
    val peakElDeg: Double,
    val aosAzDeg: Double,
    val losAzDeg: Double,
    val inProgress: Boolean,
)

/** Geodetic sub-point + altitude of a satellite, with its orbit class. */
data class SatGeo(
    val tle: Tle,
    val latDeg: Double,
    val lonDeg: Double,
    val altKm: Double,
    val orbitClass: OrbitClass,
)

object Sgp4 {
    private const val CK2 = 5.413080e-4
    private const val CK4 = 0.62098875e-6
    private const val XKMPER = 6378.135
    private const val XKE = 0.743669161e-1
    private const val QOMS2T = 1.88027916e-9
    private const val S = 1.01222928
    private const val XJ3 = -0.253881e-5
    private const val XMNPDA = 1440.0
    private const val AE = 1.0
    private const val TOTHRD = 2.0 / 3.0
    private const val TWOPI = 2.0 * PI
    private const val DE2RA = PI / 180.0

    private class Elements(
        val bstar: Double, val xincl: Double, val xnodeo: Double,
        val eo: Double, val omegao: Double, val xmo: Double, val xno: Double,
    )

    private fun parse(t: Tle): Elements {
        val l1 = t.line1; val l2 = t.line2
        val bs = l1.substring(53, 61)
        val mant = ("0." + bs.substring(1, 6).trim()).toDouble()
        val exp = bs.substring(6, 8).trim().toInt()
        val sign = if (bs[0] == '-') -1.0 else 1.0
        val bstar = sign * mant * 10.0.pow(exp.toDouble())
        return Elements(
            bstar = bstar,
            xincl = l2.substring(8, 16).trim().toDouble() * DE2RA,
            xnodeo = l2.substring(17, 25).trim().toDouble() * DE2RA,
            eo = ("0." + l2.substring(26, 33).trim()).toDouble(),
            omegao = l2.substring(34, 42).trim().toDouble() * DE2RA,
            xmo = l2.substring(43, 51).trim().toDouble() * DE2RA,
            xno = l2.substring(52, 63).trim().toDouble() * TWOPI / XMNPDA,
        )
    }

    /** Propagate [t] to [tsinceMin] minutes past its epoch → TEME position (km) as [x, y, z]. */
    fun propagate(t: Tle, tsinceMin: Double): DoubleArray {
        val el = parse(t)
        val xno = el.xno; val eo = el.eo; val xincl = el.xincl; val bstar = el.bstar
        val xnodeo = el.xnodeo; val omegao = el.omegao; val xmo = el.xmo

        val a1 = (XKE / xno).pow(TOTHRD)
        val cosio = cos(xincl); val theta2 = cosio * cosio; val x3thm1 = 3 * theta2 - 1
        val eosq = eo * eo; val betao2 = 1 - eosq; val betao = sqrt(betao2)
        val del1 = 1.5 * CK2 * x3thm1 / (a1 * a1 * betao * betao2)
        val ao = a1 * (1 - del1 * (0.5 * TOTHRD + del1 * (1 + 134.0 / 81.0 * del1)))
        val delo = 1.5 * CK2 * x3thm1 / (ao * ao * betao * betao2)
        val xnodp = xno / (1 + delo); val aodp = ao / (1 - delo)
        val isimp = (aodp * (1 - eo) / AE) < (220.0 / XKMPER + AE)
        var s4 = S; var qoms24 = QOMS2T
        val perige = (aodp * (1 - eo) - AE) * XKMPER
        if (perige < 156) {
            s4 = perige - 78
            if (perige <= 98) s4 = 20.0
            qoms24 = ((120 - s4) * AE / XKMPER).pow(4.0)
            s4 = s4 / XKMPER + AE
        }
        val pinvsq = 1.0 / (aodp * aodp * betao2 * betao2)
        val tsi = 1.0 / (aodp - s4); val eta = aodp * eo * tsi; val etasq = eta * eta; val eeta = eo * eta
        val psisq = abs(1 - etasq); val coef = qoms24 * tsi.pow(4.0); val coef1 = coef / psisq.pow(3.5)
        val c2 = coef1 * xnodp * (aodp * (1 + 1.5 * etasq + eeta * (4 + etasq)) +
            0.75 * CK2 * tsi / psisq * x3thm1 * (8 + 3 * etasq * (8 + etasq)))
        val c1 = bstar * c2
        val sinio = sin(xincl); val a3ovk2 = -XJ3 / CK2 * AE.pow(3.0)
        val c3 = coef * tsi * a3ovk2 * xnodp * AE * sinio / eo
        val x1mth2 = 1 - theta2
        val c4 = 2 * xnodp * coef1 * aodp * betao2 * (eta * (2 + 0.5 * etasq) + eo * (0.5 + 2 * etasq) -
            2 * CK2 * tsi / (aodp * psisq) * (-3 * x3thm1 * (1 - 2 * eeta + etasq * (1.5 - 0.5 * eeta)) +
                0.75 * x1mth2 * (2 * etasq - eeta * (1 + etasq)) * cos(2 * omegao)))
        val c5 = 2 * coef1 * aodp * betao2 * (1 + 2.75 * (etasq + eeta) + eeta * etasq)
        val theta4 = theta2 * theta2
        val temp1i = 3 * CK2 * pinvsq * xnodp; val temp2i = temp1i * CK2 * pinvsq
        val temp3i = 1.25 * CK4 * pinvsq * pinvsq * xnodp
        val xmdot = xnodp + 0.5 * temp1i * betao * x3thm1 + 0.0625 * temp2i * betao * (13 - 78 * theta2 + 137 * theta4)
        val x1m5th = 1 - 5 * theta2
        val omgdot = -0.5 * temp1i * x1m5th + 0.0625 * temp2i * (7 - 114 * theta2 + 395 * theta4) +
            temp3i * (3 - 36 * theta2 + 49 * theta4)
        val xhdot1 = -temp1i * cosio
        val xnodot = xhdot1 + (0.5 * temp2i * (4 - 19 * theta2) + 2 * temp3i * (3 - 7 * theta2)) * cosio
        val omgcof = bstar * c3 * cos(omegao); val xmcof = -TOTHRD * coef * bstar * AE / eeta
        val xnodcf = 3.5 * betao2 * xhdot1 * c1; val t2cof = 1.5 * c1
        val xlcof = 0.125 * a3ovk2 * sinio * (3 + 5 * cosio) / (1 + cosio); val aycof = 0.25 * a3ovk2 * sinio
        val delmo = (1 + eta * cos(xmo)).pow(3.0); val sinmo = sin(xmo); val x7thm1 = 7 * theta2 - 1

        var d2 = 0.0; var d3 = 0.0; var d4 = 0.0; var t3cof = 0.0; var t4cof = 0.0; var t5cof = 0.0
        if (!isimp) {
            val c1sq = c1 * c1; d2 = 4 * aodp * tsi * c1sq; val temp = d2 * tsi * c1 / 3.0
            d3 = (17 * aodp + s4) * temp; d4 = 0.5 * temp * aodp * tsi * (221 * aodp + 31 * s4) * c1
            t3cof = d2 + 2 * c1sq; t4cof = 0.25 * (3 * d3 + c1 * (12 * d2 + 10 * c1sq))
            t5cof = 0.2 * (3 * d4 + 12 * c1 * d3 + 6 * d2 * d2 + 15 * c1sq * (2 * d2 + c1sq))
        }

        // --- propagate ---
        val xmdf = xmo + xmdot * tsinceMin; val omgadf = omegao + omgdot * tsinceMin
        val xnoddf = xnodeo + xnodot * tsinceMin
        var omega = omgadf; var xmp = xmdf; val tsq = tsinceMin * tsinceMin
        val xnode = xnoddf + xnodcf * tsq
        var tempa = 1 - c1 * tsinceMin; var tempe = bstar * c4 * tsinceMin; var templ = t2cof * tsq
        if (!isimp) {
            val delomg = omgcof * tsinceMin; val delm = xmcof * ((1 + eta * cos(xmdf)).pow(3.0) - delmo)
            val temp = delomg + delm; xmp = xmdf + temp; omega = omgadf - temp
            val tcube = tsq * tsinceMin; val tfour = tsinceMin * tcube
            tempa = tempa - d2 * tsq - d3 * tcube - d4 * tfour
            tempe += bstar * c5 * (sin(xmp) - sinmo)
            templ += t3cof * tcube + tfour * (t4cof + tsinceMin * t5cof)
        }
        val a = aodp * tempa * tempa; val e = eo - tempe
        val xl = xmp + omega + xnode + xnodp * templ
        val xn = XKE / a.pow(1.5)
        val axn = e * cos(omega); var temp = 1.0 / (a * (1 - e * e))
        val xll = temp * xlcof * axn; val aynl = temp * aycof
        val xlt = xl + xll; val ayn = e * sin(omega) + aynl
        val capu = (xlt - xnode) % TWOPI
        var epw = capu; var temp2 = capu
        var sinepw = 0.0; var cosepw = 0.0; var temp3 = 0.0; var temp4 = 0.0; var temp5 = 0.0; var temp6 = 0.0
        for (i in 0 until 10) {
            sinepw = sin(temp2); cosepw = cos(temp2)
            temp3 = axn * sinepw; temp4 = ayn * cosepw; temp5 = axn * cosepw; temp6 = ayn * sinepw
            epw = (capu - temp4 + temp3 - temp2) / (1 - temp5 - temp6) + temp2
            if (abs(epw - temp2) <= 1e-12) break
            temp2 = epw
        }
        val ecose = temp5 + temp6; val esine = temp3 - temp4; val elsq = axn * axn + ayn * ayn
        temp = 1 - elsq; val pl = a * temp; val r = a * (1 - ecose); var temp1 = 1.0 / r
        temp2 = a * temp1; val betal = sqrt(temp); temp3 = 1.0 / (1 + betal)
        val cosu = temp2 * (cosepw - axn + ayn * esine * temp3)
        val sinu = temp2 * (sinepw - ayn - axn * esine * temp3)
        val u = atan2(sinu, cosu); val sin2u = 2 * sinu * cosu; val cos2u = 2 * cosu * cosu - 1
        temp = 1.0 / pl; temp1 = CK2 * temp; temp2 = temp1 * temp
        val rk = r * (1 - 1.5 * temp2 * betal * x3thm1) + 0.5 * temp1 * x1mth2 * cos2u
        val uk = u - 0.25 * temp2 * x7thm1 * sin2u
        val xnodek = xnode + 1.5 * temp2 * cosio * sin2u
        val xinck = xincl + 1.5 * temp2 * cosio * sinio * cos2u
        val sinuk = sin(uk); val cosuk = cos(uk); val sinik = sin(xinck); val cosik = cos(xinck)
        val sinnok = sin(xnodek); val cosnok = cos(xnodek)
        val xmx = -sinnok * cosik; val xmy = cosnok * cosik
        val ux = xmx * sinuk + cosnok * cosuk; val uy = xmy * sinuk + sinnok * cosuk; val uz = sinik * sinuk
        return doubleArrayOf(rk * ux * XKMPER, rk * uy * XKMPER, rk * uz * XKMPER)
    }

    /** Greenwich mean sidereal time (radians) for a Julian date. */
    fun gmst(jd: Double): Double {
        val t = (jd - 2451545.0) / 36525.0
        var temp = -6.2e-6 * t * t * t + 0.093104 * t * t +
            (876600.0 * 3600 + 8640184.812866) * t + 67310.54841
        temp = (temp * DE2RA / 240.0) % TWOPI
        if (temp < 0) temp += TWOPI
        return temp
    }

    /** Julian date (UT1≈UTC) from epoch millis. */
    fun julianDate(ms: Long): Double = ms / 86400000.0 + 2440587.5

    /** TEME [x,y,z] km at Julian date [jd] → geodetic latitude, longitude (deg) and altitude (km). */
    fun temeToGeodetic(x: Double, y: Double, z: Double, jd: Double): Triple<Double, Double, Double> {
        val g = gmst(jd)
        val xf = x * cos(g) + y * sin(g); val yf = -x * sin(g) + y * cos(g); val zf = z
        val lon = atan2(yf, xf) / DE2RA
        val a = 6378.137; val f = 1 / 298.257223563; val e2 = f * (2 - f)
        val p = sqrt(xf * xf + yf * yf); var lat = atan2(zf, p * (1 - e2))
        repeat(5) {
            val n = a / sqrt(1 - e2 * sin(lat) * sin(lat))
            lat = atan2(zf + e2 * n * sin(lat), p)
        }
        val n = a / sqrt(1 - e2 * sin(lat) * sin(lat)); val alt = p / cos(lat) - n
        return Triple(lat / DE2RA, lon, alt)
    }

    /** Propagate to [nowMs] and return the sub-point / class, or null for deep-space / errors. */
    fun geoNow(t: Tle, nowMs: Long): SatGeo? = runCatching {
        if (t.isDeepSpace) return@runCatching null
        val tsince = (julianDate(nowMs) - epochJd(t)) * 1440.0
        val r = propagate(t, tsince)
        val (lat, lon, alt) = temeToGeodetic(r[0], r[1], r[2], julianDate(nowMs))
        if (alt.isNaN() || alt < -100 || alt > 500000) return@runCatching null
        SatGeo(t, lat, lon, alt, classify(t))
    }.getOrNull()

    /** Julian date of a TLE's epoch (year + fractional day of year). Matches the validated
     *  `jday(year,1,1,0,0,0) + (doy-1)` with integer division, giving 2460310.5 for 2024-01-01. */
    fun epochJd(t: Tle): Double {
        val yy = t.line1.substring(18, 20).trim().toInt()
        val year = if (yy < 57) 2000 + yy else 1900 + yy
        val doy = t.line1.substring(20, 32).trim().toDouble()
        val jan1 = 367 * year - (7 * year) / 4 + 30 + 1 + 1721013.5
        return jan1 + (doy - 1.0)
    }

    fun classify(t: Tle): OrbitClass {
        val n = t.meanMotion; val e = t.eccentricity
        return when {
            e > 0.25 -> OrbitClass.HEO
            n >= 11.25 -> OrbitClass.LEO          // period < ~128 min
            n <= 1.1 -> OrbitClass.GEO            // ~geosynchronous
            else -> OrbitClass.MEO
        }
    }

    private const val OBS_A = 6378.137                 // WGS-84 equatorial radius (km)
    private const val OBS_F = 1.0 / 298.257223563      // WGS-84 flattening

    /**
     * Topocentric look-angles from an observer (geodetic lat/lon deg, altitude km) to satellite
     * [t] at [nowMs]. Returns elevation above the horizon, azimuth from true north (clockwise) and
     * slant range. Null for deep-space TLEs or propagation errors.
     */
    fun lookAngle(
        t: Tle, obsLatDeg: Double, obsLonDeg: Double, obsAltKm: Double, nowMs: Long,
    ): LookAngle? = runCatching {
        if (t.isDeepSpace) return@runCatching null
        val jd = julianDate(nowMs)
        val tsince = (jd - epochJd(t)) * 1440.0
        val r = propagate(t, tsince)
        val g = gmst(jd)
        // TEME -> Earth-fixed (ECEF).
        val sx = r[0] * cos(g) + r[1] * sin(g)
        val sy = -r[0] * sin(g) + r[1] * cos(g)
        val sz = r[2]
        // Observer ECEF (WGS-84 geodetic).
        val lat = obsLatDeg * DE2RA; val lon = obsLonDeg * DE2RA
        val e2 = OBS_F * (2 - OBS_F)
        val sinLat = sin(lat); val cosLat = cos(lat); val sinLon = sin(lon); val cosLon = cos(lon)
        val n = OBS_A / sqrt(1 - e2 * sinLat * sinLat)
        val ox = (n + obsAltKm) * cosLat * cosLon
        val oy = (n + obsAltKm) * cosLat * sinLon
        val oz = (n * (1 - e2) + obsAltKm) * sinLat
        // Range vector, observer -> satellite, in ECEF then SEZ (south/east/zenith).
        val rx = sx - ox; val ry = sy - oy; val rz = sz - oz
        val south = sinLat * cosLon * rx + sinLat * sinLon * ry - cosLat * rz
        val east = -sinLon * rx + cosLon * ry
        val zen = cosLat * cosLon * rx + cosLat * sinLon * ry + sinLat * rz
        val range = sqrt(rx * rx + ry * ry + rz * rz)
        if (range <= 0) return@runCatching null
        val el = asin((zen / range).coerceIn(-1.0, 1.0)) / DE2RA
        var az = atan2(east, -south) / DE2RA
        if (az < 0) az += 360.0
        LookAngle(el, az, range)
    }.getOrNull()

    /**
     * The next pass of satellite [t] over an observer, searching forward from [fromMs]. Coarse-steps
     * one minute at a time to bracket the rise above [horizonDeg], bisects to pin the rise, then
     * walks the pass to find the culmination and set. Returns null when the satellite does not clear
     * the horizon within [windowHrs] (e.g. inclination below the observer's latitude), or for
     * deep-space TLEs. If the satellite is already up at [fromMs] the pass is returned [inProgress].
     */
    fun nextPass(
        t: Tle, obsLatDeg: Double, obsLonDeg: Double, obsAltKm: Double, fromMs: Long,
        horizonDeg: Double = 0.0, windowHrs: Int = 8,
    ): PassPrediction? {
        if (t.isDeepSpace) return null
        fun el(ms: Long): Double = lookAngle(t, obsLatDeg, obsLonDeg, obsAltKm, ms)?.elDeg ?: -90.0
        val coarse = 60_000L
        val fine = 10_000L
        val end = fromMs + windowHrs * 3_600_000L

        val startEl = el(fromMs)
        val inProgress = startEl >= horizonDeg
        val aos: Long
        if (inProgress) {
            aos = fromMs
        } else {
            var prev = startEl
            var tms = fromMs + coarse
            var rise = -1L
            while (tms <= end) {
                val cur = el(tms)
                if (prev < horizonDeg && cur >= horizonDeg) {
                    var lo = tms - coarse; var hi = tms
                    repeat(20) { val mid = (lo + hi) / 2; if (el(mid) < horizonDeg) lo = mid else hi = mid }
                    rise = hi; break
                }
                prev = cur; tms += coarse
            }
            if (rise < 0) return null
            aos = rise
        }

        // Walk from AOS to find culmination and set.
        var peakMs = aos; var peakEl = el(aos)
        var los = end
        var tms = aos + fine
        val passEnd = aos + 30 * 60_000L   // no LEO pass lasts longer than ~30 min
        while (tms <= passEnd) {
            val cur = el(tms)
            if (cur > peakEl) { peakEl = cur; peakMs = tms }
            if (cur < horizonDeg) {
                var lo = tms - fine; var hi = tms
                repeat(16) { val mid = (lo + hi) / 2; if (el(mid) >= horizonDeg) lo = mid else hi = mid }
                los = lo; break
            }
            tms += fine
        }
        val aosAz = lookAngle(t, obsLatDeg, obsLonDeg, obsAltKm, aos)?.azDeg ?: 0.0
        val losAz = lookAngle(t, obsLatDeg, obsLonDeg, obsAltKm, los)?.azDeg ?: 0.0
        return PassPrediction(aos, peakMs, los, peakEl, aosAz, losAz, inProgress)
    }
}
