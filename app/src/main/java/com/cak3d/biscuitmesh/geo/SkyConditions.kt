package com.cak3d.biscuitmesh.geo

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * Real-time sky darkness for an observer — the honest "live light pollution": a fixed base from
 * the site's Bortle class, modulated by what's actually happening right now (Sun twilight + Moon
 * phase/altitude). Pure math on top of [celestialBodies]; no network.
 */

enum class Twilight(val label: String) {
    DAY("Daylight"), CIVIL("Civil twilight"), NAUTICAL("Nautical twilight"),
    ASTRONOMICAL("Astronomical twilight"), NIGHT("Full dark"),
}

data class SkyConditions(
    val sunElDeg: Double,
    val twilight: Twilight,
    val moonElDeg: Double,
    val moonUp: Boolean,
    val moonIllum: Double,        // 0..1 illuminated fraction
    val moonPhase: String,
    val bortle: Int,             // 1 (pristine) .. 9 (inner city)
    val baseNelm: Double,        // naked-eye limiting magnitude at full dark for this Bortle
    val effectiveNelm: Double,   // right now, with twilight + moon
    val darkness: Double,        // 0 (bright) .. 1 (as dark as it gets here)
    val canSee: String,
)

/** Naked-eye limiting magnitude for a Bortle class at full astronomical dark (approx). */
private fun bortleNelm(b: Int): Double = (7.9 - 0.49 * (b - 1)).coerceIn(3.8, 7.9)

private fun phaseName(illum: Double): String = when {
    illum < 0.04 -> "New"
    illum < 0.46 -> "Crescent"
    illum < 0.54 -> "Quarter"
    illum < 0.96 -> "Gibbous"
    else -> "Full"
}

private fun canSee(nelm: Double, twilight: Twilight): String = when {
    twilight == Twilight.DAY -> "Sun, Moon & the brightest planets only"
    nelm < 2.0 -> "Only the Moon, planets & a handful of the brightest stars"
    nelm < 4.0 -> "Bright stars & constellations; no Milky Way"
    nelm < 5.3 -> "Most constellations; the Milky Way is faint or hidden"
    nelm < 6.3 -> "The Milky Way is visible; thousands of stars"
    else -> "Superb — the Milky Way casts shadows, faint deep-sky objects naked-eye"
}

/** Angular separation (degrees) between two az/el directions. */
private fun angularSep(az1: Float, el1: Float, az2: Float, el2: Float): Double {
    val a1 = Math.toRadians(az1.toDouble()); val e1 = Math.toRadians(el1.toDouble())
    val a2 = Math.toRadians(az2.toDouble()); val e2 = Math.toRadians(el2.toDouble())
    val c = sin(e1) * sin(e2) + cos(e1) * cos(e2) * cos(a1 - a2)
    return Math.toDegrees(acos(c.coerceIn(-1.0, 1.0)))
}

fun skyConditions(lat: Double, lon: Double, timeMs: Long, bortle: Int): SkyConditions {
    val bodies = celestialBodies(lat, lon, timeMs)
    val sun = bodies.first { it.name == "Sun" }
    val moon = bodies.first { it.name == "Moon" }

    val sunEl = sun.el.toDouble()
    val twilight = when {
        sunEl > -0.833 -> Twilight.DAY
        sunEl > -6 -> Twilight.CIVIL
        sunEl > -12 -> Twilight.NAUTICAL
        sunEl > -18 -> Twilight.ASTRONOMICAL
        else -> Twilight.NIGHT
    }

    val elong = angularSep(sun.az, sun.el, moon.az, moon.el)
    val illum = ((1 - cos(Math.toRadians(elong))) / 2).coerceIn(0.0, 1.0)
    val moonUp = moon.el > 0

    val base = bortleNelm(bortle)
    val twilightPenalty = when (twilight) {
        Twilight.DAY -> 8.0; Twilight.CIVIL -> 3.5; Twilight.NAUTICAL -> 1.5
        Twilight.ASTRONOMICAL -> 0.4; Twilight.NIGHT -> 0.0
    }
    val moonPenalty = if (moonUp) illum * (0.4 + 0.6 * sin(Math.toRadians(moon.el.toDouble()))) * 2.1 else 0.0
    val eff = (base - twilightPenalty - moonPenalty).coerceIn(-4.0, base)
    val darkness = ((eff) / base).coerceIn(0.0, 1.0)

    return SkyConditions(
        sunElDeg = sunEl, twilight = twilight,
        moonElDeg = moon.el.toDouble(), moonUp = moonUp,
        moonIllum = illum, moonPhase = phaseName(illum),
        bortle = bortle, baseNelm = base, effectiveNelm = eff, darkness = darkness,
        canSee = canSee(eff, twilight),
    )
}

/** Scan forward up to 24 h for the next time it reaches full astronomical dark with the Moon down. */
fun nextTrueDark(lat: Double, lon: Double, fromMs: Long): Long? {
    val step = 10 * 60 * 1000L
    var t = fromMs
    val end = fromMs + 24 * 60 * 60 * 1000L
    while (t <= end) {
        val c = skyConditions(lat, lon, t, 5)
        if (c.twilight == Twilight.NIGHT && !c.moonUp) return t
        t += step
    }
    return null
}
