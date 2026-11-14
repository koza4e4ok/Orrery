@file:OptIn(InternalOrreryApi::class)

package me.kozakov.orrery.widget

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import me.kozakov.orrery.InternalOrreryApi
import me.kozakov.orrery.compose.internal.drawEventDots
import me.kozakov.orrery.compose.internal.drawSelectionPill
import me.kozakov.orrery.compose.internal.drawTodayMark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedPrimitivesDrawTest {
    @Test
    fun primitivesDrawIntoAnOffscreenBitmap() {
        val bitmap = ImageBitmap(WIDTH, HEIGHT)
        val size = Size(WIDTH.toFloat(), HEIGHT.toFloat())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), size) {
            drawRect(Color.White)
            drawSelectionPill(Color.Blue, Rect(0f, 0f, 20f, 20f), radius = 8f, roundStart = true, roundEnd = false)
            drawTodayMark(Color.Red, radius = 8f, center = Offset(30f, 10f))
            drawEventDots(listOf(Color.Green, Color.Green), centerY = 30f, diameter = 4f, spacing = 2f)
        }
        val pixels = bitmap.toPixelMap()
        assertEquals(Color.Blue, pixels[15, 10])
        assertEquals(Color.Red, pixels[30, 10])
        assertTrue((0 until WIDTH).any { pixels[it, 30] == Color.Green })
        assertEquals(Color.White, pixels[WIDTH - 1, HEIGHT - 1])
    }

    private companion object {
        const val WIDTH = 40
        const val HEIGHT = 40
    }
}
