package com.cak3d.biscuitmesh.sensors

import android.content.Context

/**
 * IEEE OUI registry (bundled as assets/oui.tsv) → vendor name from a MAC/BSSID.
 *
 * Loaded once, lazily, off the main thread. Lookups return null until it's ready,
 * and null for locally-administered (randomized/private) addresses, which have no
 * real manufacturer prefix.
 */
object OuiDb {
    @Volatile private var map: Map<String, String>? = null
    val loaded: Boolean get() = map != null

    fun ensureLoaded(context: Context) {
        if (map != null) return
        synchronized(this) {
            if (map != null) return
            val m = HashMap<String, String>(45_000)
            runCatching {
                context.applicationContext.assets.open("oui.tsv").bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val tab = line.indexOf('\t')
                        if (tab == 6) m[line.substring(0, 6)] = line.substring(tab + 1)
                    }
                }
            }
            map = m
        }
    }

    private fun hex(mac: String): String = mac.replace(":", "").replace("-", "").uppercase()

    /** True for a locally-administered (randomized / private) address — no real vendor. */
    fun isRandom(mac: String): Boolean {
        val first = hex(mac).take(2).toIntOrNull(16) ?: return false
        return first and 0x02 != 0
    }

    /** Manufacturer for a real (universally-administered) address, else null. */
    fun vendor(mac: String): String? {
        val m = map ?: return null
        val h = hex(mac)
        if (h.length < 6 || isRandom(mac)) return null
        return m[h.substring(0, 6)]
    }
}
