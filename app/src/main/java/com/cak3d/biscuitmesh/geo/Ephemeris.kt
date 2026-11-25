package com.cak3d.biscuitmesh.geo

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Low-precision ephemeris — Sun, Moon and the five naked-eye planets, plus a bright-star
 * catalog with constellation lines. Everything comes back as horizontal coordinates
 * (azimuth from true north, elevation above the horizon) for the observer's lat/lon and
 * the given instant, which is exactly what the Sky-AR overlay needs to place a marker.
 *
 * Orbital elements and the solve follow Paul Schlyter's "computing planetary positions"
 * (good to a fraction of a degree for the planets, ~a degree for the Moon) — more than
 * enough to point a phone at the right patch of sky.
 */

data class CelestialBody(
    val name: String,
    val az: Float,      // degrees, 0 = N, 90 = E
    val el: Float,      // degrees above horizon (negative = below)
    val kind: BodyKind,
    val mag: Float,     // apparent magnitude (smaller = brighter)
)

enum class BodyKind { SUN, MOON, PLANET }

/** A catalog star already projected to the observer's sky. */
data class SkyStar(val az: Float, val el: Float, val mag: Float, val name: String)

private const val RAD = Math.PI / 180.0
private const val DEG = 180.0 / Math.PI

private fun rev(x: Double): Double = ((x % 360.0) + 360.0) % 360.0

/** Days since the 2000 Jan 0.0 epoch Schlyter uses, including fractional UT. */
private fun dayNumber(timeMs: Long): Double {
    val secs = timeMs / 1000.0
    // Unix epoch (1970-01-01) to Schlyter epoch (2000-01-00 = 1999-12-31 00:00 UT)
    // is 10957 days. Schlyter d for the Unix epoch is -10957.5 at 00:00 UT 1970-01-01.
    return secs / 86400.0 - 10957.5
}

private data class Elements(
    val n: Double, val i: Double, val w: Double,
    val a: Double, val e: Double, val m: Double,
)

/** Rectangular geocentric-equatorial unit-ish vector + distance, then to RA/Dec. */
private data class EquatorialCoord(val ra: Double, val dec: Double)

private fun eclipticToEquatorial(xe: Double, ye: Double, ze: Double, oblecl: Double): EquatorialCoord {
    val xeq = xe
    val yeq = ye * cos(oblecl * RAD) - ze * sin(oblecl * RAD)
    val zeq = ye * sin(oblecl * RAD) + ze * cos(oblecl * RAD)
    val ra = rev(atan2(yeq, xeq) * DEG)
    val dec = atan2(zeq, sqrt(xeq * xeq + yeq * yeq)) * DEG
    return EquatorialCoord(ra, dec)
}

/** Horizontal coordinates from RA/Dec for the observer and local sidereal time. */
private fun toHorizontal(ra: Double, dec: Double, lat: Double, lst: Double): Pair<Float, Float> {
    val ha = rev(lst - ra)
    val x = cos(ha * RAD) * cos(dec * RAD)
    val y = sin(ha * RAD) * cos(dec * RAD)
    val z = sin(dec * RAD)
    val xhor = x * sin(lat * RAD) - z * cos(lat * RAD)
    val yhor = y
    val zhor = x * cos(lat * RAD) + z * sin(lat * RAD)
    val az = rev(atan2(yhor, xhor) * DEG + 180.0)
    val el = asin(zhor) * DEG
    return az.toFloat() to el.toFloat()
}

/** Solve Kepler's equation (iterative; fine for the Moon's eccentricity). */
private fun eccentricAnomaly(m: Double, e: Double): Double {
    var ea = m + e * DEG * sin(m * RAD) * (1 + e * cos(m * RAD))
    repeat(5) {
        ea -= (ea - e * DEG * sin(ea * RAD) - m) / (1 - e * cos(ea * RAD))
    }
    return ea
}

