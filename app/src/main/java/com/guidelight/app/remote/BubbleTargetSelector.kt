package com.guidelight.app.remote

data class BubbleWindow(val id: Int, val isWispr: Boolean, val left: Int, val top: Int, val right: Int, val bottom: Int)

/** Conservative selection of a single small edge overlay; never a coordinate fallback. */
object BubbleTargetSelector {
    fun select(windows: List<BubbleWindow>, width: Int, height: Int, density: Float): BubbleWindow? = windows.filter {
        val w = it.right - it.left
        val h = it.bottom - it.top
        val x = (it.left + it.right) / 2
        val y = (it.top + it.bottom) / 2
        it.isWispr && w >= 24 * density && h >= 24 * density && w <= 160 * density && h <= 160 * density &&
            (it.left < 64 * density || width - it.right < 64 * density) && x in 0 until width && y in 0 until height
    }.singleOrNull()
}
