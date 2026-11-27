package com.cak3d.biscuitmesh.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One thing the user can drop into a nav slot (a bottom tab or a top-bar icon). [core] marks the six
 * main scanning screens, which render inside the shell's own header; everything else is a full page
 * with its own header and back button. [id] is the nav route, except [HELP] which is the in-app help
 * action rather than a destination.
 */
data class NavDest(val id: String, val label: String, val icon: ImageVector, val core: Boolean = false)

/** The menu of destinations a user can assign to the bottom tabs and top-bar icons. */
object NavCatalog {
    const val HELP = "help"

    /** The six main scanning screens — headerless, rendered inside the shell. */
    val CORE = listOf(
        NavDest("fleet", "Fleet", Icons.Default.Dashboard, core = true),
        NavDest("wardrive", "Wardrive", Icons.Default.Map, core = true),
        NavDest("wifi", "WiFi", Icons.Default.Wifi, core = true),
        NavDest("bluetooth", "Bluetooth", Icons.Default.Bluetooth, core = true),
        NavDest("subghz", "Sub-GHz", Icons.Default.SettingsInputAntenna, core = true),
        NavDest("misc", "Misc", Icons.Default.Apps, core = true),
    )

    /** Everything else — full pages that can go in any slot. */
    val PAGES = listOf(
        NavDest(HELP, "Help", Icons.Default.Help),
        NavDest("settings", "Settings", Icons.Default.Settings),
        NavDest("dashboard", "Dashboard", Icons.Default.SpaceDashboard),
        NavDest("sensors", "Sensors & Sky", Icons.Default.SatelliteAlt),
        NavDest("terminal", "Terminal", Icons.Default.Terminal),
        NavDest("drives", "Drives", Icons.Default.History),
        NavDest("rarebirds", "Field Guide", Icons.Default.Radar),
        NavDest("guide", "Guide", Icons.Default.Help),
        NavDest("profile", "Profile", Icons.Default.Groups),
        NavDest("weather", "Weather", Icons.Default.Cloud),
        NavDest("alerts", "Alerts", Icons.Default.Warning),
        NavDest("airquality", "Air Quality", Icons.Default.Air),
        NavDest("spaceevents", "Space Events", Icons.Default.RocketLaunch),
        NavDest("feeds", "Live Feeds", Icons.Default.Videocam),
        NavDest("skyar", "Sky AR", Icons.Default.CameraAlt),
        NavDest("orbit", "Orbit", Icons.Default.Public),
        NavDest("orbitglobe", "Orbit Globe", Icons.Default.SatelliteAlt),
        NavDest("visiblepasses", "Visible Passes", Icons.Default.Visibility),
        NavDest("starlink", "Starlink", Icons.Default.Train),
        NavDest("skyconditions", "Sky Conditions", Icons.Default.Nightlight),
        NavDest("lightmap", "Light Pollution", Icons.Default.Map),
        NavDest("sitesurvey", "Site Survey", Icons.Default.TravelExplore),
        NavDest("vision", "Vision", Icons.Default.RemoveRedEye),
        NavDest("signals", "Signals", Icons.Default.Equalizer),
        NavDest("datacube", "Data Cube", Icons.Default.ViewInAr),
        NavDest("holotimeline", "Holo-Timeline", Icons.Default.Timeline),
        NavDest("store", "Store", Icons.Default.Storefront),
        NavDest("passport", "Passport", Icons.Default.MenuBook),
        NavDest("globalstats", "Global Stats", Icons.Default.Leaderboard),
        NavDest("feathersboard", "Feathers Board", Icons.Default.Leaderboard),
        NavDest("hunts", "Hunts", Icons.Default.EmojiEvents),
        NavDest("games", "Games", Icons.Default.SportsEsports),
        NavDest("ledger", "Device Ledger", Icons.Default.ReceiptLong),
        NavDest("tripjournal", "Trip Journal", Icons.Default.AutoStories),
        NavDest("elevation", "Drive Stats", Icons.Default.ShowChart),
        NavDest("crew", "Flock", Icons.Default.Groups),
        NavDest("squawk", "Squawk", Icons.Default.Campaign),
        NavDest("meshchat", "Caw", Icons.Default.Forum),
        NavDest("whisper", "Chirps", Icons.Default.Lock),
        NavDest("tiredash", "Tire Pressure", Icons.Default.DirectionsCar),
        NavDest("remote", "Universal Remote", Icons.Default.SettingsRemote),
        NavDest("labbench", "Lab Bench", Icons.Default.Science),
        NavDest("rogueap", "Rogue AP", Icons.Default.Security),
        NavDest("trackers", "Tracker Check", Icons.Default.PersonSearch),
        NavDest("watchlist", "Watchlist", Icons.Default.Visibility),
        NavDest("targets", "Targets", Icons.Default.GpsFixed),
        NavDest("fingerprint", "Place Fingerprint", Icons.Default.Fingerprint),
        NavDest("collect", "Background Collect", Icons.Default.Sensors),
        NavDest("terrain3d", "3D Terrain", Icons.Default.Terrain),
    )

    val ALL = CORE + PAGES

    private val byId = ALL.associateBy { it.id }
    fun byId(id: String): NavDest? = byId[id]
    fun isCore(id: String): Boolean = byId[id]?.core == true

    /** Shipped defaults — today's hard-coded layout. */
    val DEFAULT_BOTTOM = CORE.map { it.id }
    val DEFAULT_TOP = listOf(HELP, "dashboard", "rarebirds", "drives", "sensors", "terminal", "settings")
}
