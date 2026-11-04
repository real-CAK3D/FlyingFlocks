package com.cak3d.biscuitmesh.progress

/**
 * The things we can find. Wi-Fi and Bluetooth are the levelling grind — you rank up
 * through their sheer volume. Cameras aren't a gate: each one you discover is a big
 * one-time boost toward the next rank (worth many radios), but since cameras are
 * tracked you only ever get that boost once per camera. Meshtastic is left out.
 */
enum class ProgressTrack(val displayName: String, val short: String, val xpPerFind: Int) {
    WIFI("Wi-Fi networks", "Wi-Fi", 3),
    BLUETOOTH("Bluetooth devices", "BT", 3),
    FLOCK("Flock / ALPR cameras", "Flock", 250),
    SPEED_CAM("Speed cameras", "Speed", 180),
    REDLIGHT("Red-light cameras", "Red-light", 180),
}

/** The radios you grind to rank up. */
val RADIO_TRACKS = setOf(ProgressTrack.WIFI, ProgressTrack.BLUETOOTH)

/** Cameras — one-time boosts toward your rank, not a gate. */
val CAMERA_TRACKS = setOf(ProgressTrack.FLOCK, ProgressTrack.SPEED_CAM, ProgressTrack.REDLIGHT)

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
 * Overall standing. Your rank is the level your combined XP has earned — Wi-Fi and
 * Bluetooth volume plus every camera's one-time boost — PLUS a full extra level for
 * each rare bird you've captured. Rare birds are the only shortcut; the rest is a
 * long, deliberate grind (hundreds of ranks deep).
 */
data class Progress(
    val tracks: List<TrackState>,
    val levelXp: Int,
    /** Full levels granted by captured rare birds, stacked on top of the XP level. */
    val bonusLevels: Int,
    val rank: Int,
    val rankName: String,
) {
    val totalFinds: Int get() = tracks.sumOf { it.count }
    val radios: List<TrackState> get() = tracks.filter { it.track in RADIO_TRACKS }
    val cameras: List<TrackState> get() = tracks.filter { it.track in CAMERA_TRACKS }
    val cameraFinds: Int get() = cameras.sumOf { it.count }
    val cameraBoostXp: Int get() = cameras.sumOf { it.xp }

    /** The level earned from XP alone (rank minus the rare-bird bonus). */
    val xpLevel: Int get() = rank - bonusLevels
    val rankStartXp: Int get() = xpForLevel(xpLevel)
    val rankNextXp: Int get() = xpForLevel(xpLevel + 1)
    val xpToNext: Int get() = (rankNextXp - levelXp).coerceAtLeast(0)
    /** 0f..1f toward the next XP level. */
    val rankProgress: Float
        get() {
            val span = (rankNextXp - rankStartXp).coerceAtLeast(1)
            return ((levelXp - rankStartXp).toFloat() / span).coerceIn(0f, 1f)
        }
}

/**
 * Cumulative XP to reach [level]. Deliberately steep so ranks are earned over many
 * drives, not handed out: L2 = 450, L3 = 1,800, L4 = 4,050, L5 = 7,200 … A Wi-Fi/BT
 * find is 3 XP, so early ranks already mean hundreds of finds, and it keeps climbing
 * for 200+ levels.
 */
fun xpForLevel(level: Int): Int {
    val n = (level - 1).coerceAtLeast(0)
    return 450 * n * n
}

/** Highest level [xp] has unlocked (minimum 1). */
fun levelForXp(xp: Int): Int {
    var lvl = 1
    while (xpForLevel(lvl + 1) <= xp) lvl++
    return lvl
}

// The rank ladder: ~100 birds climbing from humble feeder visitors to apex raptors
// and myth. Beyond the list, an ascending epithet is prepended so the ladder runs
// hundreds of ranks deep without repeating a name.
private val RANK_BIRDS = listOf(
    "Sparrow", "Wren", "Finch", "Chickadee", "Titmouse", "Junco", "Siskin", "Redpoll",
    "Swallow", "Martin", "Robin", "Bluebird", "Thrush", "Catbird", "Mockingbird", "Waxwing",
    "Starling", "Lark", "Pipit", "Warbler", "Vireo", "Oriole", "Tanager", "Bunting",
    "Cardinal", "Grosbeak", "Towhee", "Bobolink", "Meadowlark", "Jay", "Magpie", "Nutcracker",
    "Jackdaw", "Rook", "Crow", "Raven", "Swift", "Nightjar", "Nighthawk", "Whippoorwill",
    "Kingfisher", "Sapsucker", "Woodpecker", "Flicker", "Cuckoo", "Roadrunner", "Dove", "Pigeon",
    "Plover", "Killdeer", "Sandpiper", "Curlew", "Godwit", "Snipe", "Woodcock", "Avocet",
    "Skimmer", "Tern", "Gull", "Jaeger", "Heron", "Egret", "Bittern", "Ibis",
    "Spoonbill", "Stork", "Crane", "Grebe", "Loon", "Cormorant", "Gannet", "Pelican",
    "Puffin", "Auk", "Shearwater", "Petrel", "Fulmar", "Albatross", "Kestrel", "Merlin",
    "Hobby", "Sparrowhawk", "Harrier", "Kite", "Buzzard", "Goshawk", "Caracara", "Osprey",
    "Falcon", "Peregrine", "Gyrfalcon", "Owl", "Hawk", "Sea-Eagle", "Eagle", "Condor",
    "Vulture", "Phoenix", "Roc", "Thunderbird",
)

// Prestige epithets for each full pass through the bird list (rank 1 = no epithet).
private val RANK_EPITHETS = listOf(
    "Gilded", "Bronze", "Silver", "Storm", "Ember", "Frost", "Shadow", "Crimson",
    "Astral", "Obsidian", "Mythic", "Primeval", "Celestial", "Eternal",
)

fun rankName(rank: Int): String {
    val idx = (rank - 1).coerceAtLeast(0)
    val base = RANK_BIRDS[idx % RANK_BIRDS.size]
    val tier = idx / RANK_BIRDS.size
    if (tier == 0) return base
    val epithet = RANK_EPITHETS.getOrElse(tier - 1) { "Eternal +${tier - RANK_EPITHETS.size}" }
    return "$epithet $base"
}

/** Builds the aggregate [Progress]: XP level for the grind, plus a full level per rare bird. */
fun progressFrom(tracks: List<TrackState>, bonusLevels: Int = 0): Progress {
    val levelXp = tracks.sumOf { it.xp }
    val bonus = bonusLevels.coerceAtLeast(0)
    val rank = levelForXp(levelXp) + bonus
    return Progress(tracks, levelXp, bonus, rank, rankName(rank))
}
