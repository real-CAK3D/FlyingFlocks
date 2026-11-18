package com.cak3d.biscuitmesh.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * A native Compose 3D surface-mesh renderer — the engine behind the app's 3D
 * spectral charts (sub-GHz waterfall, audio spectrogram, LiDAR-style scatter).
 *
 * It takes a grid of heights (`rows` = time, each row = a spectrum, values 0..1),
 * projects it in perspective, and draws it as a height-coloured wireframe the way
 * the SciChart / Qt 3D demos do — but fed by the real radios, offline. Drag to
 * orbit; it also drifts slowly on its own so a static board still looks alive.
 */
data class MeshPalette(val low: Color, val mid: Color, val high: Color, val grid: Color) {
    fun at(t: Float): Color {
        val c = t.coerceIn(0f, 1f)
        return if (c < 0.5f) lerp(low, mid, c * 2f) else lerp(mid, high, (c - 0.5f) * 2f)
    }
}

object MeshPalettes {
    // The magenta wireframe look (Spectrum3d).
    val Magenta = MeshPalette(Color(0xFF2A0A3A), Color(0xFFB026FF), Color(0xFFFF4FD8), Color(0x33FFFFFF))
    // Synthwave: deep indigo → orange → hot pink.
    val Synthwave = MeshPalette(Color(0xFF1A1036), Color(0xFFF59E0B), Color(0xFFFF2D95), Color(0x3322D3EE))
    // Matrix green audio-spectrogram look.
    val Matrix = MeshPalette(Color(0xFF02140A), Color(0xFF16A34A), Color(0xFF6EE7B7), Color(0x3334D399))
}

private const val MESH_COLS = 46       // frequency bins drawn across
private const val MESH_ROWS = 26       // time steps drawn front→back
private const val PEAK = 0.62f         // how tall a full-scale value stands

/** Resample a row of 0..1 heights to exactly [n] columns (nearest-ish average). */
private fun resample(src: FloatArray, n: Int): FloatArray {
    if (src.isEmpty()) return FloatArray(n)
    if (src.size == n) return src
    return FloatArray(n) { j ->
        val a = j.toFloat() / n * src.size
        val b = (j + 1).toFloat() / n * src.size
        var sum = 0f; var cnt = 0
        var k = a.toInt()
        while (k < b.toInt().coerceAtLeast(k + 1) && k < src.size) { sum += src[k]; cnt++; k++ }
        if (cnt == 0) src[a.toInt().coerceIn(0, src.size - 1)] else sum / cnt
    }
}

@Composable
fun Mesh3DSurface(
    rows: List<FloatArray>,
    modifier: Modifier = Modifier,
    palette: MeshPalette = MeshPalettes.Magenta,
    height: Dp = 220.dp,
) {
    // User orbit (drag) plus a slow continuous drift.
    var yaw by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(0.82f) }
    val spin = rememberInfiniteTransition(label = "spin")
    val drift by spin.animateFloat(
        0f, (2f * Math.PI).toFloat(),
        infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Restart), label = "drift",
    )

    // Normalise the grid: last MESH_ROWS rows, each resampled to MESH_COLS.
    val grid = remember(rows) {
        rows.takeLast(MESH_ROWS).map { resample(it, MESH_COLS) }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(Unit) {
                detectDragGestures { _, d ->
                    yaw += d.x * 0.008f
                    pitch = (pitch + d.y * 0.006f).coerceIn(0.25f, 1.30f)
                }
            },
    ) {
        if (grid.isEmpty()) return@Canvas
        val r = grid.size
        val c = MESH_COLS
        val cx = size.width / 2f
        val cy = size.height * 0.56f
        val minDim = minOf(size.width, size.height)
        val totalYaw = yaw + 0.25f * sin(drift)     // gentle left-right sway
        val cosY = cos(totalYaw); val sinY = sin(totalYaw)
        val cosP = cos(pitch); val sinP = sin(pitch)
        val camD = 2.4f

        // Project grid coords (i=row/time, j=col/freq, h=height 0..1) to the screen.
        fun project(i: Int, j: Int, h: Float): Offset {
            val x = j.toFloat() / (c - 1) - 0.5f
            val z = i.toFloat() / (r - 1).coerceAtLeast(1) - 0.5f
            val y = h * PEAK - 0.12f
            // yaw about Y
            val x1 = x * cosY + z * sinY
            val z1 = -x * sinY + z * cosY
            // pitch about X
            val y2 = y * cosP - z1 * sinP
            val z2 = y * sinP + z1 * cosP
            val p = 1.5f / (camD + z2)
            return Offset(cx + x1 * p * minDim, cy - y2 * p * minDim)
        }

        // Column (depth) lines first, faint — the mesh floor running away from the viewer.
        for (j in 0 until c step 2) {
            for (i in 0 until r - 1) {
                val a = project(i, j, grid[i][j])
                val b = project(i + 1, j, grid[i + 1][j])
                drawLine(palette.grid, a, b, strokeWidth = 1f)
            }
        }
        // Row lines (the spectra), bright, coloured by height — the signal traces.
        for (i in 0 until r) {
            val front = i.toFloat() / (r - 1).coerceAtLeast(1)   // newer rows brighter
            val alpha = 0.35f + 0.65f * front
            for (j in 0 until c - 1) {
                val h0 = grid[i][j]; val h1 = grid[i][j + 1]
                val a = project(i, j, h0)
                val b = project(i, j + 1, h1)
                val col = palette.at((h0 + h1) / 2f).copy(alpha = alpha)
                drawLine(col, a, b, strokeWidth = 1.6f + 1.4f * front)
            }
        }
    }
}

