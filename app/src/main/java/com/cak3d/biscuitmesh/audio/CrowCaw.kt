package com.cak3d.biscuitmesh.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Synthesises the Flocked Up crow "caw" on the phone speaker — the same rise-then-
 * harsh-fall, double-call shape the T-Embed firmware plays (spkCaw), so the app and
 * the device share one voice. Rendered to PCM and played on a daemon thread so it
 * never blocks the UI; failures are swallowed (a missing speaker shouldn't crash launch).
 */
object CrowCaw {
    private const val SR = 22050

    fun play() {
        Thread {
            runCatching {
                val samples = buildCaw()
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SR)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(samples.size * 2)
                    .build()
                track.write(samples, 0, samples.size)
                track.play()
                Thread.sleep((samples.size * 1000L / SR) + 140)
                runCatching { track.stop() }
                track.release()
            }
        }.apply { isDaemon = true }.start()
    }

    // f0, f1 = pitch glide endpoints in Hz (0,0 = a silent gap); ms = duration.
    private val PARTS = listOf(
        Triple(680.0, 1120.0, 55),   // caw 1 — quick rise
        Triple(1120.0, 470.0, 190),  // caw 1 — harsh fall
        Triple(0.0, 0.0, 85),        // gap
        Triple(680.0, 1120.0, 55),   // caw 2 — quick rise
        Triple(1120.0, 470.0, 190),  // caw 2 — harsh fall
    )

    private fun buildCaw(): ShortArray {
        val total = PARTS.sumOf { it.third } * SR / 1000
        val out = ShortArray(total)
        var idx = 0
        var ph = 0.0
        var rph = 0.0
        val raspDp = 2.0 * PI * 55.0 / SR
        for ((f0, f1, ms) in PARTS) {
            val n = ms * SR / 1000
            val silent = f0 == 0.0 && f1 == 0.0
            for (i in 0 until n) {
                if (idx >= total) break
                if (silent) { out[idx++] = 0; continue }
                val t = if (n > 1) i.toDouble() / n else 0.0
                val f = f0 + (f1 - f0) * t
                val rasp = 0.55 + 0.45 * sin(rph)           // tremolo rasp
                val env = when {                            // soft attack / decay
                    t < 0.06 -> t / 0.06
                    t > 0.85 -> (1.0 - t) / 0.15
                    else -> 1.0
                }
                out[idx++] = (sin(ph) * 9000.0 * rasp * env).toInt().toShort()
                ph += 2.0 * PI * f / SR; if (ph > 2 * PI) ph -= 2 * PI
                rph += raspDp; if (rph > 2 * PI) rph -= 2 * PI
            }
        }
        return out
    }
}
