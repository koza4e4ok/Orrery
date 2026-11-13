package me.kozakov.orrery.compose.spike

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import kotlin.test.assertTrue

// Spike (#89 task 1, item a): measure and draw text into an ImageBitmap with no composition.

internal data class SpikeTextStats(
    val platform: String,
    val textWidth: Int,
    val textHeight: Int,
    val inkPixels: Int,
    val distinctColors: Int,
)

private const val BITMAP_WIDTH = 240
private const val BITMAP_HEIGHT = 80

internal fun renderSpikeText(
    platform: String,
    resolver: FontFamily.Resolver,
): SpikeTextStats {
    val density = Density(density = 2f, fontScale = 1f)
    val measurer =
        TextMeasurer(
            defaultFontFamilyResolver = resolver,
            defaultDensity = density,
            defaultLayoutDirection = LayoutDirection.Ltr,
            cacheSize = 0,
        )
    val layout = measurer.measure(text = "Orrery 13", style = TextStyle(color = Color.Black, fontSize = 20.sp))
    val bitmap = ImageBitmap(BITMAP_WIDTH, BITMAP_HEIGHT)
    CanvasDrawScope().draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(bitmap),
        size = Size(BITMAP_WIDTH.toFloat(), BITMAP_HEIGHT.toFloat()),
    ) {
        drawRect(color = Color.White)
        drawText(textLayoutResult = layout, topLeft = Offset(4f, 4f))
    }
    val pixels = IntArray(BITMAP_WIDTH * BITMAP_HEIGHT)
    bitmap.readPixels(buffer = pixels)
    val white = Color.White.toArgb()
    return SpikeTextStats(
        platform = platform,
        textWidth = layout.size.width,
        textHeight = layout.size.height,
        inkPixels = pixels.count { it != white },
        distinctColors = pixels.toSet().size,
    )
}

@Suppress("ForbiddenMethodCall")
internal fun assertSpikeText(stats: SpikeTextStats) {
    println("SPIKE-89 offscreen text: $stats")
    assertTrue(stats.textWidth > 0 && stats.textHeight > 0, "text was not measured: $stats")
    assertTrue(stats.inkPixels > 100, "no text ink in the bitmap: $stats")
}
