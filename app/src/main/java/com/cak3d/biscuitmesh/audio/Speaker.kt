package com.cak3d.biscuitmesh.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Thin wrapper over Android TextToSpeech for spoken camera/speed alerts and
 * turn-by-turn directions. Safe to call before the engine is ready (drops), and
 * honours a runtime on/off flag.
 */
class Speaker(context: Context) {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile var enabled = true

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                runCatching { tts?.language = Locale.US }
                ready = true
            }
        }
    }

    /** Speaks [text]. [flush] interrupts anything in progress (use for urgent "now" prompts). */
    fun say(text: String, flush: Boolean = false) {
        if (!enabled || !ready || text.isBlank()) return
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        runCatching { tts?.speak(text, mode, null, text.hashCode().toString()) }
    }

    fun stop() { runCatching { tts?.stop() } }

    fun shutdown() {
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null; ready = false
    }
}
