package com.cak3d.biscuitmesh.geo

/** A fixed hazard we warn about: a camera from OpenStreetMap. */
enum class HazardKind { FLOCK, SPEED_CAMERA, RED_LIGHT, CAMERA }

data class Hazard(
    val id: String,          // "node/<osm id>"
    val kind: HazardKind,
    val lat: Double,
    val lon: Double,
    val name: String = "",
)

/** Nearest posted road speed limit — canonical km/h plus the mph equivalent. */
data class SpeedLimit(val kmh: Int, val mph: Int, val posted: String)
