package com.cak3d.biscuitmesh.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/** One recorded g-force sample: when, and the total acceleration in g. */
data class GSample(val ms: Long, val g: Float)

/**
 * Logs total g-force from the accelerometer while a drive is recording, so Drive Stats can chart
 * the bumps, braking and cornering of a finished drive. Downsampled (~1/sec) and capped so a long
 * drive stays light. No permission needed.
 */
class GForceRecorder(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accel = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val samples = ArrayList<GSample>()
    @Volatile private var recording = false
    private var lastMs = 0L

    fun start() {
        if (recording || accel == null) return
        synchronized(samples) { samples.clear() }
        lastMs = 0L
        recording = true
        runCatching { sm?.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI) }
    }

    /** Stop and return the recorded series. */
    fun stop(): List<GSample> {
        recording = false
        runCatching { sm?.unregisterListener(this) }
        return synchronized(samples) { ArrayList(samples) }
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (!recording) return
        val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) / 9.80665f
        val now = System.currentTimeMillis()
        if (now - lastMs < 900) return
        lastMs = now
        synchronized(samples) {
            samples.add(GSample(now, g))
            if (samples.size > 6000) samples.removeAt(0)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