/** Main entry: Sun, Moon, Mercury…Saturn as az/el for the observer. */
fun celestialBodies(lat: Double, lon: Double, timeMs: Long): List<CelestialBody> {
    val d = dayNumber(timeMs)
    val oblecl = 23.4393 - 3.563e-7 * d

    // --- Sun ---
    val wS = 282.9404 + 4.70935e-5 * d
    val eS = 0.016709 - 1.151e-9 * d
    val mS = rev(356.0470 + 0.9856002585 * d)
    val eaS = eccentricAnomaly(mS, eS)
    val xvS = cos(eaS * RAD) - eS
    val yvS = sqrt(1 - eS * eS) * sin(eaS * RAD)
    val vS = rev(atan2(yvS, xvS) * DEG)
    val rS = sqrt(xvS * xvS + yvS * yvS)
    val lonSun = rev(vS + wS)
    val xs = rS * cos(lonSun * RAD)
    val ys = rS * sin(lonSun * RAD)

    // Local sidereal time (degrees).
    val ls = rev(wS + mS)
    val gmst0 = rev(ls + 180.0)
    val ut = ((timeMs / 1000.0) % 86400.0) / 3600.0  // hours
    val lst = rev(gmst0 + ut * 15.0 + lon)

    val out = ArrayList<CelestialBody>(8)

    // Sun to equatorial/horizontal.
    run {
        val eq = eclipticToEquatorial(xs, ys, 0.0, oblecl)
        val (az, el) = toHorizontal(eq.ra, eq.dec, lat, lst)
        out += CelestialBody("Sun", az, el, BodyKind.SUN, -26.7f)
    }

    // --- Moon ---
    run {
        val n = rev(125.1228 - 0.0529538083 * d)
        val i = 5.1454
        val w = rev(318.0634 + 0.1643573223 * d)
        val a = 60.2666
        val e = 0.054900
        val m = rev(115.3654 + 13.0649929509 * d)
        val ea = eccentricAnomaly(m, e)
        val xv = a * (cos(ea * RAD) - e)
        val yv = a * (sqrt(1 - e * e) * sin(ea * RAD))
        val v = rev(atan2(yv, xv) * DEG)
        val r = sqrt(xv * xv + yv * yv)
        var xec = r * (cos(n * RAD) * cos((v + w) * RAD) - sin(n * RAD) * sin((v + w) * RAD) * cos(i * RAD))
        var yec = r * (sin(n * RAD) * cos((v + w) * RAD) + cos(n * RAD) * sin((v + w) * RAD) * cos(i * RAD))
        var zec = r * sin((v + w) * RAD) * sin(i * RAD)
        // Main perturbations (Schlyter): bring the Moon within ~0.25°.
        var lonEc = rev(atan2(yec, xec) * DEG)
        var latEc = atan2(zec, sqrt(xec * xec + yec * yec)) * DEG
        val lsM = rev(wS + mS)           // sun mean longitude
        val lmM = rev(n + w + m)         // moon mean longitude
        val dM = rev(lmM - lsM)          // mean elongation
        val fM = rev(lmM - n)            // argument of latitude
        lonEc += (-1.274 * sin((m - 2 * dM) * RAD)
            + 0.658 * sin(2 * dM * RAD)
            - 0.186 * sin(mS * RAD)
            - 0.059 * sin((2 * m - 2 * dM) * RAD)
            - 0.057 * sin((m - 2 * dM + mS) * RAD)
            + 0.053 * sin((m + 2 * dM) * RAD)
            + 0.046 * sin((2 * dM - mS) * RAD)
            + 0.041 * sin((m - mS) * RAD)
            - 0.035 * sin(dM * RAD)
            - 0.031 * sin((m + mS) * RAD)
            - 0.015 * sin((2 * fM - 2 * dM) * RAD)
            + 0.011 * sin((m - 4 * dM) * RAD))
        latEc += (-0.173 * sin((fM - 2 * dM) * RAD)
            - 0.055 * sin((m - fM - 2 * dM) * RAD)
            - 0.046 * sin((m + fM - 2 * dM) * RAD)
            + 0.033 * sin((fM + 2 * dM) * RAD)
            + 0.017 * sin((2 * m + fM) * RAD))
        val rg = r
        xec = rg * cos(lonEc * RAD) * cos(latEc * RAD)
        yec = rg * sin(lonEc * RAD) * cos(latEc * RAD)
        zec = rg * sin(latEc * RAD)
        val eq = eclipticToEquatorial(xec, yec, zec, oblecl)
        val (az, el) = toHorizontal(eq.ra, eq.dec, lat, lst)
        out += CelestialBody("Moon", az, el, BodyKind.MOON, -12.0f)
    }

    // --- Planets ---
    data class Planet(val name: String, val el: Elements, val mag: Float)
    val planets = listOf(
        Planet("Mercury", Elements(
            rev(48.3313 + 3.24587e-5 * d), 7.0047 + 5.00e-8 * d, rev(29.1241 + 1.01444e-5 * d),
            0.387098, 0.205635 + 5.59e-10 * d, rev(168.6562 + 4.0923344368 * d)), -0.4f),
        Planet("Venus", Elements(
            rev(76.6799 + 2.46590e-5 * d), 3.3946 + 2.75e-8 * d, rev(54.8910 + 1.38374e-5 * d),
            0.723330, 0.006773 - 1.302e-9 * d, rev(48.0052 + 1.6021302244 * d)), -4.4f),
        Planet("Mars", Elements(
            rev(49.5574 + 2.11081e-5 * d), 1.8497 - 1.78e-8 * d, rev(286.5016 + 2.92961e-5 * d),
            1.523688, 0.093405 + 2.516e-9 * d, rev(18.6021 + 0.5240207766 * d)), 0.7f),
        Planet("Jupiter", Elements(
            rev(100.4542 + 2.76854e-5 * d), 1.3030 - 1.557e-7 * d, rev(273.8777 + 1.64505e-5 * d),
            5.20256, 0.048498 + 4.469e-9 * d, rev(19.8950 + 0.0830853001 * d)), -2.2f),
        Planet("Saturn", Elements(
            rev(113.6634 + 2.38980e-5 * d), 2.4886 - 1.081e-7 * d, rev(339.3939 + 2.97661e-5 * d),
            9.55475, 0.055546 - 9.499e-9 * d, rev(316.9670 + 0.0334442282 * d)), 0.7f),
    )
    planets.forEach { p ->
        val el = p.el
        val ea = eccentricAnomaly(el.m, el.e)
        val xv = el.a * (cos(ea * RAD) - el.e)
        val yv = el.a * (sqrt(1 - el.e * el.e) * sin(ea * RAD))
        val v = rev(atan2(yv, xv) * DEG)
        val r = sqrt(xv * xv + yv * yv)
        val xh = r * (cos(el.n * RAD) * cos((v + el.w) * RAD) - sin(el.n * RAD) * sin((v + el.w) * RAD) * cos(el.i * RAD))
        val yh = r * (sin(el.n * RAD) * cos((v + el.w) * RAD) + cos(el.n * RAD) * sin((v + el.w) * RAD) * cos(el.i * RAD))
        val zh = r * sin((v + el.w) * RAD) * sin(el.i * RAD)
        // Geocentric = heliocentric planet + heliocentric Earth (= -Sun's geocentric).
        val xg = xh + xs
        val yg = yh + ys
        val zg = zh
        val eq = eclipticToEquatorial(xg, yg, zg, oblecl)
        val (az, elh) = toHorizontal(eq.ra, eq.dec, lat, lst)
        out += CelestialBody(p.name, az, elh, BodyKind.PLANET, p.mag)
    }
    return out
}

