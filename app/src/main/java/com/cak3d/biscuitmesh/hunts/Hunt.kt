package com.cak3d.biscuitmesh.hunts

/** Everything a hunt can be scored against — gathered once from the live stats. */
data class HuntInputs(
    val wifi24: Int,
    val wifi5: Int,
    val bt: Int,
    val flock: Int,
    val speedCam: Int,
    val redlight: Int,
    val totalKm: Double,
    val mesh: Int,
    val rf: Int,
    val rank: Int,
) {
    val wifi: Int get() = wifi24 + wifi5
    val cameras: Int get() = flock + speedCam + redlight
    val signals: Int get() = wifi + bt
}

/**
 * One bounty. Instead of a single fixed goal, a hunt has a ladder of [tiers] that rolls up:
 * clear one target and the next, bigger one takes its place (5 → 10 → 15 …). Your level on a
 * hunt is how many rungs you've cleared; the hunt is "maxed" once you pass the last rung.
 */
data class Hunt(
    val id: String,
    val title: String,
    /** A short unit noun for the thing being counted, e.g. "Wi-Fi networks", "km of road". */
    val unit: String,
    val emoji: String,
    val tiers: List<Int>,
    val tier: Tier,
    val metric: (HuntInputs) -> Int,
) {
    enum class Tier(val label: String) { BRONZE("Bronze"), SILVER("Silver"), GOLD("Gold"), LEGEND("Legend") }

    /** Rungs cleared at [value] (0..tiers.size). */
    fun levelAt(value: Int): Int = tiers.count { value >= it }

    /** The target you're currently working toward (the last rung once maxed). */
    fun targetAt(value: Int): Int = tiers.firstOrNull { value < it } ?: tiers.last()

    /** The lower bound of the current rung, so the bar fills within this rung only. */
    fun floorAt(value: Int): Int {
        val idx = tiers.indexOfFirst { value < it }
        return if (idx <= 0) 0 else tiers[idx - 1]
    }

    fun maxed(value: Int): Boolean = value >= tiers.last()

    /** 0f..1f within the current rung (full once maxed). */
    fun progressAt(value: Int): Float {
        if (maxed(value)) return 1f
        val lo = floorAt(value)
        val hi = targetAt(value)
        val span = (hi - lo).coerceAtLeast(1)
        return ((value - lo).toFloat() / span).coerceIn(0f, 1f)
    }
}

/** The bounty board. Progress is derived live from the grind — no claiming needed. */
val HUNTS: List<Hunt> = listOf(
    Hunt("scout", "Scout", "Wi-Fi networks", "📡", listOf(100, 250, 500, 1000, 2500, 5000, 10000), Hunt.Tier.BRONZE) { it.wifi },
    Hunt("bluefinch", "Blue Finch", "Bluetooth devices", "🦷", listOf(100, 250, 500, 1000, 2500), Hunt.Tier.BRONZE) { it.bt },
    Hunt("mesh", "Mesh Weaver", "Meshtastic contacts", "🕸️", listOf(1, 2, 5, 10), Hunt.Tier.BRONZE) { it.mesh },
    Hunt("subghz", "Ghost in the Band", "sub-GHz signals", "📻", listOf(1, 5, 10, 25, 50), Hunt.Tier.BRONZE) { it.rf },
    Hunt("band5", "High Band", "5 GHz networks", "📶", listOf(50, 100, 250, 500, 1000), Hunt.Tier.SILVER) { it.wifi5 },
    Hunt("shutterbug", "Shutterbug", "cameras", "📷", listOf(10, 25, 50, 100), Hunt.Tier.SILVER) { it.cameras },
    Hunt("roadwarrior", "Road Warrior", "km of road", "🛣️", listOf(50, 100, 250, 500), Hunt.Tier.SILVER) { it.totalKm.toInt() },
    Hunt("flockbuster", "Flock Buster", "Flock / ALPR cameras", "🚨", listOf(5, 10, 15, 25, 50), Hunt.Tier.GOLD) { it.flock },
    Hunt("centurion", "Centurion", "rank", "🏅", listOf(10, 20, 30, 40, 50), Hunt.Tier.GOLD) { it.rank },
    Hunt("thousandaire", "Thousandaire", "signals total", "💠", listOf(1000, 5000, 10000, 25000, 50000), Hunt.Tier.GOLD) { it.signals },
    Hunt("marathon", "Marathon", "km of road", "🏁", listOf(250, 500, 1000, 2000), Hunt.Tier.LEGEND) { it.totalKm.toInt() },
    Hunt("apex", "Apex Predator", "rank", "🦅", listOf(30, 50, 75, 100), Hunt.Tier.LEGEND) { it.rank },
)
