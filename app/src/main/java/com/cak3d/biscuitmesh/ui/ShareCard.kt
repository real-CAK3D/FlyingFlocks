package com.cak3d.biscuitmesh.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.cak3d.biscuitmesh.wardrive.DriveSession
import com.cak3d.biscuitmesh.wardrive.GeoPt
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Renders a shareable image summarising a drive, then fires a share sheet. */
object ShareCard {
    fun share(context: Context, drive: DriveSession, useMph: Boolean, pts: List<GeoPt>) {
        val bmp = render(drive, useMph, pts)
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "drive_${drive.id}.png")
        runCatching { file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Caught the invisible on a Flying Flocks drive — cak3dcreations.vercel.app")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share drive").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun render(drive: DriveSession, useMph: Boolean, pts: List<GeoPt>): Bitmap {
        val w = 1080; val h = 1080
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#070c08"))
        val green = Color.parseColor("#5fd36a")
        val ember = Color.parseColor("#e8873a")
        val mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // route trace, normalised into a box in the lower-middle
        if (pts.size >= 2) {
            val lats = pts.map { it.lat }; val lons = pts.map { it.lon }
            val minLa = lats.min(); val maxLa = lats.max(); val minLo = lons.min(); val maxLo = lons.max()
            val spanLa = (maxLa - minLa).coerceAtLeast(1e-6); val spanLo = (maxLo - minLo).coerceAtLeast(1e-6)
            val bx = 120f; val by = 470f; val bw = 840f; val bh = 380f
            p.color = Color.argb(60, 95, 211, 106); p.style = Paint.Style.FILL
            pts.forEach { gp ->
                val x = bx + ((gp.lon - minLo) / spanLo).toFloat() * bw
                val y = by + bh - ((gp.lat - minLa) / spanLa).toFloat() * bh
                c.drawCircle(x, y, 4f, p)
            }
        }

        p.typeface = mono; p.color = green; p.textSize = 40f
        c.drawText("FLYING FLOCKS", 120f, 160f, p)
        p.color = ember; p.textSize = 28f
        c.drawText("CAK3D_CREATIONS", 120f, 205f, p)
        p.color = Color.WHITE; p.textSize = 30f; p.typeface = Typeface.MONOSPACE
        c.drawText(SimpleDateFormat("EEE MMM d, yyyy · HH:mm", Locale.US).format(Date(drive.startMs)), 120f, 270f, p)

        // big stats grid
        val km = drive.distanceM / 1000.0
        val dist = if (useMph) "%.1f mi".format(km * 0.621371) else "%.1f km".format(km)
        val dur = drive.durationMs / 60000
        drawStat(c, "${drive.wifiCount}", "Wi-Fi", 120f, 380f, green, mono)
        drawStat(c, "${drive.btCount}", "BT", 400f, 380f, Color.parseColor("#7C9CFF"), mono)
        drawStat(c, "${drive.cameraCount}", "cams", 680f, 380f, Color.parseColor("#FF5470"), mono)
        drawStat(c, dist, "surveyed", 120f, 980f, ember, mono)
        drawStat(c, "${dur}m", "driving", 680f, 980f, green, mono)
        return bmp
    }

    private fun drawStat(c: Canvas, value: String, label: String, x: Float, y: Float, color: Int, mono: Typeface) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.typeface = mono; p.color = color; p.textSize = 72f
        c.drawText(value, x, y, p)
        p.textSize = 26f; p.color = Color.argb(180, 255, 255, 255)
        c.drawText(label, x, y + 36f, p)
    }
}
