package com.cak3d.biscuitmesh.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.cak3d.biscuitmesh.R

/**
 * Plays the app's REAL sound effects — bundled CC0 .ogg files in res/raw — via SoundPool,
 * instead of synthesising them. A genuine recorded crow caw plus UI click / beep / alert /
 * success cues.
 *
 * Credits (all Creative Commons CC0 / public domain, no attribution required):
 *  - caw: "Crow caw" (OpenGameArt, CC0)
 *  - click / beep / alert / success: Kenney "Interface Sounds" (kenney.nl, CC0)
 */
object CrowCaw {
    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()

    /** Preload the samples. Safe to call repeatedly; call it early (Application.onCreate). */
    fun init(context: Context) {
        if (pool != null) return
        val sp = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        val app = context.applicationContext
        val map = mapOf(
            "caw" to R.raw.caw,
            "click" to R.raw.click,
            "beep" to R.raw.beep,
            "alert" to R.raw.alert,
            "success" to R.raw.success,
        )
        for ((k, res) in map) runCatching { ids[k] = sp.load(app, res, 1) }
        pool = sp
    }

    /** Play a named effect: caw / click / beep / alert / success. No-op if not loaded yet. */
    fun fx(name: String, vol: Float = 1f) {
        val id = ids[name] ?: return
        runCatching { pool?.play(id, vol, vol, 1, 0, 1f) }
    }

    /** The crow's call — fired at app launch. */
    fun play() = fx("caw")
}
