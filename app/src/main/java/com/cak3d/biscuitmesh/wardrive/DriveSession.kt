package com.cak3d.biscuitmesh.wardrive

/** A finished wardrive, kept in history so you can review and re-upload it. */
data class DriveSession(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val wifiCount: Int,
    val btCount: Int,
    val cameraCount: Int,
    val distanceM: Double,
    val uploaded: Boolean = false,
    /** True when the capture CSV was saved to disk and can be re-uploaded. */
    val hasCapture: Boolean = false,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
}
