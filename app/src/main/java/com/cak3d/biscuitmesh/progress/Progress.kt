package com.cak3d.biscuitmesh.progress

/**
 * The things we can find, each its own levelling track. Meshtastic is deliberately
 * left out — it's too rare to gate ranks behind. Cameras are worth far more XP per
 * find than radios because they're scarce; radios level through sheer volume.
 */
enum class ProgressTrack(val displayName: String, val short: String, val xpPerFind: Int) {
    WIFI("Wi-Fi networks", "Wi-Fi", 3),
    BLUETOOTH("Bluetooth devices", "BT", 3),
    FLOCK("Flock / ALPR cameras", "Flock", 60),
    SPEED_CAM("Speed cameras", "Speed", 45),
    REDLIGHT("Red-light cameras", "Red-light", 45),
}

/** One track's accrued state. Levels start at 1; XP thresholds grow quadratically. */
data class TrackState(val track: ProgressTrack, val xp: Int, val count: Int) {
    val level: Int get() = levelForXp(xp)
    val levelStartXp: Int get() = xpForLevel(level)
    val nextLevelXp: Int get() = xpForLevel(level + 1)

    /** 0f..1f toward the next level. */
    val progress: Float
        get() {
            val span = (nextLevelXp - levelStartXp).coerceAtLeast(1)
            return ((xp - levelStartXp).toFloat() / span).coerceIn(0f, 1f)
        }
}

/**
 * Overall standing. The main rank is the MINIMUM track level: you can't rank up
 * until every track has reached the next level, so a weak track (usually cameras)
 * is what holds you back — chasing it is what "widens your reach".
 */
data class Progress(
    val tracks: List<TrackState>,
    val rank: Int,
    val rankName: String,
    val weakest: List<ProgressTrack>,
) {
    val totalFinds: Int get() = tracks.sumOf { it.count }
    /** The level every track must reach for the next rank. */
    val nextRankLevel: Int get() = rank + 1
}

/** Cumulative XP to reach [level] (level 1 = 0, L2 = 25, L3 = 100, L4 = 225, …). */
fun xpForLevel(level: Int): Int {
    val n = (level - 1).coerceAtLeast(0)
    return 25 * n * n
}

/** Highest level [xp] has unlocked (minimum 1). */
fun levelForXp(xp: Int): Int {
    var lvl = 1
    while (xpForLevel(lvl + 1) <= xp) lvl++
    return lvl
}

private val RANK_NAMES = listOf(
    "Fledgling", "Nestling", "Scout", "Flyer", "Hawk",
    "Falcon", "Osprey", "Eagle", "Condor", "Apex Flock",
)

fun rankName(rank: Int): String =
    RANK_NAMES.getOrElse(rank - 1) { "Apex Flock +${rank - RANK_NAMES.size}" }

/** Builds the aggregate [Progress] from per-track states. */
fun progressFrom(tracks: List<TrackState>): Progress {
    val rank = tracks.minOfOrNull { it.level } ?: 1
    val weakest = tracks.filter { it.level == rank }.map { it.track }
    return Progress(tracks, rank, rankName(rank), weakest)
}
