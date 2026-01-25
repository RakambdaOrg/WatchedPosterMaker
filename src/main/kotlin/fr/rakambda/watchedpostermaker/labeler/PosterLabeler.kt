package fr.rakambda.watchedpostermaker.labeler

import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min


sealed interface PosterLabeler {
    fun addLabel(
        image: BufferedImage,
        text: String?,
        bgColor: Color = Color(255, 0, 0, (255 * 0.8).toInt()),
    ): BufferedImage

    class SimplePosterLabeler : PosterLabeler {
        override fun addLabel(
            image: BufferedImage,
            text: String?,
            bgColor: Color,
        ): BufferedImage {
            val resized = resize(image)
            return if (text.isNullOrBlank()) resized else addText(text, bgColor, resized)
        }

        private fun addText(text: String, bgColor: Color, image: BufferedImage): BufferedImage {
            val graphics = image.createGraphics()

            val panelWidth: Int = image.width
            val panelHeight: Int = image.height
            val panelMinSize = min(panelWidth, panelHeight)

            graphics.font = Font("SansSerif", Font.PLAIN, 5)
            var metrics = graphics.getFontMetrics(graphics.font)

            while (metrics.stringWidth(text) < (0.5 * panelWidth) && metrics.height < (0.10 * panelHeight)) {
                graphics.font = graphics.font.deriveFont(graphics.font.getSize().toFloat() + 1)
                metrics = graphics.getFontMetrics(graphics.font)
            }

            if (metrics.stringWidth(text) > panelWidth / 2) {
                graphics.font = graphics.font.deriveFont(graphics.font.getSize().toFloat() - 1)
                metrics = graphics.getFontMetrics(graphics.font)
            }

            val textWidth = metrics.stringWidth(text)
            val textX = (panelWidth - textWidth) / 2
            val textY = panelHeight - 80

            val paddingX = max(4, 25 - textWidth)
            val paddingY = 2

            val bgX = textX - paddingX
            val bgY = textY - metrics.ascent - paddingY
            val bgWidth = textWidth + 2 * paddingX
            val bgHeight = metrics.ascent + metrics.descent + 2 * paddingY

            graphics.color = bgColor
            graphics.fill(RoundRectangle2D.Float(bgX.toFloat(), bgY.toFloat(), bgWidth.toFloat(), bgHeight.toFloat(), panelMinSize * 0.10f, panelMinSize * 0.10f))

            graphics.color = Color.WHITE;
            graphics.drawString(text, textX, textY)
            return image
        }

        private fun resize(image: BufferedImage): BufferedImage {
            val targetWidth = 1080
            val targetHeight = 1920

            val widthRatio = targetWidth.toDouble() / image.width
            val heightRatio = targetHeight.toDouble() / image.height
            val scale = minOf(widthRatio, heightRatio)

            val scaledWidth = (image.width * scale).toInt()
            val scaledHeight = (image.height * scale).toInt()

            val x = (targetWidth - scaledWidth) / 2
            val y = (targetHeight - scaledHeight) / 2

            val outputImage = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
            val g2d = outputImage.createGraphics()

            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            g2d.composite = AlphaComposite.Clear
            g2d.fillRect(0, 0, targetWidth, targetHeight)
            g2d.composite = AlphaComposite.SrcOver

            g2d.drawImage(image, x, y, scaledWidth, scaledHeight, null)
            g2d.dispose()

            return outputImage
        }
    }
}