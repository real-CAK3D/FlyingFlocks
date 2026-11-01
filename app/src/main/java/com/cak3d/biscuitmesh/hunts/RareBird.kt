package com.cak3d.biscuitmesh.hunts

/** A trackable target in the Rare Birds hunt. */
enum class BirdKind { RARE_FIND, COLLECTIBLE, STRAY }

/**
 * One "rare bird" — a point you can track and navigate to. RARE_FIND birds are
 * promoted from unusual wardrive hits, COLLECTIBLE birds are seeded near you to
 * go find, and the STRAY is a computed lead toward unexplored airspace.
 */
data class RareBird(
    val id: String,
    val name: String,
    val kind: BirdKind,
    val lat: Double,
    val lon: Double,
    val captured: Boolean = false,
    val note: String = "",
)