/**
 * A point in the 3D cloud, in world space roughly within [-0.5, 0.5] on each axis
 * (same scale the surface mesh uses), with its own on-screen dot size and colour.
 */
data class CloudPoint(val x: Float, val y: Float, val z: Float, val size: Float, val color: Color)

/**
 * A 3D point cloud / LiDAR-style scatter — the GNSS satellite sky-dome and device
 * clouds. Orbit by dragging; a faint hemisphere grid gives the dome its shape.
 * Points are depth-sorted (painter's algorithm) so near dots sit over far ones.
 */
@Composable
fun Point3DCloud(
    points: List<CloudPoint>,
    modifier: Modifier = Modifier,
    height: Dp = 240.dp,
    showDome: Boolean = true,
    domeColor: Color = Color(0x3300E5A0),
) {
    var yaw by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(0.72f) }
    val spin = rememberInfiniteTransition(label = "cloudspin")
    val drift by spin.animateFloat(
        0f, (2f * Math.PI).toFloat(),
        infiniteRepeatable(tween(30000, easing = LinearEasing), RepeatMode.Restart), label = "drift",
    )
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(Unit) {
                detectDragGestures { _, d ->
                    yaw += d.x * 0.008f
                    pitch = (pitch + d.y * 0.006f).coerceIn(0.20f, 1.35f)
                }
            },
    ) {
        val cx = size.width / 2f
        val cy = size.height * 0.58f
        val minDim = minOf(size.width, size.height)
        val totalYaw = yaw + 0.25f * sin(drift)
        val cosY = cos(totalYaw); val sinY = sin(totalYaw)
        val cosP = cos(pitch); val sinP = sin(pitch)
        val camD = 2.4f

        fun project(x: Float, y: Float, z: Float): Pair<Offset, Float> {
            val x1 = x * cosY + z * sinY
            val z1 = -x * sinY + z * cosY
            val y2 = y * cosP - z1 * sinP
            val z2 = y * sinP + z1 * cosP
            val p = 1.5f / (camD + z2)
            return Offset(cx + x1 * p * minDim, cy - y2 * p * minDim) to z2
        }

        if (showDome) {
            // Elevation rings at 30° and 60°, plus azimuth spokes — the dome skeleton.
            for (elDeg in intArrayOf(30, 60)) {
                val el = Math.toRadians(elDeg.toDouble())
                val rh = 0.5f * cos(el).toFloat(); val yy = 0.5f * sin(el).toFloat()
                var prev: Offset? = null
                var az = 0
                while (az <= 360) {
                    val a = Math.toRadians(az.toDouble())
                    val pt = project(rh * sin(a).toFloat(), yy, rh * cos(a).toFloat()).first
                    prev?.let { drawLine(domeColor, it, pt, 1f) }
                    prev = pt; az += 15
                }
            }
            var az = 0
            while (az < 360) {
                val a = Math.toRadians(az.toDouble())
                val horizon = project(0.5f * sin(a).toFloat(), 0f, 0.5f * cos(a).toFloat()).first
                val top = project(0f, 0.5f, 0f).first
                drawLine(domeColor, horizon, top, 1f)
                az += 45
            }
        }

        // Far points first so near ones paint over them.
        points.map { project(it.x, it.y, it.z) to it }
            .sortedByDescending { it.first.second }
            .forEach { (proj, cp) ->
                val o = proj.first
                drawCircle(cp.color.copy(alpha = 0.25f), cp.size + 3f, o)
                drawCircle(cp.color, cp.size, o)
            }
    }
}
