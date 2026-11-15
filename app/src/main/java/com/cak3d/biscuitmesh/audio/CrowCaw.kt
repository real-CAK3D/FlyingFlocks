package com.cak3d.biscuitmesh.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Synthesises a SINGLE crow "caw" on the phone speaker — one short, harsh squawk, not a
 * pitch-gliding siren. The caw character comes from harmonic-rich tone + a deep buzz/rasp
 * + a touch of noise over a quick rise-then-fall pitch contour (matches the T-Embed's
 * spkCaw). Rendered to PCM on a daemon thread so it never blocks the UI; failures swallowed.
 */
object CrowCaw {
    private const val SR = 22050
    private const val DUR_MS = 230

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

    private fun buildCaw(): ShortArray {
        val n = DUR_MS * SR / 1000
        val out = ShortArray(n)
        var ph = 0.0      // tone phase
        var bz = 0.0      // buzz/rasp phase
        val rnd = java.util.Random(7)
        for (i in 0 until n) {
            val t = i.toDouble() / n
            // Pitch: a fast rise, then a long fall — the crow's "caw" downglide (not up-and-down).
            val f = if (t < 0.12) 720.0 + (1180.0 - 720.0) * (t / 0.12)
                    else 1180.0 + (500.0 - 1180.0) * ((t - 0.12) / 0.88)
            // Harsh, harmonic-rich tone (sawtooth-ish) — the brightness that makes it a squawk.
            val tone = sin(ph) + 0.6 * sin(2 * ph) + 0.4 * sin(3 * ph) + 0.25 * sin(4 * ph)
            // Deep buzz/rasp (~80 Hz AM) + a little noise — the scratchy crow texture, kills the
            // pure-tone siren quality.
            val rasp = 0.28 + 0.72 * abs(sin(bz))
            val noise = (rnd.nextDouble() - 0.5) * 0.20
            val env = when {                     // fast attack, sustain, decay tail
                t < 0.035 -> t / 0.035
                t > 0.78 -> (1.0 - t) / 0.22
                else -> 1.0
            }
            val s = (tone * 0.42 + noise) * rasp * env * 10500.0
            out[i] = s.coerceIn(-31000.0, 31000.0).toInt().toShort()
            ph += 2.0 * PI * f / SR; if (ph > 2 * PI) ph -= 2 * PI
            bz += 2.0 * PI * 80.0 / SR; if (bz > 2 * PI) bz -= 2 * PI
        }
        return out
    }
}
