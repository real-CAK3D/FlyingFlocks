package com.cak3d.biscuitmesh.data

/** What a store item changes when equipped. */
enum class StoreKind { TITLE, ACCENT }

/** One purchasable cosmetic. [value] is the title text (TITLE) or a #RRGGBB hex (ACCENT). */
data class StoreItem(
    val id: String,
    val name: String,
    val desc: String,
    val price: Int,
    val kind: StoreKind,
    val value: String,
)

/** The store catalogue. Tight pricing — cosmetics are earned, vanity is pricey. */
object StoreCatalog {
    const val RENAME_COST = 3000

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
    )

    fun byId(id: String): StoreItem? = items.firstOrNull { it.id == id }
}
