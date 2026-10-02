package com.example.domain

import kotlin.math.max
import kotlin.math.roundToInt

data class ProfileCrop(val left: Int, val top: Int, val side: Int)

/** Shared by preview and JPEG export so the saved face framing matches the circle. */
fun profileCrop(width: Int, height: Int, preview: Float, zoom: Float, x: Float, y: Float): ProfileCrop {
    require(width > 0 && height > 0 && preview > 0)
    val scale = max(preview / width, preview / height) * zoom.coerceIn(1f, 4f)
    val side = (preview / scale).roundToInt().coerceIn(1, minOf(width, height))
    val left = (width / 2f - x / scale - side / 2f).roundToInt().coerceIn(0, width - side)
    val top = (height / 2f - y / scale - side / 2f).roundToInt().coerceIn(0, height - side)
    return ProfileCrop(left, top, side)
}
