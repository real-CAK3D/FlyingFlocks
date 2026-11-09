package com.cak3d.biscuitmesh.tools

/**
 * A fleet tool: a labelled BLE command the user can run against connected boards.
 *
 * [command] is the part after `CMD:` and is prefilled into the run screen so the
 * user can edit params (targets, SSIDs, …) before sending. The firmware performs
 * the actual radio work; the app only dispatches and renders results.
 *
 * [documented] is true when the command is taken from the published Biscuit command
 * reference; false ones are best-effort and flagged as editable in the UI.
 */
data class Tool(
    val key: String,
    val title: String,
    val subtitle: String,
    val command: String,
    val offensive: Boolean = false,
    val documented: Boolean = true,
)

object ToolCatalog {

    // --- WiFi ----------------------------------------------------------------

    val wifiSniffing = listOf(
        Tool("scanap", "Scan Access Points", "Discover nearby WiFi networks", "scanap"),
        Tool("scansta", "Scan Stations", "Detect WiFi clients (run Scan APs first)", "scansta"),
        Tool("scanall", "Scan All", "APs and Stations together", "scanall"),
        Tool("sniffbeacon", "Beacon Frames", "Monitor beacon broadcasts", "sniffbeacon"),
        Tool("sniffprobe", "Probe Requests", "Capture probe requests", "sniffprobe"),
        Tool("sniffdeauth", "Deauth Frames", "Detect deauth / disassoc frames", "sniffdeauth"),
        Tool("packetcount", "Packet Stats", "Rolling frame-type counters", "packetcount"),
        Tool("sniffpwn", "Pwnagotchi", "Detect Pwnagotchi devices", "sniffpwn"),
        Tool("sniffpinescan", "Pineapple Scan", "Detect suspicious APs", "sniffpinescan"),
        Tool("channelanalyze", "Channel Analyze", "Per-channel activity", "channelanalyze"),
    )

    val wifiAttacks = listOf(
        Tool("deauth", "Deauth Attack", "Disconnect clients — needs targets", "attack:type=deauth,mode=basic,targets=", offensive = true),
        Tool("beacon", "Beacon Spam", "Flood fake access points", "attack:type=beacon,mode=random", offensive = true),
        Tool("rickroll", "RickRoll", "Beacon set with lyric SSIDs", "attack:type=rickroll", offensive = true),
        Tool("probeatk", "Probe Attack", "Probe request flooding — needs targets", "attack:type=probe,targets=", offensive = true),
        Tool("badmsg", "Bad Message", "Malformed-frame attack — needs targets", "attack:type=badmsg,targets=", offensive = true),
        Tool("sleepatk", "Sleep Attack", "Association sleep — needs targets", "attack:type=sleep,targets=", offensive = true),
        Tool("portalstart", "Captive Portal", "Evil Portal — needs ssid/bssid/channel/file", "portalstart:ssid=,bssid=,channel=,file=", offensive = true),
    )

    val wifiScanners = listOf(
        Tool("pingscan", "Ping Scan", "ICMP echo to find alive hosts", "pingscan", documented = false),
        Tool("arpscan", "ARP Scan", "Map network devices", "arpscan", documented = false),
        Tool("portscan", "Port Scan All", "Scan ports on discovered hosts", "portscan", documented = false),
        Tool("sshscan", "SSH Scan", "Find SSH services (22)", "sshscan", documented = false),
        Tool("telnetscan", "Telnet Scan", "Find Telnet services (23)", "telnetscan", documented = false),
    )

    // --- Bluetooth -----------------------------------------------------------

    val btScanning = listOf(
        Tool("sniffbt", "Bluetooth Scan", "Discover nearby BLE devices", "sniffbt:type=all"),
        Tool("findmy", "Find My Sniff", "AirTags, FMNA & DULT trackers", "sniffbt:type=airtag"),
        Tool("flipper", "Flipper Detection", "Detect Flipper Zero devices", "sniffbt:type=flipper"),
        Tool("flock", "Flock Detection", "Detect Flock trackers", "sniffbt:type=flock"),
        Tool("skimmer", "Skimmer Detection", "Detect card-skimmer modules", "sniffskim"),
    )