/**
 * Bright-star catalog → horizontal coordinates. Returns the projected stars (indexed so
 * the overlay can draw constellation lines against [constellationLines]).
 */
fun starField(lat: Double, lon: Double, timeMs: Long): List<SkyStar> {
    val d = dayNumber(timeMs)
    val wS = 282.9404 + 4.70935e-5 * d
    val mS = rev(356.0470 + 0.9856002585 * d)
    val ls = rev(wS + mS)
    val gmst0 = rev(ls + 180.0)
    val ut = ((timeMs / 1000.0) % 86400.0) / 3600.0
    val lst = rev(gmst0 + ut * 15.0 + lon)
    return BRIGHT_STARS.map { s ->
        val (az, el) = toHorizontal(s.raDeg, s.decDeg, lat, lst)
        SkyStar(az, el, s.mag, s.name)
    }
}

/** RA (degrees), Dec (degrees), magnitude, name. Ordered so constellation lines can index them. */
private data class CatalogStar(val raDeg: Double, val decDeg: Double, val mag: Float, val name: String)

// ~70 of the brightest / most recognizable stars. RA converted to degrees (h*15).
private val BRIGHT_STARS = listOf(
    // 0-6 Orion
    CatalogStar(88.793, 7.407, 0.45f, "Betelgeuse"),   // 0
    CatalogStar(78.634, -8.202, 0.18f, "Rigel"),        // 1
    CatalogStar(81.283, 6.350, 1.64f, "Bellatrix"),     // 2
    CatalogStar(83.002, -0.299, 2.23f, "Mintaka"),      // 3
    CatalogStar(84.053, -1.202, 1.69f, "Alnilam"),      // 4
    CatalogStar(85.190, -1.943, 1.74f, "Alnitak"),      // 5
    CatalogStar(86.939, -9.670, 2.07f, "Saiph"),        // 6
    // 7-13 Big Dipper (Ursa Major)
    CatalogStar(165.932, 61.751, 1.79f, "Dubhe"),       // 7
    CatalogStar(165.460, 56.382, 2.37f, "Merak"),       // 8
    CatalogStar(178.458, 53.695, 2.44f, "Phecda"),      // 9
    CatalogStar(183.857, 57.033, 3.31f, "Megrez"),      // 10
    CatalogStar(193.507, 55.960, 1.77f, "Alioth"),      // 11
    CatalogStar(200.981, 54.926, 2.27f, "Mizar"),       // 12
    CatalogStar(206.885, 49.313, 1.86f, "Alkaid"),      // 13
    // 14-18 Cassiopeia
    CatalogStar(10.127, 56.537, 2.24f, "Caph"),         // 14
    CatalogStar(14.177, 60.717, 2.15f, "Schedar"),      // 15
    CatalogStar(21.454, 60.235, 2.68f, "Gamma Cas"),    // 16
    CatalogStar(28.599, 63.670, 2.68f, "Ruchbah"),      // 17
    CatalogStar(37.041, 63.670, 3.38f, "Segin"),        // 18
    // 19-22 Summer Triangle + Lyra/Cygnus/Aquila
    CatalogStar(279.234, 38.784, 0.03f, "Vega"),        // 19
    CatalogStar(310.358, 45.280, 1.25f, "Deneb"),       // 20
    CatalogStar(297.696, 8.868, 0.76f, "Altair"),       // 21
    CatalogStar(305.557, 40.257, 2.48f, "Sadr"),        // 22
    // 23-26 Cygnus arms
    CatalogStar(292.680, 27.960, 2.87f, "Albireo"),     // 23
    CatalogStar(311.553, 33.970, 2.46f, "Gienah Cyg"),  // 24
    CatalogStar(299.077, 35.083, 2.23f, "Delta Cyg"),   // 25
    CatalogStar(318.234, 30.227, 3.21f, "Zeta Cyg"),    // 26
    // 27-30 Leo
    CatalogStar(152.093, 11.967, 1.35f, "Regulus"),     // 27
    CatalogStar(154.993, 19.842, 2.01f, "Algieba"),     // 28
    CatalogStar(168.527, 20.524, 2.56f, "Zosma"),       // 29
    CatalogStar(177.265, 14.572, 2.14f, "Denebola"),    // 30
    // 31-34 Scorpius
    CatalogStar(247.352, -26.432, 1.06f, "Antares"),    // 31
    CatalogStar(240.083, -22.622, 2.29f, "Dschubba"),   // 32
    CatalogStar(263.402, -37.104, 1.62f, "Shaula"),     // 33
    CatalogStar(252.968, -34.293, 2.82f, "Sargas"),     // 34
    // 35-40 bright standalone stars
    CatalogStar(101.287, -16.716, -1.46f, "Sirius"),    // 35
    CatalogStar(95.988, -52.696, -0.74f, "Canopus"),    // 36
    CatalogStar(213.915, 19.182, -0.05f, "Arcturus"),   // 37
    CatalogStar(219.902, -60.834, -0.27f, "Rigil Kent"),// 38
    CatalogStar(79.172, 45.998, 0.08f, "Capella"),      // 39
    CatalogStar(114.825, 5.225, 0.34f, "Procyon"),      // 40
    // 41-44 Gemini + Taurus
    CatalogStar(116.329, 28.026, 1.58f, "Castor"),      // 41
    CatalogStar(113.650, 31.888, 1.14f, "Pollux"),      // 42
    CatalogStar(68.980, 16.509, 0.87f, "Aldebaran"),    // 43
    CatalogStar(81.573, 28.608, 1.65f, "Elnath"),       // 44
    // 45-47 misc navigation
    CatalogStar(37.955, 89.264, 1.98f, "Polaris"),      // 45
    CatalogStar(24.429, -57.237, 0.46f, "Achernar"),    // 46
    CatalogStar(344.413, -29.622, 1.17f, "Fomalhaut"),  // 47
    // 48-51 Crux (Southern Cross)
    CatalogStar(186.650, -63.099, 0.77f, "Acrux"),      // 48
    CatalogStar(191.930, -59.689, 1.25f, "Mimosa"),     // 49
    CatalogStar(183.786, -57.113, 1.59f, "Gacrux"),     // 50
    CatalogStar(187.791, -58.749, 2.79f, "Delta Cru"),  // 51
    // 52-56 Boötes (the kite), anchored on Arcturus (37)
    CatalogStar(221.247, 27.074, 2.37f, "Izar"),        // 52
    CatalogStar(208.671, 18.398, 2.68f, "Muphrid"),     // 53
    CatalogStar(218.020, 38.308, 3.03f, "Seginus"),     // 54
    CatalogStar(225.486, 40.390, 3.49f, "Nekkar"),      // 55
    CatalogStar(228.876, 33.315, 3.46f, "Delta Boo"),   // 56
    // 57-60 Lyra parallelogram (Vega is 19)
    CatalogStar(282.520, 33.363, 3.52f, "Sheliak"),     // 57
    CatalogStar(284.736, 32.690, 3.25f, "Sulafat"),     // 58
    CatalogStar(283.626, 36.899, 4.30f, "Delta Lyr"),   // 59
    CatalogStar(282.200, 37.605, 4.36f, "Zeta Lyr"),    // 60
    // 61-64 Aquila (Altair is 21)
    CatalogStar(296.565, 10.613, 2.72f, "Tarazed"),     // 61
    CatalogStar(298.828, 6.407, 3.71f, "Alshain"),      // 62
    CatalogStar(286.352, 13.863, 2.99f, "Zeta Aql"),    // 63
    CatalogStar(302.826, -0.821, 3.23f, "Theta Aql"),   // 64
    // 65-68 Canis Major (Sirius is 35)
    CatalogStar(95.675, -17.956, 1.98f, "Mirzam"),      // 65
    CatalogStar(107.098, -26.393, 1.83f, "Wezen"),      // 66
    CatalogStar(104.656, -28.972, 1.50f, "Adhara"),     // 67
    CatalogStar(111.024, -29.303, 2.45f, "Aludra"),     // 68
    // 69-74 Pegasus (Great Square) + Andromeda
    CatalogStar(346.190, 15.205, 2.48f, "Markab"),      // 69
    CatalogStar(345.944, 28.083, 2.42f, "Scheat"),      // 70
    CatalogStar(3.309, 15.184, 2.83f, "Algenib"),       // 71
    CatalogStar(2.097, 29.090, 2.06f, "Alpheratz"),     // 72
    CatalogStar(17.433, 35.620, 2.05f, "Mirach"),       // 73
    CatalogStar(30.975, 42.330, 2.10f, "Almach"),       // 74
    // 75-76 Perseus
    CatalogStar(51.081, 49.861, 1.79f, "Mirfak"),       // 75
    CatalogStar(47.042, 40.956, 2.12f, "Algol"),        // 76
    // 77-79 Auriga pentagon (Capella 39, Elnath 44)
    CatalogStar(89.882, 44.947, 1.90f, "Menkalinan"),   // 77
    CatalogStar(89.930, 37.213, 2.62f, "Mahasim"),      // 78
    CatalogStar(74.248, 33.166, 2.69f, "Hassaleh"),     // 79
    // 80-85 Sagittarius (the Teapot)
    CatalogStar(276.043, -34.385, 1.85f, "Kaus Austr."),// 80
    CatalogStar(283.816, -26.297, 2.05f, "Nunki"),      // 81
    CatalogStar(275.248, -29.828, 2.70f, "Kaus Media"), // 82
    CatalogStar(276.993, -25.421, 2.81f, "Kaus Bor."),  // 83
    CatalogStar(285.653, -29.880, 2.60f, "Ascella"),    // 84
    CatalogStar(281.414, -26.990, 3.17f, "Phi Sgr"),    // 85
)

