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

/** One bounty: a title, a target, and how to score it from the live stats. */
data class Hunt(
    val id: String,
    val title: String,
    val blurb: String,
    val emoji: String,
    val target: Int,
    val tier: Tier,
    val metric: (HuntInputs) -> Int,
) {
    enum class Tier(val label: String) { BRONZE("Bronze"), SILVER("Silver"), GOLD("Gold"), LEGEND("Legend") }
}

/** The bounty board. Progress is derived live from the grind — no claiming needed. */
val HUNTS: List<Hunt> = listOf(
    Hunt("scout", "Scout", "Catch 100 Wi-Fi networks", "📡", 100, Hunt.Tier.BRONZE) { it.wifi },
    Hunt("bluefinch", "Blue Finch", "Catch 100 Bluetooth devices", "🦷", 100, Hunt.Tier.BRONZE) { it.bt },
    Hunt("mesh", "Mesh Weaver", "Make contact with a Meshtastic node", "🕸️", 1, Hunt.Tier.BRONZE) { it.mesh },
    Hunt("subghz", "Ghost in the Band", "Catch a sub-GHz signal on the CC1101", "📻", 1, Hunt.Tier.BRONZE) { it.rf },
    Hunt("band5", "High Band", "Catch 50 networks on 5 GHz", "📶", 50, Hunt.Tier.SILVER) { it.wifi5 },
    Hunt("shutterbug", "Shutterbug", "Find 10 cameras of any kind", "📷", 10, Hunt.Tier.SILVER) { it.cameras },
    Hunt("roadwarrior", "Road Warrior", "Survey 50 km of road", "🛣️", 50, Hunt.Tier.SILVER) { it.totalKm.toInt() },
    Hunt("flockbuster", "Flock Buster", "Spot 5 Flock / ALPR cameras", "🚨", 5, Hunt.Tier.GOLD) { it.flock },
    Hunt("centurion", "Centurion", "Reach rank 10", "🏅", 10, Hunt.Tier.GOLD) { it.rank },
    Hunt("thousandaire", "Thousandaire", "Log 1,000 signals total", "💠", 1000, Hunt.Tier.GOLD) { it.signals },
    Hunt("marathon", "Marathon", "Survey 250 km of road", "🏁", 250, Hunt.Tier.LEGEND) { it.totalKm.toInt() },
    Hunt("apex", "Apex Predator", "Climb to rank 30", "🦅", 30, Hunt.Tier.LEGEND) { it.rank },
)
