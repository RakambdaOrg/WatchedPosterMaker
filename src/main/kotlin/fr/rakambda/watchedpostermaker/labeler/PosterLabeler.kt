package fr.rakambda.watchedpostermaker.labeler

import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.min


sealed interface PosterLabeler {
    fun addLabel(
        image: BufferedImage,
        text: String,
        bgColor: Color = Color(255, 0, 0, (255 * 0.8).toInt()),
    ): BufferedImage

    class SimplePosterLabeler : PosterLabeler {
        override fun addLabel(
            image: BufferedImage,
            text: String,
            bgColor: Color,
        ): BufferedImage {
            val graphics = image.createGraphics() ?: return image
            val panelWidth: Int = image.width
            val panelHeight: Int = image.height
            val panelMinSize = min(panelWidth, panelHeight)

            graphics.font = Font("SansSerif", Font.PLAIN, 5)
            var metrics: FontMetrics = graphics.getFontMetrics(graphics.font)

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
            val textY = panelHeight - 40

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
    }
}