/** Index pairs into [BRIGHT_STARS] that draw the stick-figure of each constellation. */
val constellationLines: List<Pair<Int, Int>> = listOf(
    // Orion: shoulders, belt, feet
    2 to 0, 2 to 3, 0 to 5, 3 to 4, 4 to 5, 3 to 1, 5 to 6, 1 to 6,
    // Big Dipper
    7 to 8, 8 to 9, 9 to 10, 10 to 7, 10 to 11, 11 to 12, 12 to 13,
    // Cassiopeia W
    14 to 15, 15 to 16, 16 to 17, 17 to 18,
    // Cygnus cross
    20 to 22, 22 to 23, 24 to 22, 22 to 25, 25 to 26,
    // Leo
    27 to 28, 28 to 29, 29 to 30,
    // Scorpius
    32 to 31, 31 to 34, 34 to 33,
    // Gemini
    41 to 42,
    // Crux
    48 to 50, 49 to 51,
    // Boötes (kite): Arcturus at the base
    37 to 53, 37 to 52, 52 to 56, 56 to 55, 55 to 54, 54 to 37,
    // Lyra: Vega + parallelogram
    19 to 60, 19 to 59, 60 to 57, 59 to 58, 57 to 58,
    // Aquila: the eagle's line + wings
    61 to 21, 21 to 62, 21 to 63, 21 to 64,
    // Canis Major: head to tail
    35 to 67, 67 to 66, 66 to 68, 35 to 65,
    // Pegasus Great Square + Andromeda chain
    69 to 70, 70 to 72, 72 to 71, 71 to 69, 72 to 73, 73 to 74,
    // Perseus
    74 to 75, 75 to 76,
    // Auriga pentagon
    39 to 77, 77 to 78, 78 to 44, 44 to 79, 79 to 39,
    // Sagittarius Teapot
    80 to 82, 82 to 83, 83 to 85, 85 to 81, 81 to 84, 84 to 80,
    // Taurus horn (Aldebaran → Elnath)
    43 to 44,
)

/** Constellation name + the catalog-star index to anchor its label on (a bright central star). */
val constellationLabels: List<Pair<String, Int>> = listOf(
    "ORION" to 4, "BIG DIPPER" to 10, "CASSIOPEIA" to 16, "CYGNUS" to 22, "LEO" to 28,
    "SCORPIUS" to 31, "GEMINI" to 42, "CRUX" to 50, "BOÖTES" to 52, "LYRA" to 19,
    "AQUILA" to 21, "CANIS MAJOR" to 66, "PEGASUS" to 69, "ANDROMEDA" to 73, "PERSEUS" to 75,
    "AURIGA" to 77, "SAGITTARIUS" to 82, "TAURUS" to 43,
)
