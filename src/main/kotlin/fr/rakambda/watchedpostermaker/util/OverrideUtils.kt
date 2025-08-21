package fr.rakambda.watchedpostermaker.util

import java.awt.image.BufferedImage
import java.awt.image.WritableRaster


internal fun Int.fixed(length: Int): String {
    return this.toString().padStart(length, '0')
}

internal fun BufferedImage.clone(): BufferedImage {
    try {
        val raster: WritableRaster = copyData(raster.createCompatibleWritableRaster())
        return BufferedImage(colorModel, raster, colorModel.isAlphaPremultiplied(), null)
    } catch (_: Exception) {
    }

    val b = BufferedImage(width, height, type)
    val g = b.graphics
    g.drawImage(this, 0, 0, null)
    g.dispose()
    return b
}