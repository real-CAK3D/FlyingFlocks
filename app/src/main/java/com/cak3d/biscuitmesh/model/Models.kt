package com.cak3d.biscuitmesh.model

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
) {
    /** Connected, or mid-connection. */
    val isLive: Boolean
        get() = state == ConnectionState.READY ||
            state == ConnectionState.CONNECTING ||
            state == ConnectionState.PROVISIONING

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
    val rawJson: String = "",
)
