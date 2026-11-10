package com.cak3d.biscuitmesh.model

import com.cak3d.biscuitmesh.protocol.BiscuitMessage

/** One protocol line in the fleet-wide log, tagged with its board and arrival time. */
data class LogEntry(
    val address: String,
    val message: BiscuitMessage,
    val timeMs: Long,
)

/** Where a device sits in its connection lifecycle. */
enum class ConnectionState {
    DISCOVERED,   // seen in a scan, not connected
    CONNECTING,   // GATT connect / service discovery in progress
    PROVISIONING, // MTU + notifications being set up
    READY,        // handshake done, accepting commands
    DISCONNECTED, // dropped; may be retried
    FAILED,       // gave up
}

/**
 * A single ESP32-C5 board in the fleet. The app may hold several of these
 * connected at once (subject to the Android BLE multi-connection limit).
 */
data class BiscuitDevice(
    val address: String,              // BLE MAC, stable key for the fleet registry
    val displayName: String,          // user-set label; defaults to short address
    val rssi: Int = 0,
    val state: ConnectionState = ConnectionState.DISCOVERED,
    val model: String? = null,        // from Device Information Service 0x2A24
    val firmware: String? = null,     // from 0x2A26
    val status: DeviceStatus? = null, // latest parsed STATUS JSON snapshot
    val lastError: String? = null,
    val known: Boolean = false,       // saved to the fleet; persists across restarts
    val inRange: Boolean = false,     // seen in the current scan sweep
    val lastSeenMs: Long = 0L,        // when it last appeared in a scan
    val health: BoardHealth? = null,  // latest DATA:HEALTH heartbeat (live telemetry)
) {
    /** Connected, or mid-connection. */
    val isLive: Boolean
        get() = state == ConnectionState.READY ||
            state == ConnectionState.CONNECTING ||
            state == ConnectionState.PROVISIONING

    /** What kind of board this is (for the fleet card subtitle): the firmware's
     *  self-reported model, else the GATT model/firmware strings. */
    fun typeLabel(): String? = status?.model ?: model ?: firmware

    /** True when the board reports a link-code gate that hasn't been satisfied yet. */
    val needsLink: Boolean get() = status?.link == "required"

    /** A short human status line for the card. */
    fun statusLabel(): String = when {
        state == ConnectionState.READY -> "Connected"
        state == ConnectionState.CONNECTING -> "Connecting…"
        state == ConnectionState.PROVISIONING -> "Setting up…"
        state == ConnectionState.FAILED -> "Failed"
        inRange -> "In range"
        known -> "Out of range"
        else -> "Seen"
    }
}

/**
 * Parsed snapshot from the Status characteristic (JSON). Keys are treated as
 * opaque; unknown or absent keys stay null rather than being assumed.
 */
data class DeviceStatus(
    val statusCode: Int? = null,
    val battery: Int? = null,         // 0-100, or null if not yet measured (-1 on wire)
    val temperatureC: Double? = null,
    val sdMounted: Boolean? = null,
    val activeOp: String? = null,     // e.g. "pktmon" / "eapol" during capture
    val model: String? = null,        // firmware's self-reported model, e.g. "T-Embed-CC1101"
    val link: String? = null,         // link-code gate: "required" | "ok" | null
    val rawJson: String = "",
)

/**
 * A live health snapshot from a board's `DATA:HEALTH:` heartbeat (emitted every
 * few seconds over BLE/USB). This is how the Fleet Health view tells whether a
 * board is actually working: free memory, uptime (a reset zeroes it), and which
 * radios/peripherals came up.
 *   DATA:HEALTH:<heapFree>,<uptimeSec>,<sd>,<cc1101>,<nfc>,<link>
 */
data class BoardHealth(
    val heapFree: Int? = null,
    val uptimeSec: Long? = null,
    val sd: Boolean? = null,
    val cc1101: Boolean? = null,
    val nfc: Boolean? = null,
    val link: String? = null,
    val atMs: Long = 0L,              // when this heartbeat arrived (for "last heard")
) {
    companion object {
        /** Parse a `DATA:HEALTH` payload (`heap,uptime,sd,cc1101,nfc,link`). */
        fun parse(payload: String, atMs: Long): BoardHealth? {
            val p = payload.split(',')
            if (p.size < 6) return null
            fun bit(s: String): Boolean? = when (s.trim()) { "1" -> true; "0" -> false; else -> null }
            return BoardHealth(
                heapFree = p[0].trim().toIntOrNull(),
                uptimeSec = p[1].trim().toLongOrNull(),
                sd = bit(p[2]),
                cc1101 = bit(p[3]),
                nfc = bit(p[4]),
                link = p[5].trim().ifEmpty { null },
                atMs = atMs,
            )
        }
    }
}
