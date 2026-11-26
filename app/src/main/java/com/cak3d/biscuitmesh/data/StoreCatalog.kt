package com.cak3d.biscuitmesh.data

/** What a store item changes when equipped. */
enum class StoreKind { TITLE, ACCENT, AVATAR, OVERLAY, PREMIUM }

/** One purchasable cosmetic. [value] is the title text / hex / avatar glyph / overlay id. */
data class StoreItem(
    val id: String,
    val name: String,
    val desc: String,
    val price: Int,
    val kind: StoreKind,
    val value: String,
    /** True for premium-only items (shown but locked without Flock Premium). */
    val premiumOnly: Boolean = false,
)

/** The store catalogue. Tight pricing — cosmetics are earned, vanity is pricey. */
object StoreCatalog {
    const val RENAME_COST = 3000
    const val PREMIUM_PRICE = 20000

    val items = listOf(
        // Titles — a flex shown under your handle.
        StoreItem("title_nightowl", "Night Owl", "For the after-dark drivers.", 800, StoreKind.TITLE, "Night Owl"),
        StoreItem("title_gridsweeper", "Grid Sweeper", "You leave no street uncovered.", 1200, StoreKind.TITLE, "Grid Sweeper"),
        StoreItem("title_signalhunter", "Signal Hunter", "Nothing on the air escapes you.", 1200, StoreKind.TITLE, "Signal Hunter"),
        StoreItem("title_skywatcher", "Sky Watcher", "Eyes always up.", 1500, StoreKind.TITLE, "Sky Watcher"),
        StoreItem("title_apex", "Apex Corvid", "Top of the flock.", 2500, StoreKind.TITLE, "Apex Corvid"),

        // Premium accent colours — applied across the whole app theme.
        StoreItem("accent_sunset", "Sunset", "A warm orange-pink accent.", 1200, StoreKind.ACCENT, "#FF7849"),
        StoreItem("accent_toxic", "Toxic", "Electric lime.", 1200, StoreKind.ACCENT, "#B6FF3B"),
        StoreItem("accent_ice", "Ice", "Cool pale cyan.", 1200, StoreKind.ACCENT, "#7DF9FF"),
        StoreItem("accent_blood", "Blood", "Deep crimson.", 1500, StoreKind.ACCENT, "#E01E37"),
        StoreItem("accent_gold", "24K", "Rich metallic gold.", 1800, StoreKind.ACCENT, "#FFC400"),
        StoreItem("accent_royal", "Royal", "Regal violet.", 1800, StoreKind.ACCENT, "#7C4DFF"),

        // Profile pictures (preset glyphs — the value is the emoji drawn in your avatar).
        StoreItem("avatar_crow", "Crow", "The classic.", 0, StoreKind.AVATAR, "🐦‍⬛"),
        StoreItem("avatar_owl", "Owl", "Night sentinel.", 600, StoreKind.AVATAR, "🦉"),
        StoreItem("avatar_hawk", "Hawk", "Sharp eyes.", 600, StoreKind.AVATAR, "🦅"),
        StoreItem("avatar_sat", "Satellite", "Always overhead.", 800, StoreKind.AVATAR, "🛰️"),
        StoreItem("avatar_radar", "Radar", "Sweeping.", 800, StoreKind.AVATAR, "📡"),
        StoreItem("avatar_ghost", "Ghost", "Off the grid.", 1000, StoreKind.AVATAR, "👻"),
        StoreItem("avatar_alien", "Signal", "From out there.", 1200, StoreKind.AVATAR, "👾"),

        // Avatar/name overlays — a decorative ring around your profile picture (Discord-style).
        StoreItem("overlay_gold", "Gold Ring", "A solid gold band.", 1500, StoreKind.OVERLAY, "gold"),
        StoreItem("overlay_neon", "Neon Pulse", "A glowing accent ring.", 2000, StoreKind.OVERLAY, "neon"),
        StoreItem("overlay_circuit", "Circuit", "A running-light border.", 2500, StoreKind.OVERLAY, "circuit"),
        StoreItem("overlay_flame", "Flame", "A fiery gradient ring.", 3000, StoreKind.OVERLAY, "flame"),
        StoreItem("overlay_aurora", "Aurora", "Premium: a shifting aurora ring.", 0, StoreKind.OVERLAY, "aurora", premiumOnly = true),

        // Flock Premium — unlock with Feathers (a real-money option would need Google Play Billing).
        StoreItem("premium", "Flock Premium", "2× Feathers earning, a PREMIUM badge, and premium-only cosmetics (like the Aurora ring).",
            PREMIUM_PRICE, StoreKind.PREMIUM, "premium"),
    )

    fun byId(id: String): StoreItem? = items.firstOrNull { it.id == id }
}
