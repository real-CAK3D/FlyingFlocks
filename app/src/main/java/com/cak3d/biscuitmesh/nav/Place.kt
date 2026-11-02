package com.cak3d.biscuitmesh.nav

import com.cak3d.biscuitmesh.wardrive.LatLon

enum class PlaceKind { HOME, WORK, CUSTOM }

/** A saved destination the operator can route to. */
data class Place(
    val id: String,
    val name: String,
    val kind: PlaceKind,
    val lat: Double,
    val lon: Double,
)

/** A computed route to a destination, drawn on the map until cleared. */
data class ActiveRoute(
    val name: String,
    val destLat: Double,
    val destLon: Double,
    val points: List<LatLon>,
    val distanceM: Double,
    val durationS: Double,
)
