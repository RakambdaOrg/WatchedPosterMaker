package fr.rakambda.watchedpostermaker.saver

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

sealed interface PosterSaver {
    fun savePoster(image: BufferedImage): Unit

    class StaticPosterSaver(private val file: File) : PosterSaver {
        override fun savePoster(image: BufferedImage) {
            ImageIO.write(image, "png", file)
        }
    }
}