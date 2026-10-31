package com.cak3d.biscuitmesh.protocol

import java.util.UUID

/**
 * Constants for the public Biscuit BLE/GATT surface.
 *
 * Source: CodeHedge Biscuit Wiki, "3rd-Party Integration / GATT Reference".
 * These describe the device the app talks to; the app itself is a clean-room client.
 */
object BiscuitGatt {

    /** Primary service. Scanning filters on this UUID, NOT on the advertised name. */
    val SERVICE: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")

    /** Central -> device. Write / Write-Without-Response. Carries `CMD:` lines. */
    val CMD: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

    /** Device -> central. Notify. Carries RSP / STATUS / DATA / ERROR / TIME lines. */
    val RESPONSE: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a9")

    /** Device -> central. Read. JSON health snapshot. */
    val STATUS: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26aa")

    /** Bidirectional. Read / Write / Notify. JSON configuration. */
    val SETTINGS: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26ab")

    /** Optional encrypted-read characteristic for LE Secure Connections bonding. */
    val SECURITY: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26ac")

    /** Standard CCCD; write 0x0001 to enable notifications on RESPONSE. */
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** Standard Device Information Service, for model / firmware strings. */
    val DEVICE_INFO_SERVICE: UUID = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")
    val MODEL_NUMBER: UUID = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
    val FIRMWARE_REV: UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
    val SOFTWARE_REV: UUID = UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")

    /** Device requests this MTU on connect; a client MTU >= 247 avoids fragmentation. */
    const val PREFERRED_MTU = 517
    const val MIN_USEFUL_MTU = 247

    /** The advertised local name is the same ("Biscuit") on every device, so it is not a filter. */
    const val ADVERTISED_NAME = "Biscuit"
}