    val btAttacks = listOf(
        Tool("bt_apple", "Sour Apple", "iOS BLE spam", "attack:type=bt_apple", offensive = true),
        Tool("bt_windows", "Swiftpair", "Windows BLE spam", "attack:type=bt_windows", offensive = true),
        Tool("bt_samsung", "Samsung Spam", "Samsung BLE spam", "attack:type=bt_samsung", offensive = true),
        Tool("bt_google", "Google Fast Pair", "Fast Pair spam", "attack:type=bt_google", offensive = true),
        Tool("bt_flipper", "Flipper Spam", "Flipper Zero spam", "attack:type=bt_flipper", offensive = true),
        Tool("bt_all", "BLE Spam All", "Cycle all BLE spam types", "attack:type=bt_all", offensive = true),
        Tool("bt_airtag", "AirTag Spoof", "Spoof an AirTag — needs payload/mac", "attack:type=bt_airtag,payload=", offensive = true),
    )

    // --- Misc ----------------------------------------------------------------

    val misc = listOf(
        Tool("meshrecon", "802.15.4 Mesh Recon", "Map Zigbee/Thread/Matter", "meshrecon", documented = false),
        Tool("flockmisc", "Flock Detection", "Flock trackers via BLE + WiFi", "sniffbt:type=flock"),
        Tool("dronescan", "Drone Detection", "Drones via RemoteID", "dronescan", documented = false),
    )

    // --- MaurCAK3D (ESP32 Marauder on the XIAO C5, Biscuit-BLE bridge) --------
    // These map to the biscuit-bridge CMD contract: benign sniffs stream
    // DATA:STA / DATA:PROBE / DATA:AP; the two attacks are offensive-gated.
    val marauder = listOf(
        Tool("mc_stations", "Station Sniff", "Client stations near APs → DATA:STA", "stationlist"),
        Tool("mc_probes", "Probe Sniff", "SSIDs clients are probing for → DATA:PROBE", "probesniff"),
        Tool("mc_aplist", "AP List", "Access points with vendor + packet counts → DATA:AP", "aplist"),
        Tool("mc_pktmon", "Packet Monitor", "Raw 802.11 packet monitor", "pktmon"),
        Tool("mc_stop", "Stop", "Stop all scans and attacks", "stopscan"),
        Tool("mc_deauth", "Deauth", "Deauth the board's selected AP list — own networks only", "deauth", offensive = true),
        Tool("mc_beaconspam", "Beacon Spam", "Flood fake beacons — own networks only", "beaconspam", offensive = true),
    )

    // --- T-Embed CC1101 Plus (ESP32-S3 Biscuit image) ------------------------
    // Everything the T-Embed hardware offers over the Biscuit-BLE bridge: CC1101
    // sub-GHz (sweep/decode + gated TX), PN532 NFC, IR TX/RX, and the Wi-Fi set.
    // Results stream back live (DATA:RF/TPMS/WEATHER/KEYFOB/NFC/IR/STA/PROBE/PKT).
    val tembed = listOf(
        Tool("te_subghz", "Sub-GHz Sweep", "315/433/868/915 RSSI + TPMS/weather/keyfob decode", "scanall"),
        Tool("te_nfc", "NFC Read", "Poll the PN532 for a tag → DATA:NFC", "nfcread"),
        Tool("te_irsniff", "IR Sniff", "Enable the IR receiver → DATA:IR", "irsniff"),
        Tool("te_stations", "Station Sniff", "Wi-Fi client stations → DATA:STA", "stationlist"),
        Tool("te_probes", "Probe Sniff", "SSIDs clients are probing for → DATA:PROBE", "probesniff"),
        Tool("te_aplist", "AP List", "Access points with vendor + packet counts → DATA:AP", "aplist"),
        Tool("te_pktmon", "Packet Monitor", "802.11 frame-type counters → DATA:PKT", "pktmon"),
        Tool("te_stop", "Stop", "Stop all scans and attacks", "stopscan"),
        Tool("te_irsend", "IR Send", "Transmit IR — edit proto,hex (e.g. NEC,0x20DF10EF)", "irsend:NEC,", documented = false),
        Tool("te_tx", "Sub-GHz TX", "Replay an OOK burst — edit freqMHz,bits — your own device only", "tx:433.92,", offensive = true),
        Tool("te_deauth", "Deauth", "Deauth the board's selected AP list — own networks only", "deauth", offensive = true),
        Tool("te_beaconspam", "Beacon Spam", "Flood fake beacons — own networks only", "beaconspam", offensive = true),
    )

    val all: List<Tool> =
        wifiSniffing + wifiAttacks + wifiScanners + btScanning + btAttacks + misc + marauder + tembed

    fun byKey(key: String): Tool? = all.firstOrNull { it.key == key }
}
