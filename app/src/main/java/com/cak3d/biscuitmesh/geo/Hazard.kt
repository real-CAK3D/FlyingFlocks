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

/** A safety / emergency place from OpenStreetMap — a toggleable map layer. */
enum class SafetyKind { POLICE, FIRE, HOSPITAL, AMBULANCE }
data class SafetyPoi(val id: String, val kind: SafetyKind, val lat: Double, val lon: Double, val name: String = "")
