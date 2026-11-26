package com.cak3d.biscuitmesh.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/** One pickable talk-back voice: its engine [name] (the stored key) and a friendly [label]. */
data class VoiceInfo(val name: String, val label: String)

/**
 * Thin wrapper over Android TextToSpeech for spoken camera/speed alerts, crossings and directions.
 * Safe to call before the engine is ready (drops), honours a runtime on/off flag, and lets the
 * operator pick a voice and speaking rate (applied as soon as the engine is ready).
 */
class Speaker(context: Context) {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile var enabled = true
    @Volatile private var pendingVoice: String? = null
    @Volatile private var pendingRate: Float = 1f
    @Volatile private var pendingPitch: Float = 1f

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                runCatching { tts?.language = Locale.US }
                ready = true
                applyVoice()
                applyRate()
                applyPitch()
            }
        }
    }

    /** Speaks [text]. [flush] interrupts anything in progress (use for urgent "now" prompts). */
    fun say(text: String, flush: Boolean = false) {
        if (!enabled || !ready || text.isBlank()) return
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        runCatching { tts?.speak(text, mode, null, text.hashCode().toString()) }
    }

    /** English voices the installed engine offers, for the picker (on-device ones first). */
    fun availableVoices(): List<VoiceInfo> = runCatching {
        tts?.voices.orEmpty()
            .filter { it.locale.language == "en" }
            .sortedWith(compareBy({ it.isNetworkConnectionRequired }, { it.locale.toString() }, { it.name }))
            .map { VoiceInfo(it.name, label(it)) }
            .distinctBy { it.name }
    }.getOrDefault(emptyList())

    private fun label(v: Voice): String {
        val place = runCatching { v.locale.displayName }.getOrDefault(v.locale.toString())
        val n = v.name.lowercase()
        val gender = when {
            "female" in n || "#f" in n -> " · female"
            "male" in n || "#m" in n -> " · male"
            else -> ""
        }
        val net = if (v.isNetworkConnectionRequired) " · online" else ""
        return "$place$gender$net"
    }

    /** Pick a voice by its engine name (null/blank = engine default). Applied when ready. */
    fun setVoice(name: String?) {
        pendingVoice = name?.takeIf { it.isNotBlank() }
        if (ready) applyVoice()
    }

    private fun applyVoice() {
        val name = pendingVoice ?: return
        runCatching { tts?.voices?.firstOrNull { it.name == name }?.let { tts?.voice = it } }
    }

    /** Speaking rate (0.5 slow … 2.0 fast; 1.0 normal). Applied when ready. */
    fun setRate(rate: Float) {
        pendingRate = rate.coerceIn(0.5f, 2.0f)
        if (ready) runCatching { tts?.setSpeechRate(pendingRate) }
    }

    private fun applyRate() {
        runCatching { tts?.setSpeechRate(pendingRate) }
    }

    /** Voice pitch (0.5 deep … 2.0 high; 1.0 normal). Lets one engine voice sound like several. */
    fun setPitch(pitch: Float) {
        pendingPitch = pitch.coerceIn(0.5f, 2.0f)
        if (ready) runCatching { tts?.setPitch(pendingPitch) }
    }

    private fun applyPitch() {
        runCatching { tts?.setPitch(pendingPitch) }
    }

    fun stop() { runCatching { tts?.stop() } }

    fun shutdown() {
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null; ready = false
    }
}
