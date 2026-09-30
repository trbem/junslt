package com.jev.probe.advisor

data class CropBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)
object CropMath {
    fun pixels(left: Float, top: Float, right: Float, bottom: Float,
               frameLeft: Float, frameTop: Float, frameWidth: Float, frameHeight: Float,
               imageWidth: Int, imageHeight: Int): CropBounds {
        require(frameWidth > 0 && frameHeight > 0 && imageWidth > 0 && imageHeight > 0)
        val l = ((left - frameLeft) / frameWidth * imageWidth).toInt().coerceIn(0, imageWidth - 1)
        val t = ((top - frameTop) / frameHeight * imageHeight).toInt().coerceIn(0, imageHeight - 1)
        val r = ((right - frameLeft) / frameWidth * imageWidth).toInt().coerceIn(l + 1, imageWidth)
        val b = ((bottom - frameTop) / frameHeight * imageHeight).toInt().coerceIn(t + 1, imageHeight)
        return CropBounds(l, t, r, b)
    }
}
