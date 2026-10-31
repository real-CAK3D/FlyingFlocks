package com.cak3d.biscuitmesh.protocol

/**
 * Biscuit line protocol: `TYPE:SUBTYPE:PAYLOAD\n`.
 *
 * The payload may be empty but the colons remain (e.g. `CMD:stopscan:`).
 * Multiple DATA records can share one notification separated by `\n`, so callers
 * must split incoming bytes on `\n` and parse each line independently.
 */
data class BiscuitMessage(
    val type: Type,
    val subtype: String,
    val payload: String,
    val raw: String,
) {
    enum class Type { CMD, RSP, STATUS, DATA, ERROR, TIME, UNKNOWN }

    companion object {
        fun parse(line: String): BiscuitMessage {
            val trimmed = line.trimEnd('\r', '\n')
            if (trimmed.isEmpty()) {
                return BiscuitMessage(Type.UNKNOWN, "", "", line)
            }
            // Only the first two colons are structural; the payload itself may contain colons.
            val first = trimmed.indexOf(':')
            if (first < 0) {
                return BiscuitMessage(Type.UNKNOWN, trimmed, "", line)
            }
            val second = trimmed.indexOf(':', first + 1)
            val typeStr = trimmed.substring(0, first)
            val subtype: String
            val payload: String
            if (second < 0) {
                subtype = trimmed.substring(first + 1)
                payload = ""
            } else {
                subtype = trimmed.substring(first + 1, second)
                payload = trimmed.substring(second + 1)
            }
            val type = runCatching { Type.valueOf(typeStr) }.getOrDefault(Type.UNKNOWN)
            return BiscuitMessage(type, subtype, payload, line)
        }
    }
}

/** Builds a well-formed command line including the required trailing colon. */
fun command(name: String, params: String = ""): ByteArray =
    "CMD:$name:$params\n".toByteArray(Charsets.UTF_8)

/** STATUS subtype codes reported by the device. */
enum class StatusCode(val code: Int, val label: String) {
    BOOTING(0, "Booting"),
    READY(1, "Ready"),
    BUSY(2, "Busy"),
    OK(3, "OK"),
    WARNING(4, "Warning"),
    ERROR(5, "Error"),
    SCAN_COMPLETE(6, "Scan complete"),
    UNKNOWN(-1, "Unknown");

    companion object {
        fun from(code: Int): StatusCode = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}
